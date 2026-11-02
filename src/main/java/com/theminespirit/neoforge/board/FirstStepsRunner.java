package com.theminespirit.neoforge.board;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardOutcome;
import com.theminespirit.core.board.BoardStatus;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.BoardTuning;
import com.theminespirit.core.board.BuildGap;
import com.theminespirit.core.board.CaseMaterials;
import com.theminespirit.core.board.ConfigStamp;
import com.theminespirit.core.board.GroundsPlan;
import com.theminespirit.core.board.MaterialSource;
import com.theminespirit.core.board.MineDescentBottom;
import com.theminespirit.core.board.PlotLeveling;
import com.theminespirit.core.board.RepairPlan;
import com.theminespirit.core.board.ResourceGate;
import com.theminespirit.core.board.SchematicMaterials;
import com.theminespirit.core.board.SettlementFacts;
import com.theminespirit.core.board.SettlementPlot;
import com.theminespirit.core.board.StageAudit;
import com.theminespirit.core.board.StockNeed;
import com.theminespirit.core.board.StockView;
import com.theminespirit.core.board.TicketPlans;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.board.ToolSet;
import com.theminespirit.core.building.BlockMatch;
import com.theminespirit.core.building.MineLayout;
import com.theminespirit.core.building.PresetLibrary;
import com.theminespirit.core.building.Schematic;
import com.theminespirit.core.building.SchematicBlocks;
import com.theminespirit.core.building.WoodSpecies;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.FallbackNode;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.core.planner.Ingredient;
import com.theminespirit.core.reload.DataTables;
import com.theminespirit.core.town.SiteLevels;
import com.theminespirit.core.town.StageSite;
import com.theminespirit.core.town.StageSites;
import com.theminespirit.core.town.StageWork;
import com.theminespirit.core.town.TownPlan;
import com.theminespirit.core.town.TownSnapshot;
import com.theminespirit.neoforge.OwnWood;
import com.theminespirit.neoforge.SchematicResources;
import com.theminespirit.neoforge.SpiritConfig;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.crafting.RecipeKnowledge;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.IntBinaryOperator;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;

public final class FirstStepsRunner {
    static final BuildGap.Schemes SCHEMES = new BuildGap.Schemes() {
        @Override
        public Optional<MineLayout> mine(ScriptCall call) {
            return SchemeAudit.mine(call);
        }

        @Override
        public Optional<List<Schematic.PlacedEntry>> entries(ScriptCall call) {
            return SchemeAudit.entries(call);
        }
    };
    private static final Set<BoardCase> WOODEN_BUILDINGS =
            EnumSet.of(BoardCase.BUILD_FENCE, BoardCase.BUILD_HOUSE, BoardCase.BUILD_ORCHARD);
    private static final int LAYOUT_SLACK_DIVISOR = 50;
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int SCAN_RADIUS = 16;
    private static final String GATHER_PREFIX = "gather:";
    private static final Set<BoardCase> BEFORE_YARD = EnumSet.of(
            BoardCase.CRAFTING_STATION,
            BoardCase.CRAFT_WOODEN_PICKAXE,
            BoardCase.CRAFT_WOODEN_AXE,
            BoardCase.CRAFT_WOODEN_SHOVEL);
    private static final Set<BoardCase> EXACT_STEPS =
            EnumSet.of(BoardCase.BUILD_FENCE, BoardCase.BUILD_HOUSE, BoardCase.LIGHTING, BoardCase.STOCK_CHESTS);
    private static final String FIRST_PREFIX = "first:";
    private static final String MINECRAFT = "minecraft:";
    private static final String CLEAR_PART_PREFIX = "clear:";
    private static final String FENCE_PART_PREFIX = "fence:";
    private static final long PLAN_WARN_EVERY = 600;
    private static final int EXPAND_DEPTH = 4;
    private static final int LEVELING_SHOWN = 6;

    private FirstStepsRunner() {}

    public enum PlanWorld {
        AUDIT,
        JOURNAL
    }

    public record StageScan(
            StageSite stage, String wood, BlockCoord home, SettlementFacts facts, boolean furnaceBlock) {}

    public record StepOrder(BoardCase caseValue, int priority, String cause) {}

    public static Optional<StageScan> tick(
            ServerLevel level, BoardService service, List<SpiritEntity> spirits, StockView stock, long now) {
        return tick(level, service, spirits, stock, now, PlanWorld.AUDIT);
    }

    public static Optional<StageScan> tick(
            ServerLevel level,
            BoardService service,
            List<SpiritEntity> spirits,
            StockView stock,
            long now,
            PlanWorld world) {
        if (!SpiritConfig.boardEnabled() || spirits.isEmpty()) {
            return Optional.empty();
        }
        if (spirits.stream().noneMatch(spirit -> spirit.home().isPresent())) {
            return Optional.empty();
        }
        if (plotOf(level, service, spirits, now).isEmpty()) {
            return Optional.empty();
        }
        StageSite stage = SettlementPlotStore.get(level)
                .site()
                .orElseThrow(() -> new IllegalStateException(
                        "Участок обмерен, а места этапа 1 нет: план поселения не сохранился в мире"));
        BlockCoord home = new BlockCoord(
                stage.anchor().x(), homeY(spirits), stage.anchor().z());
        String wood = woodOf(level, home, stock);
        StoneSite site = new StoneSite(home, wood, stage);
        Scan scan = buildFacts(level, site, stock, service, world);
        dropOtherWood(level, service, BuildingWoodChoice.allowed(level, wood));
        StageScan stageScan = new StageScan(stage, wood, home, scan.facts(), scan.furnaceBlock());
        return Optional.of(StageSchedule.reopen(level, service, stock, stageScan, spirits.size(), now));
    }

    private static void dropOwnWood(Map<String, Integer> need, StockView stock, String wood) {
        int cost = 0;
        List<String> items = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : need.entrySet()) {
            if (wood.equals(OwnWood.species(entry.getKey()))) {
                cost += OwnWood.plankCost(entry.getKey(), entry.getValue());
                items.add(entry.getKey());
            }
        }
        if (!items.isEmpty() && OwnWood.planksIn(stock.available(), wood) >= cost) {
            items.forEach(need::remove);
        }
    }

    private static void asPlanks(Map<String, Integer> need, String wood) {
        int cost = 0;
        for (Map.Entry<String, Integer> entry : List.copyOf(need.entrySet())) {
            if (wood.equals(OwnWood.species(entry.getKey()))) {
                cost += OwnWood.plankCost(entry.getKey(), entry.getValue());
                need.remove(entry.getKey());
            }
        }
        if (cost > 0) {
            need.merge(wood + "_planks", cost, Integer::sum);
        }
    }

    public static Map<String, Integer> materialsOf(
            ServerLevel level, StockView stock, StageScan scan, BoardCase caseValue) {
        String wood = BuildingWoodChoice.stored(level, caseValue, scan.stage(), scan.wood());
        TicketTarget target = targetFor(level, caseValue, scan.stage(), wood);
        return need(level, stock, new OpenStep(caseValue, target, Map.of(), wood), false);
    }

    public static Map<String, Integer> open(
            ServerLevel level, BoardService service, StockView stock, StageScan scan, StepOrder order, long now) {
        BoardCase caseValue = order.caseValue();
        StageSite stage = scan.stage();
        boolean blocked = scan.facts().blocked().contains(caseValue);
        String wood = blocked
                ? BuildingWoodChoice.stored(level, caseValue, stage, scan.wood())
                : BuildingWoodChoice.settle(level, service, caseValue, stage, stock.available(), scan.wood(), now);
        TicketTarget target = targetFor(level, caseValue, stage, wood);
        if (caseValue == BoardCase.CLEAR_GROUNDS) {
            target = target.withLeveling(leveling(level, stage));
        }
        Map<String, Integer> need = need(level, stock, new OpenStep(caseValue, target, Map.of(), wood), blocked);
        if (StageSettle.AUDIT_STEPS.contains(caseValue)
                && reopenDone(level, service, new OpenStep(caseValue, target, need, wood), partsOf(caseValue), now)) {
            return Map.of();
        }
        Map<String, Integer> missing = ResourceGate.missing(new StockNeed(need), stock);
        if (!missing.isEmpty()) {
            LOGGER.info("Постройка этапа {} ждёт: не хватает {}", caseValue, missing);
            return missing;
        }
        openStep(level, service, new OpenStep(caseValue, target, need, wood), order, now);
        return Map.of();
    }

    public static List<String> ticketIds(BoardCase caseValue) {
        return StageReopen.ids(caseValue);
    }

    static Map<String, Integer> need(ServerLevel level, StockView stock, OpenStep step, boolean blocked) {
        BoardCase caseValue = step.caseValue();
        TicketTarget target = step.target();
        String wood = step.wood();
        MaterialSource source = materialSource(level);
        Map<String, Integer> need = new HashMap<>();
        if (caseValue == BoardCase.BUILD_MINE) {
            TaskNode plan = TicketPlans.plan(caseValue, 1, target);
            need.putAll(BuildGap.need(plan, BuildGap.trace(plan, wood, probe(level), SCHEMES), source));
            StageMaterialNeed.mine(stock, need);
            return need;
        }
        if (caseValue == BoardCase.BUILD_ORCHARD) {
            return StageMaterialNeed.orchard(level, target, wood);
        }
        materials(source, caseValue, target)
                .forEach((item, count) -> need.merge(WoodSpecies.swap(item, wood), count, Integer::sum));
        if (caseValue == BoardCase.CLEAR_GROUNDS) {
            need.keySet().removeIf(item -> "chest".equals(item) || PlotLeveling.DEFAULT_BLOCK.equals(item));
        }
        if (WOODEN_BUILDINGS.contains(caseValue)) {
            if (blocked) {
                asPlanks(need, wood);
            } else {
                dropOwnWood(need, stock, wood);
            }
        }
        return need;
    }

    record OpenStep(BoardCase caseValue, TicketTarget target, Map<String, Integer> need, String wood) {}

    private static void openStep(ServerLevel level, BoardService service, OpenStep step, StepOrder order, long now) {
        MaterialSource source = materialSource(level);
        BoardCase caseValue = step.caseValue();
        TicketTarget target = step.target();
        Map<String, Integer> need = step.need();
        String wood = step.wood();
        int parts = partsOf(caseValue);
        StageWork work = target.work();
        List<StageBuilds.FencePart> fenceParts = caseValue == BoardCase.BUILD_FENCE && work != null
                ? FenceParts.of(level, work.site(), parts)
                : List.of();
        List<Integer> openParts = FenceParts.open(fenceParts);
        BoardOutcome outcome = BoardOutcome.DUPLICATE;
        for (int part = 1; part <= parts; part++) {
            TicketTarget partTarget = parts == 1 ? target : target.withCount(part);
            if (caseValue == BoardCase.BUILD_FENCE && work != null) {
                if (!openParts.contains(part)) {
                    continue;
                }
                partTarget = partTarget.withWork(fenceParts.get(part - 1).work());
            }
            Map<String, Integer> reserve = part == 1 ? need : Map.of();
            if (caseValue == BoardCase.BUILD_FENCE) {
                reserve = new HashMap<>(StageSettle.partNeed(source, partTarget, wood));
                reserve.keySet().retainAll(need.keySet());
            }
            BoardOutcome submitted = service.submit(
                    new BoardTicket(
                            partId(caseValue, part),
                            caseValue,
                            Optional.empty(),
                            order.priority(),
                            now + part - 1,
                            1,
                            order.cause(),
                            List.of(),
                            Map.copyOf(reserve)),
                    partTarget);
            if (submitted == BoardOutcome.DUPLICATE) {
                service.board().reprioritize(partId(caseValue, part), order.priority());
            }
            if (outcome == BoardOutcome.DUPLICATE) {
                outcome = submitted;
            }
        }
        if (outcome == BoardOutcome.ACCEPTED) {
            LOGGER.info("[layout] постройка этапа {} открыта: материалы на месте", caseValue);
        }
    }

    static boolean reopenDone(ServerLevel level, BoardService service, OpenStep step, int parts, long now) {
        BoardCase caseValue = step.caseValue();
        StageWork work = Objects.requireNonNull(
                step.target().work(), "Шаг первых шагов без места этапа: план поселения не передан в задание");
        BuildGap.Gap gap = gap(level, caseValue, work.site(), step.wood());
        if (!gap.confident()) {
            LOGGER.info(
                    "[layout] {}: {} клеток в незагруженных чанках — вывод о постройке отложен до загрузки",
                    caseValue,
                    gap.unknown());
            return true;
        }
        int missing = gap.shortage();
        BoardStatus base = service.board().statusOf(partId(caseValue, 1)).orElse(null);
        boolean partsReady = caseValue != BoardCase.BUILD_FENCE || partsDone(service, caseValue, level, work.site());
        StageAudit.Decision decision = StageAudit.decide(base, partsReady, gap.done(), gap.total());
        if (decision == StageAudit.Decision.COMPLETE) {
            StageClose.built(service, caseValue, parts);
            return true;
        }
        if (decision == StageAudit.Decision.OPEN) {
            return false;
        }
        if (StageAudit.unbuilt(missing, gap.total(), BoardTuning.rebuildPercent())) {
            PlotTrace.reportGap(level, caseValue, step.target(), step.wood(), missing, gap.total(), true);
            BuildingWoodChoice.rebuilt(level, caseValue, work.site());
            StageClose.reopen(service, caseValue, parts);
            return false;
        }
        PlotTrace.reportGap(level, caseValue, step.target(), step.wood(), missing, gap.total(), false);
        if (base != BoardStatus.DONE) {
            StageClose.drop(service, caseValue, parts);
        }
        TicketTarget repairTarget = StageSettle.repairTarget(caseValue, step.target(), gap);
        BoardOutcome repair = StageRepair.submit(
                service,
                caseValue,
                repairTarget,
                new StageRepair.Gap(missing, gap.total(), StageSettle.repairNeed(level, step, repairTarget)),
                now);
        if (StageAudit.keeps(repair)) {
            return true;
        }
        StageClose.reopen(service, caseValue, parts);
        return false;
    }

    public static void dropOtherWood(ServerLevel level, BoardService service, Set<String> woods) {
        for (BoardTicket ticket : List.copyOf(service.board().all())) {
            if (!ticket.id().startsWith(GATHER_PREFIX) || !active(service, ticket.id())) {
                continue;
            }
            String item = bare(ticket.id().substring(GATHER_PREFIX.length()));
            Optional<String> species = WoodSpecies.speciesOf(item);
            if (species.isPresent() && !woods.contains(species.orElseThrow()) && service.cancel(level, ticket.id())) {
                LOGGER.info("[wood] снята добыча {}: породы построек {}", ticket.id(), woods);
            }
        }
    }

    public static boolean stageOneBuilding(BoardService service) {
        return service.board().all().stream()
                .anyMatch(ticket -> (ticket.id().startsWith(FIRST_PREFIX)
                                || ticket.id().startsWith(FENCE_PART_PREFIX)
                                || ticket.id().startsWith(RepairPlan.PREFIX)
                                || ticket.id().startsWith(CLEAR_PART_PREFIX))
                        && active(service, ticket.id()));
    }

    static int partsOf(BoardCase caseValue) {
        return StageReopen.ids(caseValue).size();
    }

    static String partId(BoardCase caseValue, int part) {
        return StageReopen.partId(caseValue, part);
    }

    private static PlotLeveling leveling(ServerLevel level, StageSite site) {
        IntBinaryOperator tops =
                (x, z) -> GroundProbe.top(level, x, z, site.anchor().y() + 1);
        PlotLeveling plan = SiteLevels.leveling(site, tops, PlotPlanBlocks.of(site)::planned);
        if (!plan.cut().isEmpty()) {
            LOGGER.info(
                    "Расчистка на y={}: лишнего выше уровня {} ящиков, подсыпки нет",
                    site.anchor().y(),
                    plan.cut().size());
            if (plan.cut().size() <= LEVELING_SHOWN) {
                LOGGER.info("[plot] срезка выше уровня: {}", plan.cut());
            }
        }
        return plan;
    }

    private static int descentEnd(StageSite site) {
        try {
            return MineDescentBottom.standOf(site.mineEntrance())
                    .map(BlockCoord::z)
                    .orElse(Integer.MAX_VALUE);
        } catch (IllegalStateException | IllegalArgumentException unbuildable) {
            return Integer.MAX_VALUE;
        }
    }

    private static IntBinaryOperator plotFeet(ServerLevel level, StageSite site) {
        int passageEnd = descentEnd(site);
        BlockCoord anchor = site.anchor();
        return (x, z) -> {
            if (site.minePassage(x, z) && z <= passageEnd) {
                return anchor.y();
            }
            int feet = GroundProbe.feet(level, x, z, anchor.y());
            return site.mineColumn(x, z) ? Math.min(feet, anchor.y()) : feet;
        };
    }

    static void expand(MaterialSource source, String item, int count, Map<String, Integer> raw, int depth) {
        List<Ingredient> ingredients = depth < EXPAND_DEPTH ? source.ingredientsFor(item) : List.of();
        int output = source.outputCount(item);
        if (ingredients.isEmpty() || output <= 0) {
            raw.merge(
                    item.startsWith("minecraft:") ? item.substring("minecraft:".length()) : item, count, Integer::sum);
            return;
        }
        int crafts = (count + output - 1) / output;
        for (Ingredient ingredient : ingredients) {
            expand(source, ingredient.item(), ingredient.count() * crafts, raw, depth + 1);
        }
    }

    private static String woodOf(ServerLevel level, BlockCoord home, StockView stock) {
        return SettlementWood.of(level, home, stock.available());
    }

    public static TicketTarget targetFor(ServerLevel level, BoardCase caseValue, StageSite stage, String wood) {
        BlockCoord point = BEFORE_YARD.contains(caseValue) ? Stations.at(level, caseValue, stage) : stage.workYard();
        TicketTarget target = TicketTarget.at(point).withWork(StageBuilds.of(level, caseValue, stage, 0));
        if (WOODEN_BUILDINGS.contains(caseValue) && !WoodSpecies.OAK.equals(wood)) {
            target = target.withWood(wood);
        }
        return ToolSet.isTool(caseValue) ? target.withToolCount(1) : target;
    }

    private static boolean active(BoardService service, String id) {
        BoardStatus status = service.board().statusOf(id).orElse(null);
        return status == BoardStatus.PENDING
                || status == BoardStatus.WAITING_RESOURCES
                || status == BoardStatus.ASSIGNED;
    }

    static Map<String, Integer> materials(MaterialSource source, BoardCase caseValue, TicketTarget target) {
        try {
            TaskNode plan = TicketPlans.plan(caseValue, 1, target);
            return CaseMaterials.need(plan, source);
        } catch (RuntimeException failure) {
            LOGGER.error("Материалы шага первых шагов {} не посчитаны: {}", caseValue, failure.getMessage());
            return Map.of();
        }
    }

    static MaterialSource materialSource(ServerLevel level) {
        RecipeKnowledge knowledge = RecipeKnowledge.of(level);
        return new MaterialSource() {
            @Override
            public int outputCount(String item) {
                return knowledge.outputCount(item);
            }

            @Override
            public List<Ingredient> ingredientsFor(String item) {
                return knowledge.ingredientsFor(item);
            }

            @Override
            public Map<String, Integer> schematicBlocks(String file) {
                return SchematicMaterials.count(
                        DataTables.INSTANCE.presets().current().byId(file).map(preset -> preset.type()),
                        () -> PresetLibrary.countMaterials(SchematicResources.load(file)));
            }
        };
    }

    public static void minusPlaced(ServerLevel level, TaskNode plan, String wood, Map<String, Integer> need) {
        switch (plan) {
            case SequenceNode sequence -> sequence.steps().forEach(step -> minusPlaced(level, step, wood, need));
            case FallbackNode fallback -> minusPlaced(level, fallback.steps().get(0), wood, need);
            case ScriptCall call
            when SchemeAudit.entries(call).isPresent() -> {
                for (Schematic.PlacedEntry entry : SchemeAudit.entries(call).orElseThrow()) {
                    String block = bare(WoodSpecies.swap(entry.block(), wood));
                    BlockCoord at = entry.pos();
                    if (placed(level.getBlockState(new BlockPos(at.x(), at.y(), at.z())), block)) {
                        String item = bare(WoodSpecies.swap(SchematicBlocks.itemOf(entry.block()), wood));
                        need.computeIfPresent(item, (key, count) -> count > 1 ? count - 1 : null);
                    }
                }
            }
            case ScriptCall call
            when "place_block".equals(call.script())
                    && call.params().get("block") instanceof String block
                    && call.params().get("x") instanceof Integer x
                    && call.params().get("y") instanceof Integer y
                    && call.params().get("z") instanceof Integer z -> {
                String item = bare(WoodSpecies.swap(block, wood));
                if (placed(level.getBlockState(new BlockPos(x, y, z)), item)) {
                    need.computeIfPresent(item, (key, count) -> count > 1 ? count - 1 : null);
                }
            }
            default -> {}
        }
    }

    static String bare(String id) {
        int state = id.indexOf('[');
        String block = state < 0 ? id : id.substring(0, state);
        return block.startsWith(MINECRAFT) ? block.substring(MINECRAFT.length()) : block;
    }

    private record Scan(SettlementFacts facts, boolean furnaceBlock) {}

    private record StoneSite(BlockCoord home, String wood, StageSite stage) {}

    private static List<BlockPos> factOrigins(BlockCoord home, StageSite stage) {
        return Stream.of(home, stage.anchor(), stage.workYard())
                .map(point -> new BlockPos(point.x(), point.y(), point.z()))
                .toList();
    }

    private static Scan buildFacts(
            ServerLevel level, StoneSite site, StockView stock, BoardService service, PlanWorld world) {
        StageSite stage = site.stage();
        BlockCoord home = site.home();
        boolean groundsDone = service.board().ledger().has(BoardCase.CLEAR_GROUNDS, 1);
        boolean hasHome = true;
        boolean groundsCleared = true;
        int chests = 0;
        boolean craftingStation = false;
        boolean smithingTable = false;
        boolean anvil = false;
        boolean furnace = false;
        Set<BlockPos> seen = new HashSet<>();
        for (BlockPos start : factOrigins(home, stage)) {
            for (int dx = -SCAN_RADIUS; dx <= SCAN_RADIUS; dx++) {
                for (int dz = -SCAN_RADIUS; dz <= SCAN_RADIUS; dz++) {
                    if (dx * dx + dz * dz > SCAN_RADIUS * SCAN_RADIUS) {
                        continue;
                    }
                    BlockPos pos = start.offset(dx, 0, dz);
                    for (int dy = -2; dy <= 2; dy++) {
                        BlockPos check = pos.offset(0, dy, 0);
                        if (!level.isLoaded(check) || !seen.add(check)) {
                            continue;
                        }
                        var state = level.getBlockState(check);
                        boolean onPad = stage.levelColumn(check.getX(), check.getZ());
                        if (!groundsDone && onPad && (state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES))) {
                            groundsCleared = false;
                        }
                        if (state.is(Blocks.CHEST)) {
                            chests++;
                        }
                        if (state.is(Blocks.CRAFTING_TABLE)) {
                            craftingStation = true;
                        }
                        if (state.is(Blocks.FURNACE)) {
                            furnace = true;
                        }
                        if (state.is(Blocks.SMITHING_TABLE)) {
                            smithingTable = true;
                        }
                        if (state.getBlock() instanceof AnvilBlock) {
                            anvil = true;
                        }
                    }
                }
            }
        }
        Set<String> tools = toolStock(stock);
        Set<BoardCase> done = journalSteps(service, false, level, stage);
        Set<BoardCase> blocked = journalSteps(service, true, level, stage);
        boolean journal = world == PlanWorld.JOURNAL;
        boolean plotLevel = journal || PlotLeveling.isLevel(stage.anchor(), stage.level(), plotFeet(level, stage));
        boolean mineEntrance = built(level, done, BoardCase.BUILD_MINE, site, journal);
        boolean lighting = built(level, done, BoardCase.LIGHTING, site, journal);
        boolean fence = built(level, done, BoardCase.BUILD_FENCE, site, journal);
        SettlementFacts facts = new SettlementFacts(
                hasHome,
                groundsCleared,
                plotLevel,
                chests,
                built(level, done, BoardCase.STOCK_CHESTS, site, journal),
                craftingStation,
                tools,
                mineEntrance,
                built(level, done, BoardCase.CRAFT_FURNACE, site, journal),
                fence,
                lighting,
                built(level, done, BoardCase.BUILD_HOUSE, site, journal),
                built(level, done, BoardCase.BUILD_ORCHARD, site, journal),
                built(level, done, BoardCase.BUILD_STREETS, site, journal),
                smithingTable,
                anvil,
                done,
                blocked);
        return new Scan(facts, furnace);
    }

    private static boolean built(
            ServerLevel level, Set<BoardCase> done, BoardCase step, StoneSite site, boolean journal) {
        if (journal) {
            return done.contains(step);
        }
        Placed placed = audit(level, step, site.stage(), site.wood());
        if (!placed.confident() || placed.total() == 0) {
            return done.contains(step);
        }
        if (EXACT_STEPS.contains(step)) {
            return placed.done() == placed.total();
        }
        return (placed.total() - placed.done()) * LAYOUT_SLACK_DIVISOR <= placed.total();
    }

    public static TownSnapshot.Progress progress(
            ServerLevel level, BoardService service, BoardCase step, StageSite stage, String wood) {
        Placed placed = audit(level, step, stage, wood);
        boolean done = journalSteps(service, false, level, stage).contains(step);
        return new TownSnapshot.Progress(
                placed.done(), placed.total(), placed.confident() && done && placed.done() < placed.total());
    }

    static Placed audit(ServerLevel level, BoardCase step, StageSite stage, String fallback) {
        return BuiltAuditCache.of(level, step, stage, fallback);
    }

    static BuildGap.Gap gap(ServerLevel level, BoardCase step, StageSite stage, String fallback) {
        String wood = BuildingWoodChoice.stored(level, step, stage, fallback);
        return BuildGap.trace(
                TicketPlans.plan(step, 1, targetFor(level, step, stage, wood)), wood, probe(level), SCHEMES);
    }

    record Placed(int done, int total, int unknown) {
        boolean confident() {
            return unknown == 0;
        }
    }

    static Placed placedBlocks(ServerLevel level, TaskNode plan, String wood) {
        BuildGap.Gap gap = BuildGap.trace(plan, wood, probe(level), SCHEMES);
        return new Placed(gap.done(), gap.total(), gap.unknown());
    }

    static BuildGap.Probe probe(ServerLevel level) {
        return WorldProbe.of(level);
    }

    static boolean placed(BlockState state, String block) {
        return BlockMatch.matches(
                BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString(), block);
    }

    private static Set<BoardCase> journalSteps(
            BoardService service, boolean blocked, ServerLevel level, StageSite stage) {
        Set<BoardCase> steps = EnumSet.noneOf(BoardCase.class);
        for (BoardCase step : StageReopen.STEPS) {
            String id = FIRST_PREFIX + step.name().toLowerCase(Locale.ROOT);
            boolean match = blocked
                    ? service.board().statusOf(id).orElse(null) == BoardStatus.BLOCKED
                    : service.board().ledger().has(step, 1) && partsDone(service, step, level, stage);
            if (match) {
                steps.add(step);
            }
        }
        return steps;
    }

    private static boolean partsDone(BoardService service, BoardCase step, ServerLevel level, StageSite stage) {
        if (step == BoardCase.CLEAR_GROUNDS) {
            for (int part = 2; part <= GroundsPlan.CLEAR_PARTS; part++) {
                if (active(service, partId(step, part))) {
                    return false;
                }
            }
            return true;
        }
        if (step != BoardCase.BUILD_FENCE) {
            return true;
        }
        return FenceParts.done(service, FenceParts.of(level, stage, partsOf(step)));
    }

    private static Set<String> toolStock(StockView stock) {
        Set<String> tools = new HashSet<>();
        for (Map.Entry<String, Integer> entry : stock.available().entrySet()) {
            if (entry.getValue() > 0) {
                tools.add(entry.getKey());
            }
        }
        return tools;
    }

    public static Optional<SettlementPlot> plotOf(
            ServerLevel level, BoardService service, List<SpiritEntity> spirits, long now) {
        List<BlockCoord> homes = homes(spirits);
        SettlementPlotStore store = SettlementPlotStore.get(level);
        BlockCoord center = SettlementPlot.center(homes);
        TownPlan town = TownSurvey.town(level, service, store, center, now);
        IntBinaryOperator feet = (x, z) -> GroundProbe.feet(level, x, z, center.y());
        Optional<StageSite> draft =
                StageSites.find(town, 1, center.y(), DataTables.INSTANCE.town().current());
        if (draft.isEmpty()) {
            if (now % PLAN_WARN_EVERY != 0) {
                return Optional.empty();
            }
            LOGGER.warn(
                    "[plot] в плане у {} нет мест этапа 1 (не поместилось {}): первые шаги ждут другого места дома",
                    center,
                    town.unplaced());
            return Optional.empty();
        }
        Optional<SettlementPlot> stored = store.plot();
        if (stored.isPresent() && stored.orElseThrow().covers(center)) {
            SettlementPlot kept = PlotHeights.rechecked(service, store, stored.orElseThrow(), feet, now);
            return Optional.of(checkedWood(level, service, store, kept, now));
        }
        String wood = SettlementWood.survey(level, center, service.stock().available());
        return Optional.of(PlotHeights.surveyed(service, store, draft.orElseThrow(), homes.size(), wood, feet, now));
    }

    private static SettlementPlot checkedWood(
            ServerLevel level, BoardService service, SettlementPlotStore store, SettlementPlot plot, long now) {
        if (service.board().ledger().has(BoardCase.CLEAR_GROUNDS, 1)) {
            return plot;
        }
        Optional<String> ruled =
                SettlementWood.biomeRules(service.stock().available(), SettlementWood.biomeAt(level, plot.anchor()));
        if (ruled.isEmpty() || ruled.get().equals(plot.wood())) {
            return plot;
        }
        SettlementPlot fixed = plot.withWood(ruled.get());
        store.fix(fixed);
        LOGGER.info(
                "[plot] порода плана {} → {}: запас брёвен меньше 16 — решает биом (INC-62)",
                plot.wood(),
                fixed.wood());
        service.journal().recordEntry(fixed.journalEntry(now, ConfigStamp.current()));
        return fixed;
    }

    private static int homeY(List<SpiritEntity> spirits) {
        return SettlementPlot.center(homes(spirits)).y();
    }

    private static List<BlockCoord> homes(List<SpiritEntity> spirits) {
        return spirits.stream().flatMap(spirit -> spirit.home().stream()).toList();
    }
}
