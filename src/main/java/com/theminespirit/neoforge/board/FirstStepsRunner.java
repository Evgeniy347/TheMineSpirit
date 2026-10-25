package com.theminespirit.neoforge.board;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardLog;
import com.theminespirit.core.board.BoardOutcome;
import com.theminespirit.core.board.BoardRules;
import com.theminespirit.core.board.BoardStatus;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.CaseMaterials;
import com.theminespirit.core.board.ConfigStamp;
import com.theminespirit.core.board.FirstStepsPlan;
import com.theminespirit.core.board.GatherBatch;
import com.theminespirit.core.board.GroundsPlan;
import com.theminespirit.core.board.MaterialSource;
import com.theminespirit.core.board.MineDescentBottom;
import com.theminespirit.core.board.PlotLeveling;
import com.theminespirit.core.board.RepairPlan;
import com.theminespirit.core.board.ResourceGate;
import com.theminespirit.core.board.SettlementFacts;
import com.theminespirit.core.board.SettlementPlot;
import com.theminespirit.core.board.StagePlans;
import com.theminespirit.core.board.StockNeed;
import com.theminespirit.core.board.StockView;
import com.theminespirit.core.board.TicketPlans;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.board.ToolGate;
import com.theminespirit.core.board.ToolSet;
import com.theminespirit.core.board.TorchRules;
import com.theminespirit.core.building.BuildRotation;
import com.theminespirit.core.building.BuildingType;
import com.theminespirit.core.building.MineLayout;
import com.theminespirit.core.building.Preset;
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
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;

public final class FirstStepsRunner {
    private static final Set<BoardCase> WOODEN_BUILDINGS = EnumSet.of(BoardCase.BUILD_FENCE, BoardCase.BUILD_HOUSE);
    private static final int LAYOUT_SLACK_DIVISOR = 50;
    private static final Set<BoardCase> LAYOUT_STEPS =
            EnumSet.of(BoardCase.LIGHTING, BoardCase.BUILD_FENCE, BoardCase.BUILD_HOUSE);
    private static final Set<BoardCase> PREFETCH_DURING =
            EnumSet.of(BoardCase.CLEAR_GROUNDS, BoardCase.LIGHTING, BoardCase.BUILD_FENCE);
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int SCAN_RADIUS = 16;
    private static final String GATHER_PREFIX = "gather:";
    private static final String GATHER_PART_SUFFIX = ":2";
    private static final List<BoardCase> STONE_STEPS = List.of(
            BoardCase.CRAFT_STONE_PICKAXE,
            BoardCase.CRAFT_STONE_AXE,
            BoardCase.CRAFT_STONE_SHOVEL,
            BoardCase.CRAFT_STONE_SWORD,
            BoardCase.CRAFT_FURNACE);
    private static final Set<BoardCase> BEFORE_YARD = EnumSet.of(
            BoardCase.CRAFTING_STATION,
            BoardCase.CRAFT_WOODEN_PICKAXE,
            BoardCase.CRAFT_WOODEN_AXE,
            BoardCase.CRAFT_WOODEN_SHOVEL);
    private static final Set<BoardCase> EXACT_STEPS =
            EnumSet.of(BoardCase.BUILD_FENCE, BoardCase.BUILD_HOUSE, BoardCase.LIGHTING, BoardCase.STOCK_CHESTS);
    private static final Set<BoardCase> AT_YARD_TABLE =
            EnumSet.of(BoardCase.BUILD_FENCE, BoardCase.BUILD_HOUSE, BoardCase.LIGHTING, BoardCase.CRAFT_FURNACE);
    private static final String FIRST_PREFIX = "first:";
    private static final String MINECRAFT = "minecraft:";
    private static final String CLEAR_PART_PREFIX = "clear:";
    private static final String STONE_PART_PREFIX = "stone:";
    private static final String FENCE_PART_PREFIX = "fence:";
    private static final String HOUSE_PART_PREFIX = "house:";
    private static final String COBBLESTONE = "cobblestone";
    private static final String FURNACE_ITEM = "furnace";
    private static final String SAPLING = "_sapling";
    private static final long PLAN_WARN_EVERY = 600;
    private static final int EXPAND_DEPTH = 4;
    private static final int LEVELING_SHOWN = 6;

    private FirstStepsRunner() {}

    public enum PlanWorld {
        AUDIT,
        JOURNAL
    }

    public static void tick(
            ServerLevel level, BoardService service, List<SpiritEntity> spirits, StockView stock, long now) {
        tick(level, service, spirits, stock, now, PlanWorld.AUDIT);
    }

    public static void tick(
            ServerLevel level,
            BoardService service,
            List<SpiritEntity> spirits,
            StockView stock,
            long now,
            PlanWorld world) {
        if (!SpiritConfig.boardEnabled() || spirits.isEmpty()) {
            return;
        }
        if (spirits.stream().noneMatch(spirit -> spirit.home().isPresent())) {
            return;
        }
        if (plotOf(level, service, spirits, now).isEmpty()) {
            return;
        }
        StageSite stage = SettlementPlotStore.get(level)
                .site()
                .orElseThrow(() -> new IllegalStateException(
                        "Участок обмерен, а места этапа 1 нет: план поселения не сохранился в мире"));
        PlotAudit.tick(level, service, stage, now);
        OldChests.tick(level, service, stage, now);
        ChestSpaceWatch.tick(level, service, stage, now);
        SettlementPlotStore.get(level).town().ifPresent(town -> PlanDemolition.tick(level, service, stage, town, now));
        BlockCoord home = new BlockCoord(
                stage.anchor().x(), homeY(spirits), stage.anchor().z());
        String wood = woodOf(level, home, stock);
        StoneSite site = new StoneSite(home, wood, stage);
        Scan scan = buildFacts(level, site, stock, service, world);
        SettlementFacts facts = scan.facts();
        boolean mine = facts.mineEntrance();
        boolean yardTable = facts.done().contains(BoardCase.STOCK_CHESTS);
        boolean clearing = clearing(service);
        List<BoardCase> ready = FirstStepsPlan.ready(facts).stream()
                .filter(step -> yardTable || !AT_YARD_TABLE.contains(step))
                .filter(step -> !clearing || step == BoardCase.CLEAR_GROUNDS)
                .toList();
        Optional<BoardCase> next = ready.stream().findFirst();
        String log = WoodSpecies.swap("oak_log", wood);
        dropOtherWood(level, service, wood);
        ensurePickaxe(level, service, spirits, stock, site, next, now);
        if (ready.size() == 1 && STONE_STEPS.contains(next.orElseThrow())) {
            parallelStone(level, service, facts, site, stock, next.orElseThrow(), now);
        }
        next.filter(PREFETCH_DURING::contains)
                .filter(current -> hasActive(service, current))
                .ifPresent(current -> prefetch(level, service, facts, site, stock, current, now));
        int torches = 0;
        for (BoardCase caseValue : ready) {
            if (!hasActive(service, caseValue)) {
                StepContext context = new StepContext(level, service, spirits, stock, facts, stage, wood, now);
                torches = Math.max(torches, openIfStocked(context, caseValue, false));
            }
        }
        for (BoardCase caseValue : FirstStepsPlan.blockedStageOne(facts)) {
            StepContext context = new StepContext(level, service, spirits, stock, facts, stage, wood, now);
            openIfStocked(context, caseValue, true);
        }
        WoodWatch.tick(level, service, spirits, stock, home, wood, now);
        TorchWatch.tick(
                service,
                spirits,
                stage.workYard(),
                stock,
                new TorchWatch.Site(scan.furnaceBlock(), mine, torches, log),
                now);
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

    private record StepContext(
            ServerLevel level,
            BoardService service,
            List<SpiritEntity> spirits,
            StockView stock,
            SettlementFacts facts,
            StageSite stage,
            String wood,
            long now) {}

    private static int openIfStocked(StepContext context, BoardCase caseValue, boolean blocked) {
        ServerLevel level = context.level();
        BoardService service = context.service();
        List<SpiritEntity> spirits = context.spirits();
        StockView stock = context.stock();
        SettlementFacts facts = context.facts();
        StageSite stage = context.stage();
        String wood = context.wood();
        long now = context.now();
        BlockCoord home = new BlockCoord(
                stage.anchor().x(), homeY(spirits), stage.anchor().z());
        TicketTarget target = targetFor(level, caseValue, stage, wood);
        if (caseValue == BoardCase.CLEAR_GROUNDS) {
            target = target.withLeveling(leveling(level, stage, stock));
        }
        if (caseValue == BoardCase.PLANT_GROVE) {
            Optional<TicketTarget> grove = GroveStock.trimmed(level, target, stock);
            if (grove.isEmpty()) {
                return 0;
            }
            target = grove.orElseThrow();
        }
        MaterialSource source = materialSource(level);
        Map<String, Integer> need = new HashMap<>();
        materials(source, caseValue, target)
                .forEach((item, count) -> need.merge(WoodSpecies.swap(item, wood), count, Integer::sum));
        if (caseValue == BoardCase.CLEAR_GROUNDS) {
            need.remove(PlotLeveling.DEFAULT_BLOCK);
        }
        if (caseValue == BoardCase.BUILD_MINE) {
            int floors = mineFloorsToPlace(level, stage);
            String block = stock.available().getOrDefault(COBBLESTONE, 0) >= floors
                            || stock.available().getOrDefault(PlotLeveling.DEFAULT_BLOCK, 0) < floors
                    ? COBBLESTONE
                    : PlotLeveling.DEFAULT_BLOCK;
            if (floors > 0) {
                need.merge(block, floors, Integer::sum);
            }
        }
        if (caseValue == BoardCase.LIGHTING) {
            minusPlaced(level, TicketPlans.plan(caseValue, 1, target), wood, need);
        }
        if (WOODEN_BUILDINGS.contains(caseValue)) {
            minusPlaced(level, TicketPlans.plan(caseValue, 1, target), wood, need);
            if (blocked) {
                asPlanks(need, wood);
            } else {
                dropOwnWood(need, stock, wood);
            }
        }
        Map<String, Integer> missing = ResourceGate.missing(new StockNeed(need), stock);
        int torches = need.getOrDefault(TorchRules.TORCH, 0);
        if (!missing.isEmpty()) {
            LOGGER.info("Кейс первых шагов {} ждёт: не хватает {}", caseValue, missing);
            for (Map.Entry<String, Integer> entry : missing.entrySet()) {
                if (gatherable(entry.getKey(), facts)) {
                    int shortage = entry.getValue();
                    if (COBBLESTONE.equals(entry.getKey())) {
                        shortage = Math.max(shortage, stoneAhead(level, source, facts, stage, wood, stock));
                    }
                    postGather(service, source, home, entry.getKey(), shortage, helpers(entry.getKey(), spirits), now);
                }
            }
            return torches;
        }
        openStep(level, service, new OpenStep(caseValue, target, need, wood), source, now);
        return torches;
    }

    private record OpenStep(BoardCase caseValue, TicketTarget target, Map<String, Integer> need, String wood) {}

    private static void openStep(
            ServerLevel level, BoardService service, OpenStep step, MaterialSource source, long now) {
        BoardCase caseValue = step.caseValue();
        TicketTarget target = step.target();
        Map<String, Integer> need = step.need();
        String wood = step.wood();
        int parts = partsOf(caseValue);
        if (LAYOUT_STEPS.contains(caseValue)
                || caseValue == BoardCase.CLEAR_GROUNDS
                || caseValue == BoardCase.STOCK_CHESTS
                || caseValue == BoardCase.BUILD_MINE) {
            if (reopenDone(level, service, step, parts, now)) {
                return;
            }
        }
        BoardOutcome outcome = BoardOutcome.ACCEPTED;
        for (int part = 1; part <= parts; part++) {
            TicketTarget partTarget = parts == 1 ? target : target.withCount(part);
            StageWork work = target.work();
            if (caseValue == BoardCase.BUILD_FENCE && work != null) {
                partTarget = partTarget.withWork(
                        new StageWork(work.site(), StageBlocks.of(level, caseValue, work.site(), part)));
            }
            Map<String, Integer> reserve = part == 1 ? need : Map.of();
            if (caseValue == BoardCase.BUILD_FENCE) {
                reserve = new HashMap<>(partNeed(level, source, partTarget, wood));
                reserve.keySet().retainAll(need.keySet());
            }
            BoardOutcome submitted = service.submit(
                    new BoardTicket(
                            partId(caseValue, part),
                            caseValue,
                            Optional.empty(),
                            BoardRules.priorityOf(caseValue),
                            now + part - 1,
                            1,
                            BoardTicket.ALWAYS_CAUSE,
                            List.of(),
                            Map.copyOf(reserve)),
                    partTarget);
            if (part == 1) {
                outcome = submitted;
            }
        }
        if (outcome == BoardOutcome.ACCEPTED) {
            LOGGER.info("Кейс первых шагов {} открыт: материалы на месте", caseValue);
        }
    }

    private static boolean reopenDone(ServerLevel level, BoardService service, OpenStep step, int parts, long now) {
        BoardCase caseValue = step.caseValue();
        if (service.board().statusOf(partId(caseValue, 1)).orElse(null) != BoardStatus.DONE) {
            return false;
        }
        StageWork work = Objects.requireNonNull(
                step.target().work(), "Шаг первых шагов без места этапа: план поселения не передан в задание");
        Placed placed = audit(level, caseValue, work.site(), step.wood());
        int missing = placed.total() - placed.done();
        LOGGER.info(
                "[layout] {} сделан по журналу, но в мире нет {} из {} блоков плана: восстановление",
                caseValue,
                missing,
                placed.total());
        service.journal()
                .recordEntry(BoardLog.note(
                        "layout:" + caseValue.name().toLowerCase(Locale.ROOT),
                        caseValue,
                        now,
                        List.of("missing=" + missing, "total=" + placed.total()),
                        null,
                        ConfigStamp.current()));
        BoardOutcome repair = StageRepair.submit(service, caseValue, step.target(), missing, placed.total(), now);
        if (repair == BoardOutcome.ACCEPTED || repair == BoardOutcome.DUPLICATE) {
            return true;
        }
        for (int part = 1; part <= parts; part++) {
            String id = partId(caseValue, part);
            if (service.board().statusOf(id).orElse(null) == BoardStatus.DONE) {
                service.reopen(id);
            }
        }
        return false;
    }

    public static void dropOtherWood(ServerLevel level, BoardService service, String wood) {
        for (BoardTicket ticket : List.copyOf(service.board().all())) {
            if (!ticket.id().startsWith(GATHER_PREFIX) || !active(service, ticket.id())) {
                continue;
            }
            String item = bare(ticket.id().substring(GATHER_PREFIX.length()));
            Optional<String> species = WoodSpecies.speciesOf(item);
            if (species.isPresent() && !species.orElseThrow().equals(wood) && service.cancel(level, ticket.id())) {
                LOGGER.info("[wood] снята добыча {}: порода поселения {}", ticket.id(), wood);
            }
        }
    }

    private static boolean clearing(BoardService service) {
        return service.board().all().stream()
                .filter(ticket -> ticket.boardCase() == BoardCase.CLEAR_GROUNDS)
                .anyMatch(ticket -> active(service, ticket.id()));
    }

    public static boolean stageOneBuilding(BoardService service) {
        return service.board().all().stream()
                .anyMatch(ticket -> (ticket.id().startsWith(FIRST_PREFIX)
                                || ticket.id().startsWith(FENCE_PART_PREFIX)
                                || ticket.id().startsWith(HOUSE_PART_PREFIX)
                                || ticket.id().startsWith(RepairPlan.PREFIX)
                                || ticket.id().startsWith(CLEAR_PART_PREFIX))
                        && active(service, ticket.id()));
    }

    private static int partsOf(BoardCase caseValue) {
        return switch (caseValue) {
            case CLEAR_GROUNDS -> GroundsPlan.CLEAR_PARTS;
            case BUILD_FENCE -> TicketPlans.FENCE_PARTS;
            case BUILD_HOUSE -> StagePlans.HOUSE_PARTS;
            default -> 1;
        };
    }

    private static String partId(BoardCase caseValue, int part) {
        if (part == 1) {
            return FIRST_PREFIX + caseValue.name().toLowerCase(Locale.ROOT);
        }
        String prefix = switch (caseValue) {
            case BUILD_FENCE -> FENCE_PART_PREFIX;
            case BUILD_HOUSE -> HOUSE_PART_PREFIX;
            default -> CLEAR_PART_PREFIX;
        };
        return prefix + part;
    }

    private static Map<String, Integer> partNeed(
            ServerLevel level, MaterialSource source, TicketTarget target, String wood) {
        Map<String, Integer> need = new HashMap<>();
        materials(source, BoardCase.BUILD_FENCE, target)
                .forEach((item, count) -> need.merge(WoodSpecies.swap(item, wood), count, Integer::sum));
        minusPlaced(level, TicketPlans.plan(BoardCase.BUILD_FENCE, 1, target), wood, need);
        return need;
    }

    private static void parallelStone(
            ServerLevel level,
            BoardService service,
            SettlementFacts facts,
            StoneSite site,
            StockView stock,
            BoardCase current,
            long now) {
        String wood = site.wood();
        MaterialSource source = materialSource(level);
        Map<String, Integer> booked = new HashMap<>();
        boolean after = false;
        for (BoardCase step : STONE_STEPS) {
            TicketTarget target = targetFor(level, step, site.stage(), wood);
            Map<String, Integer> need = new HashMap<>();
            materials(source, step, target)
                    .forEach((item, count) -> need.merge(WoodSpecies.swap(item, wood), count, Integer::sum));
            if (step == current) {
                after = true;
            }
            if (facts.done().contains(step)) {
                continue;
            }
            need.forEach((item, count) -> booked.merge(item, count, Integer::sum));
            if (!after || step == current || hasActive(service, step)) {
                continue;
            }
            if (!ResourceGate.missing(new StockNeed(booked), stock).isEmpty()) {
                return;
            }
            BoardOutcome outcome = service.submit(
                    new BoardTicket(
                            STONE_PART_PREFIX + step.name().toLowerCase(Locale.ROOT),
                            step,
                            Optional.empty(),
                            BoardRules.priorityOf(step),
                            now,
                            1,
                            BoardTicket.ALWAYS_CAUSE,
                            List.of(),
                            Map.copyOf(need)),
                    target);
            if (outcome == BoardOutcome.ACCEPTED) {
                LOGGER.info("Каменный шаг {} открыт вместе с {}: булыжника хватает на оба", step, current);
            }
        }
    }

    private static PlotLeveling leveling(ServerLevel level, StageSite site, StockView stock) {
        BlockCoord anchor = site.anchor();
        PlotLeveling plan = PlotLeveling.plan(anchor, site.level(), plotFeet(level, site));
        IntBinaryOperator feet =
                (x, z) -> site.mineColumn(x, z) ? Integer.MIN_VALUE : GroundProbe.feet(level, x, z, anchor.y());
        plan = plan.withVoids(PlotLeveling.voids(
                anchor,
                site.level(),
                feet,
                (x, y, z) -> level.getBlockState(new BlockPos(x, y, z)).isAir()));
        if (plan.fillVolume() > 0 && stock.available().getOrDefault(COBBLESTONE, 0) >= plan.fillVolume()) {
            plan = plan.withBlock(COBBLESTONE);
        }
        if (!plan.cut().isEmpty() || !plan.fill().isEmpty()) {
            LOGGER.info(
                    "Выравнивание участка на y={}: срезка {} ящиков, подсыпка {} блоков {}, пустот под участком {}",
                    anchor.y(),
                    plan.cut().size(),
                    plan.fillVolume(),
                    plan.block(),
                    plan.voids().size());
            if (plan.cut().size() + plan.fill().size() <= LEVELING_SHOWN) {
                LOGGER.info("[plot] остаток выравнивания: срезка {}, подсыпка {}", plan.cut(), plan.fill());
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

    private static void prefetch(
            ServerLevel level,
            BoardService service,
            SettlementFacts facts,
            StoneSite site,
            StockView stock,
            BoardCase current,
            long now) {
        BlockCoord home = site.home();
        Optional<BoardCase> building = FirstStepsPlan.nextAfter(facts, current, WOODEN_BUILDINGS);
        if (building.isEmpty() || hasActive(service, building.orElseThrow())) {
            return;
        }
        String wood = woodOf(level, home, stock);
        MaterialSource source = materialSource(level);
        Map<String, Integer> need = new HashMap<>();
        materials(source, building.orElseThrow(), targetFor(level, building.orElseThrow(), site.stage(), wood))
                .forEach((item, count) -> need.merge(WoodSpecies.swap(item, wood), count, Integer::sum));
        Map<String, Integer> raw = new HashMap<>();
        ResourceGate.missing(new StockNeed(need), stock).forEach((item, count) -> expand(source, item, count, raw, 0));
        Map<String, Integer> rawNeed = new HashMap<>();
        raw.forEach((item, count) -> rawNeed.merge(WoodSpecies.swap(item, wood), count, Integer::sum));
        ResourceGate.missing(new StockNeed(rawNeed), stock).forEach((item, count) -> {
            if (gatherable(item, facts)) {
                postGather(service, source, home, item, count, 1, now);
            }
        });
    }

    private static void expand(MaterialSource source, String item, int count, Map<String, Integer> raw, int depth) {
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
        BlockCoord point = BEFORE_YARD.contains(caseValue) ? stage.anchor() : stage.workYard();
        TicketTarget target =
                TicketTarget.at(point).withWork(new StageWork(stage, StageBlocks.of(level, caseValue, stage, 0)));
        if (WOODEN_BUILDINGS.contains(caseValue) && !WoodSpecies.OAK.equals(wood)) {
            target = target.withWood(wood);
        }
        return ToolSet.isTool(caseValue) ? target.withToolCount(1) : target;
    }

    private static int stoneAhead(
            ServerLevel level,
            MaterialSource source,
            SettlementFacts facts,
            StageSite stage,
            String wood,
            StockView stock) {
        int need = 0;
        for (BoardCase step : STONE_STEPS) {
            if (!facts.done().contains(step)) {
                need += materials(source, step, targetFor(level, step, stage, wood))
                        .getOrDefault(COBBLESTONE, 0);
            }
        }
        return Math.max(0, need - stock.available().getOrDefault(COBBLESTONE, 0));
    }

    private static int helpers(String item, List<SpiritEntity> spirits) {
        if (!COBBLESTONE.equals(item) && !TorchRules.COAL.equals(item)) {
            return spirits.size();
        }
        int miners = 0;
        for (SpiritEntity spirit : spirits) {
            var inventory = spirit.getInventory();
            for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
                if (inventory.getItem(slot).getItem() instanceof PickaxeItem) {
                    miners++;
                    break;
                }
            }
        }
        return miners;
    }

    static void postGather(
            BoardService service,
            MaterialSource source,
            BlockCoord home,
            String item,
            int shortage,
            int helpers,
            long now) {
        String id = GATHER_PREFIX + item;
        if (active(service, id) || active(service, id + GATHER_PART_SUFFIX)) {
            return;
        }
        int batch = GatherBatch.round(shortage, source.outputCount(item));
        List<Integer> portions = GatherBatch.split(batch, source.outputCount(item), helpers);
        for (int part = 0; part < portions.size(); part++) {
            submitGather(service, home, part == 0 ? id : id + GATHER_PART_SUFFIX, item, portions.get(part), now);
        }
    }

    private static boolean active(BoardService service, String id) {
        BoardStatus status = service.board().statusOf(id).orElse(null);
        return status == BoardStatus.PENDING
                || status == BoardStatus.WAITING_RESOURCES
                || status == BoardStatus.ASSIGNED;
    }

    private static void submitGather(
            BoardService service, BlockCoord home, String id, String item, int batch, long now) {
        LOGGER.info("Поставлено: добыть {} ×{} (кейс первых шагов ждёт)", item, batch);
        service.submit(
                new BoardTicket(
                        id,
                        BoardCase.GATHER_MATERIALS,
                        Optional.empty(),
                        BoardRules.priorityOf(BoardCase.GATHER_MATERIALS),
                        now,
                        1,
                        BoardTicket.ALWAYS_CAUSE,
                        List.of(),
                        Map.of()),
                TicketTarget.at(home).withSupply(item, batch));
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
                boolean underground = DataTables.INSTANCE
                        .presets()
                        .current()
                        .byId(file)
                        .map(preset -> preset.type() == BuildingType.UNDERGROUND)
                        .orElse(false);
                return underground ? Map.of() : PresetLibrary.countMaterials(SchematicResources.load(file));
            }
        };
    }

    private static void ensurePickaxe(
            ServerLevel level,
            BoardService service,
            List<SpiritEntity> spirits,
            StockView stock,
            StoneSite site,
            Optional<BoardCase> next,
            long now) {
        boolean gated = next.filter(step -> ToolGate.required(step).isPresent()).isPresent()
                || hasActive(service, BoardCase.CLEAR_GROUNDS)
                || hasActive(service, BoardCase.BUILD_MINE);
        boolean nobody = spirits.stream()
                .noneMatch(spirit -> ToolGate.allows(BoardCase.CLEAR_GROUNDS, BoardService.itemIds(spirit)));
        if (!gated || !nobody || active(service, BoardService.PICKAXE_TICKET)) {
            return;
        }
        MaterialSource source = materialSource(level);
        TicketTarget target = targetFor(level, BoardCase.CRAFT_WOODEN_PICKAXE, site.stage(), site.wood());
        Map<String, Integer> need = new HashMap<>();
        materials(source, BoardCase.CRAFT_WOODEN_PICKAXE, target)
                .forEach((item, count) -> need.merge(WoodSpecies.swap(item, site.wood()), count, Integer::sum));
        Map<String, Integer> missing = ResourceGate.missing(new StockNeed(need), stock);
        if (!missing.isEmpty()) {
            LOGGER.info("[tool] кирки нет ни у одного духа: на кирку не хватает {} — добыча", missing);
            missing.forEach((item, count) -> postGather(service, source, site.home(), item, count, 1, now));
            return;
        }
        BoardOutcome outcome = service.submit(
                new BoardTicket(
                        BoardService.PICKAXE_TICKET,
                        BoardCase.CRAFT_WOODEN_PICKAXE,
                        Optional.empty(),
                        BoardRules.priorityOf(BoardCase.CRAFT_WOODEN_PICKAXE),
                        now,
                        1,
                        BoardTicket.ALWAYS_CAUSE,
                        List.of(),
                        Map.copyOf(need)),
                target);
        if (outcome == BoardOutcome.ACCEPTED) {
            LOGGER.info("[tool] кирки нет ни у одного духа: поставлено {}", BoardService.PICKAXE_TICKET);
        }
    }

    private static boolean hasActive(BoardService service, BoardCase boardCase) {
        return service.board().all().stream()
                .filter(t -> t.boardCase() == boardCase)
                .anyMatch(t -> service.board()
                        .statusOf(t.id())
                        .map(s -> s == BoardStatus.PENDING
                                || s == BoardStatus.WAITING_RESOURCES
                                || s == BoardStatus.ASSIGNED)
                        .orElse(false));
    }

    private static boolean gatherable(String item, SettlementFacts facts) {
        if (TorchRules.handles(item) || item.endsWith(SAPLING)) {
            return false;
        }
        return !COBBLESTONE.equals(item) || facts.mineEntrance();
    }

    public static void minusPlaced(ServerLevel level, TaskNode plan, String wood, Map<String, Integer> need) {
        switch (plan) {
            case SequenceNode sequence -> sequence.steps().forEach(step -> minusPlaced(level, step, wood, need));
            case FallbackNode fallback -> minusPlaced(level, fallback.steps().get(0), wood, need);
            case ScriptCall call
            when "build_schematic".equals(call.script())
                    && !call.params().containsKey("rotation")
                    && call.params().get("file") instanceof String file
                    && call.params().get("x") instanceof Integer x
                    && call.params().get("y") instanceof Integer y
                    && call.params().get("z") instanceof Integer z -> {
                for (Schematic.Entry entry : SchematicResources.load(file).blocks()) {
                    if (entry.optional() || SchematicBlocks.isSecondaryPart(entry.block(), entry.state())) {
                        continue;
                    }
                    String block = bare(WoodSpecies.swap(entry.block(), wood));
                    BlockState state =
                            level.getBlockState(new BlockPos(x + entry.dx(), y + entry.dy(), z + entry.dz()));
                    if (placed(state, block)) {
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

    private static String bare(String id) {
        int state = id.indexOf('[');
        String block = state < 0 ? id : id.substring(0, state);
        return block.startsWith(MINECRAFT) ? block.substring(MINECRAFT.length()) : block;
    }

    private record Scan(SettlementFacts facts, boolean furnaceBlock) {}

    private record StoneSite(BlockCoord home, String wood, StageSite stage) {}

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
        BlockCoord anchor = stage.anchor();
        for (BlockPos start :
                List.of(new BlockPos(home.x(), home.y(), home.z()), new BlockPos(anchor.x(), anchor.y(), anchor.z()))) {
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
        Set<BoardCase> done = journalSteps(service, false);
        Set<BoardCase> blocked = journalSteps(service, true);
        boolean furnaceAvailable = furnace || stock.available().getOrDefault(FURNACE_ITEM, 0) > 0;
        boolean journal = world == PlanWorld.JOURNAL;
        boolean plotLevel = journal || PlotLeveling.isLevel(stage.anchor(), stage.level(), plotFeet(level, stage));
        boolean mineEntrance = built(level, done, BoardCase.BUILD_MINE, site, journal);
        boolean lighting = built(level, done, BoardCase.LIGHTING, site, journal);
        boolean fence = built(level, done, BoardCase.BUILD_FENCE, site, journal);
        boolean houses = built(level, done, BoardCase.BUILD_HOUSE, site, journal);
        SettlementFacts facts = new SettlementFacts(
                hasHome,
                groundsCleared,
                plotLevel,
                chests,
                craftingStation,
                tools,
                mineEntrance,
                furnaceAvailable,
                fence,
                lighting,
                houses,
                smithingTable,
                anvil,
                done,
                blocked);
        return new Scan(facts, furnace);
    }

    private static boolean built(
            ServerLevel level, Set<BoardCase> done, BoardCase step, StoneSite site, boolean journal) {
        if (!done.contains(step) || journal) {
            return done.contains(step);
        }
        Placed placed = audit(level, step, site.stage(), site.wood());
        if (EXACT_STEPS.contains(step)) {
            return placed.done() == placed.total();
        }
        return (placed.total() - placed.done()) * LAYOUT_SLACK_DIVISOR <= placed.total();
    }

    public static TownSnapshot.Progress progress(
            ServerLevel level, BoardService service, BoardCase step, StageSite stage, String wood) {
        Placed placed = audit(level, step, stage, wood);
        boolean done = journalSteps(service, false).contains(step);
        return new TownSnapshot.Progress(placed.done(), placed.total(), done && placed.done() < placed.total());
    }

    private static Placed audit(ServerLevel level, BoardCase step, StageSite stage, String wood) {
        if (step == BoardCase.BUILD_MINE) {
            return mineAudit(level, stage);
        }
        return placedBlocks(level, TicketPlans.plan(step, 1, targetFor(level, step, stage, wood)), wood);
    }

    private static int mineFloorsToPlace(ServerLevel level, StageSite stage) {
        Placed floors = mineFloors(level, stage);
        return floors.total() - floors.done();
    }

    private static Placed mineFloors(ServerLevel level, StageSite stage) {
        Optional<MineLayout> layout = mineLayout(stage);
        if (layout.isEmpty()) {
            return new Placed(0, 0);
        }
        int done = 0;
        for (BlockCoord cell : layout.get().floors()) {
            BlockState state = level.getBlockState(new BlockPos(cell.x(), cell.y(), cell.z()));
            if (SchematicBlocks.isFloorClass(
                    BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString())) {
                done++;
            }
        }
        return new Placed(done, layout.get().floors().size());
    }

    private static Optional<MineLayout> mineLayout(StageSite stage) {
        Optional<Preset> preset = DataTables.INSTANCE.presets().current().byId(TicketPlans.MINE_DESCENT_SCHEME);
        return preset.map(found -> MineLayout.place(
                found.schematic(),
                found.entry(),
                stage.mineEntrance(),
                BuildRotation.parse(TicketPlans.MINE_DESCENT_ROTATION),
                MineLayout.Extent.NONE));
    }

    private static Placed mineAudit(ServerLevel level, StageSite stage) {
        Optional<MineLayout> layout = mineLayout(stage);
        if (layout.isEmpty()) {
            return new Placed(0, 0);
        }
        int done = 0;
        for (BlockCoord cell : layout.get().air()) {
            BlockPos pos = new BlockPos(cell.x(), cell.y(), cell.z());
            if (level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) {
                done++;
            }
        }
        return new Placed(done, layout.get().air().size()).plus(mineFloors(level, stage));
    }

    private record Placed(int done, int total) {
        Placed plus(Placed other) {
            return new Placed(done + other.done, total + other.total);
        }
    }

    private static Placed placedBlocks(ServerLevel level, TaskNode plan, String wood) {
        return switch (plan) {
            case FallbackNode fallback -> placedBlocks(level, fallback.steps().get(0), wood);
            case SequenceNode sequence ->
                sequence.steps().stream()
                        .map(step -> placedBlocks(level, step, wood))
                        .reduce(new Placed(0, 0), Placed::plus);
            case ScriptCall call
            when "place_block".equals(call.script())
                    && call.params().get("block") instanceof String block
                    && call.params().get("x") instanceof Integer x
                    && call.params().get("y") instanceof Integer y
                    && call.params().get("z") instanceof Integer z ->
                new Placed(
                        placed(level.getBlockState(new BlockPos(x, y, z)), bare(WoodSpecies.swap(block, wood))) ? 1 : 0,
                        1);
            case ScriptCall call
            when "build_schematic".equals(call.script())
                    && !call.params().containsKey("rotation")
                    && call.params().get("file") instanceof String file
                    && call.params().get("x") instanceof Integer x
                    && call.params().get("y") instanceof Integer y
                    && call.params().get("z") instanceof Integer z -> {
                int done = 0;
                int total = 0;
                for (Schematic.Entry entry : SchematicResources.load(file).blocks()) {
                    if (entry.optional() || SchematicBlocks.isSecondaryPart(entry.block(), entry.state())) {
                        continue;
                    }
                    BlockState state =
                            level.getBlockState(new BlockPos(x + entry.dx(), y + entry.dy(), z + entry.dz()));
                    total++;
                    if (placed(state, bare(WoodSpecies.swap(entry.block(), wood)))) {
                        done++;
                    }
                }
                yield new Placed(done, total);
            }
            default -> new Placed(0, 0);
        };
    }

    private static boolean placed(BlockState state, String block) {
        String actual = bare(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
        return actual.equals(block)
                || WoodSpecies.sameRole(actual, block)
                || ("torch".equals(block) && "wall_torch".equals(actual));
    }

    private static Set<BoardCase> journalSteps(BoardService service, boolean blocked) {
        Set<BoardCase> steps = EnumSet.noneOf(BoardCase.class);
        for (BoardCase step : FirstStepsPlan.chain()) {
            String id = FIRST_PREFIX + step.name().toLowerCase(Locale.ROOT);
            boolean match = blocked
                    ? service.board().statusOf(id).orElse(null) == BoardStatus.BLOCKED
                    : service.board().ledger().has(step, 1) && partsDone(service, step);
            if (match) {
                steps.add(step);
            }
        }
        return steps;
    }

    private static boolean partsDone(BoardService service, BoardCase step) {
        if (step == BoardCase.CLEAR_GROUNDS) {
            for (int part = 2; part <= GroundsPlan.CLEAR_PARTS; part++) {
                if (active(service, partId(step, part))) {
                    return false;
                }
            }
            return true;
        }
        if (step != BoardCase.BUILD_FENCE && step != BoardCase.BUILD_HOUSE) {
            return true;
        }
        boolean anyPart = false;
        boolean allDone = true;
        for (int part = 1; part <= partsOf(step); part++) {
            Optional<BoardStatus> status = service.board().statusOf(partId(step, part));
            anyPart |= part > 1 && status.isPresent();
            allDone &= status.orElse(null) == BoardStatus.DONE;
        }
        return allDone || (!anyPart && service.board().statusOf(partId(step, 1)).orElse(null) == BoardStatus.DONE);
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
