package com.theminespirit.neoforge.board;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardOutcome;
import com.theminespirit.core.board.BoardRules;
import com.theminespirit.core.board.BoardStatus;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.CaseMaterials;
import com.theminespirit.core.board.FirstStepsPlan;
import com.theminespirit.core.board.GatherBatch;
import com.theminespirit.core.board.MaterialSource;
import com.theminespirit.core.board.PlotLeveling;
import com.theminespirit.core.board.ResourceGate;
import com.theminespirit.core.board.SettlementFacts;
import com.theminespirit.core.board.SettlementLayout;
import com.theminespirit.core.board.StockNeed;
import com.theminespirit.core.board.StockView;
import com.theminespirit.core.board.TicketPlans;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.board.ToolSet;
import com.theminespirit.core.board.TorchRules;
import com.theminespirit.core.building.BuildingType;
import com.theminespirit.core.building.PresetLibrary;
import com.theminespirit.core.building.WoodSpecies;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.core.planner.Ingredient;
import com.theminespirit.core.reload.DataTables;
import com.theminespirit.neoforge.SchematicResources;
import com.theminespirit.neoforge.SpiritConfig;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.crafting.RecipeKnowledge;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import org.slf4j.Logger;

/**
 * Генератор первых шагов: по фактам поселения ставит очередное задание из цепочки.
 * Шаг с материалами из рецепта или схемы сначала требует запас: не хватает — на доску
 * встают мелкие задачи добычи, а сам шаг ждёт их выполнения и открывается после.
 */
public final class FirstStepsRunner {
    private static final Set<BoardCase> WOODEN_BUILDINGS = EnumSet.of(BoardCase.BUILD_FENCE, BoardCase.BUILD_HOUSE);
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int SCAN_RADIUS = 16;
    private static final String GATHER_PREFIX = "gather:";
    private static final String FIRST_PREFIX = "first:";
    private static final String COBBLESTONE = "cobblestone";
    private static final String FURNACE_ITEM = "furnace";
    private static final int FEET_SCAN_DEPTH = 48;

    private FirstStepsRunner() {}

    public static void tick(
            ServerLevel level, BoardService service, List<SpiritEntity> spirits, StockView stock, long now) {
        if (!SpiritConfig.boardEnabled() || spirits.isEmpty()) {
            return;
        }
        // Поселение задаёт дом: без него homeOf подставил бы (0,64,0) и доска выдала бы first:*
        // духу без дома (сцена REST получала чужое задание до своего POST).
        if (spirits.stream().noneMatch(spirit -> spirit.home().isPresent())) {
            return;
        }
        BlockCoord home = homeOf(spirits);
        Scan scan = buildFacts(level, home, stock, service);
        SettlementFacts facts = scan.facts();
        boolean mine = facts.mineEntrance() || facts.done().contains(BoardCase.BUILD_MINE);
        Optional<BoardCase> next = FirstStepsPlan.next(facts);
        if (next.isEmpty() || hasActive(service, next.orElseThrow())) {
            TorchWatch.tick(service, spirits, home, stock, new TorchWatch.Site(scan.furnaceBlock(), mine, 0), now);
            return;
        }
        BoardCase caseValue = next.orElseThrow();
        String wood = woodOf(level, home, stock);
        // Якорь участка — дом на средней высоте участка: от неё выравнивание и все постройки раскладки.
        BlockCoord anchor = new BlockCoord(
                home.x(),
                PlotLeveling.level(home, SettlementLayout.HALF, (x, z) -> feetAt(level, x, z, home.y())),
                home.z());
        TicketTarget target = targetFor(caseValue, anchor, wood);
        if (caseValue == BoardCase.CLEAR_GROUNDS) {
            target = target.withLeveling(leveling(level, anchor, stock));
        }
        MaterialSource source = materialSource(level);
        Map<String, Integer> need = new HashMap<>();
        // Доски, брёвна и деревянные блоки — породы поселения: в джунглях «oak_planks» не добыть (INC-36).
        materials(source, caseValue, target)
                .forEach((item, count) -> need.merge(WoodSpecies.swap(item, wood), count, Integer::sum));
        Map<String, Integer> missing = ResourceGate.missing(new StockNeed(need), stock);
        // Факелы шага (ограда — 17 на столбах) — сверх обычного запаса факелов: иначе шаг ждал бы их вечно.
        TorchWatch.Site site = new TorchWatch.Site(scan.furnaceBlock(), mine, need.getOrDefault(TorchRules.TORCH, 0));
        TorchWatch.tick(service, spirits, home, stock, site, now);
        if (!missing.isEmpty()) {
            LOGGER.info("Кейс первых шагов {} ждёт: не хватает {}", caseValue, missing);
            for (Map.Entry<String, Integer> entry : missing.entrySet()) {
                if (gatherable(entry.getKey(), facts)) {
                    postGather(service, source, home, entry.getKey(), entry.getValue(), now);
                }
            }
            return;
        }
        String id = FIRST_PREFIX + caseValue.name().toLowerCase(Locale.ROOT);
        BoardOutcome outcome = service.submit(
                new BoardTicket(
                        id,
                        caseValue,
                        Optional.empty(),
                        BoardRules.priorityOf(caseValue),
                        now,
                        1,
                        BoardTicket.ALWAYS_CAUSE,
                        List.of(),
                        // Материалы шага — бронь доски: фоновые занятия их не трогают (BoardReserve).
                        Map.copyOf(need)),
                target);
        if (outcome == BoardOutcome.ACCEPTED) {
            // Только реально поставленный шаг: BLOCKED/FAILED-тикет доска не переоткрывает, и строка «открыт»
            // каждые 5 с вводила в заблуждение (INC-26).
            LOGGER.info("Кейс первых шагов {} открыт: материалы на месте", caseValue);
        }
    }

    // Выравнивание участка (решение 16): срезка и подсыпка по высотам колонок; яма — булыжником, если его хватает в
    // запасе, иначе землёй.
    private static PlotLeveling leveling(ServerLevel level, BlockCoord anchor, StockView stock) {
        PlotLeveling plan = PlotLeveling.plan(anchor, SettlementLayout.HALF, (x, z) -> feetAt(level, x, z, anchor.y()));
        if (plan.fillVolume() > 0 && stock.available().getOrDefault(COBBLESTONE, 0) >= plan.fillVolume()) {
            plan = plan.withBlock(COBBLESTONE);
        }
        if (!plan.cut().isEmpty() || !plan.fill().isEmpty()) {
            LOGGER.info(
                    "Выравнивание участка на y={}: срезка {} ящиков, подсыпка {} блоков {}",
                    anchor.y(),
                    plan.cut().size(),
                    plan.fillVolume(),
                    plan.block());
        }
        return plan;
    }

    // Уровень ног колонки — над верхним блоком земли; постройки, станции, деревья и растения землёй не считаются.
    private static int feetAt(ServerLevel level, int x, int z, int fallback) {
        BlockPos.MutableBlockPos pos =
                new BlockPos.MutableBlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
        for (int step = 0; step < FEET_SCAN_DEPTH; step++) {
            pos.move(0, -1, 0);
            if (!level.isLoaded(pos)) {
                return fallback;
            }
            if (isGround(level.getBlockState(pos))) {
                return pos.getY() + 1;
            }
        }
        return fallback;
    }

    private static boolean isGround(BlockState state) {
        return state.is(BlockTags.DIRT)
                || state.is(BlockTags.BASE_STONE_OVERWORLD)
                || state.is(BlockTags.SAND)
                || state.is(Blocks.GRAVEL)
                || state.is(Blocks.COBBLESTONE)
                || state.is(Blocks.CLAY)
                || !state.getFluidState().isEmpty();
    }

    private static String woodOf(ServerLevel level, BlockCoord home, StockView stock) {
        return SettlementWood.of(level, home, stock.available());
    }

    private static TicketTarget targetFor(BoardCase caseValue, BlockCoord home, String wood) {
        TicketTarget target = TicketTarget.at(home);
        if (WOODEN_BUILDINGS.contains(caseValue) && !WoodSpecies.OAK.equals(wood)) {
            target = target.withWood(wood);
        }
        // По одному инструменту за задание: N штук одному крафтеру не собрать из запаса, разнесённого по духам;
        // остальные до набора на духа ставит постоянный запас (ToolStockWatch).
        return ToolSet.isTool(caseValue) ? target.withToolCount(1) : target;
    }

    private static void postGather(
            BoardService service, MaterialSource source, BlockCoord home, String item, int shortage, long now) {
        String id = GATHER_PREFIX + item;
        BoardStatus status = service.board().statusOf(id).orElse(null);
        if (status == BoardStatus.PENDING
                || status == BoardStatus.WAITING_RESOURCES
                || status == BoardStatus.ASSIGNED) {
            return;
        }
        int batch = GatherBatch.round(shortage, source.outputCount(item));
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
                // Шахта копается в породе: её пол — добытый ею же булыжник, на складе его до шахты нет и быть не
                // должно.
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

    // Факелы и уголь ставит TorchWatch; булыжник добывается в шахте — до её спуска добыча камня не ставится.
    private static boolean gatherable(String item, SettlementFacts facts) {
        if (TorchRules.handles(item)) {
            return false;
        }
        return !COBBLESTONE.equals(item) || facts.mineEntrance() || facts.done().contains(BoardCase.BUILD_MINE);
    }

    private record Scan(SettlementFacts facts, boolean furnaceBlock) {}

    private static Scan buildFacts(ServerLevel level, BlockCoord home, StockView stock, BoardService service) {
        boolean groundsDone = service.board().ledger().has(BoardCase.CLEAR_GROUNDS, 1);
        boolean hasHome = true;
        boolean groundsCleared = true;
        int chests = 0;
        boolean craftingStation = false;
        boolean mineEntrance = false;
        boolean fence = false;
        boolean lighting = false;
        boolean houses = false;
        boolean smithingTable = false;
        boolean anvil = false;
        boolean furnace = false;
        BlockPos start = new BlockPos(home.x(), home.y(), home.z());
        for (int dx = -SCAN_RADIUS; dx <= SCAN_RADIUS; dx++) {
            for (int dz = -SCAN_RADIUS; dz <= SCAN_RADIUS; dz++) {
                if (dx * dx + dz * dz > SCAN_RADIUS * SCAN_RADIUS) {
                    continue;
                }
                BlockPos pos = start.offset(dx, 0, dz);
                for (int dy = -2; dy <= 2; dy++) {
                    BlockPos check = pos.offset(0, dy, 0);
                    if (!level.isLoaded(check)) {
                        continue;
                    }
                    var state = level.getBlockState(check);
                    if (!groundsDone && (state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES))) {
                        groundsCleared = false;
                    }
                    if (state.is(Blocks.LADDER)) {
                        mineEntrance = true;
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
                    if (state.is(Blocks.TORCH) || state.is(Blocks.WALL_TORCH)) {
                        lighting = true;
                    }
                    if (FenceWatch.isFence(state)) {
                        fence = true;
                    }
                    if (state.getBlock() instanceof StairBlock || state.getBlock() instanceof SlabBlock) {
                        houses = true;
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
        Set<String> tools = toolStock(stock);
        Set<BoardCase> done = journalSteps(service, false);
        Set<BoardCase> blocked = journalSteps(service, true);
        boolean furnaceAvailable = furnace || stock.available().getOrDefault(FURNACE_ITEM, 0) > 0;
        SettlementFacts facts = new SettlementFacts(
                hasHome,
                groundsCleared,
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

    private static Set<BoardCase> journalSteps(BoardService service, boolean blocked) {
        Set<BoardCase> steps = EnumSet.noneOf(BoardCase.class);
        for (BoardCase step : FirstStepsPlan.chain()) {
            String id = FIRST_PREFIX + step.name().toLowerCase(Locale.ROOT);
            boolean match = blocked
                    ? service.board().statusOf(id).orElse(null) == BoardStatus.BLOCKED
                    : service.board().ledger().has(step, 1);
            if (match) {
                steps.add(step);
            }
        }
        return steps;
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

    private static BlockCoord homeOf(List<SpiritEntity> spirits) {
        for (SpiritEntity spirit : spirits) {
            Optional<BlockCoord> home = spirit.home();
            if (home.isPresent()) {
                return home.orElseThrow();
            }
        }
        return new BlockCoord(0, 64, 0);
    }
}
