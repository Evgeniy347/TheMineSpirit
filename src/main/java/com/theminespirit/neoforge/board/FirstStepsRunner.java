package com.theminespirit.neoforge.board;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardRules;
import com.theminespirit.core.board.BoardStatus;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.CaseMaterials;
import com.theminespirit.core.board.FirstStepsPlan;
import com.theminespirit.core.board.GatherBatch;
import com.theminespirit.core.board.MaterialSource;
import com.theminespirit.core.board.ResourceGate;
import com.theminespirit.core.board.SettlementFacts;
import com.theminespirit.core.board.StockNeed;
import com.theminespirit.core.board.StockView;
import com.theminespirit.core.board.TicketPlans;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.board.ToolSet;
import com.theminespirit.core.building.PresetLibrary;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.core.planner.Ingredient;
import com.theminespirit.neoforge.SchematicResources;
import com.theminespirit.neoforge.SpiritConfig;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.crafting.RecipeKnowledge;
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
import org.slf4j.Logger;

/**
 * Генератор первых шагов: по фактам поселения ставит очередное задание из цепочки.
 * Шаг с материалами из рецепта или схемы сначала требует запас: не хватает — на доску
 * встают мелкие задачи добычи, а сам шаг ждёт их выполнения и открывается после.
 */
public final class FirstStepsRunner {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int SCAN_RADIUS = 16;
    private static final String GATHER_PREFIX = "gather:";

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
        boolean groundsDone = service.board().ledger().has(BoardCase.CLEAR_GROUNDS, 1);
        SettlementFacts facts = buildFacts(level, home, stock, groundsDone);
        Optional<BoardCase> next = FirstStepsPlan.next(facts);
        if (next.isEmpty()) {
            return;
        }
        BoardCase caseValue = next.orElseThrow();
        if (hasActive(service, caseValue)) {
            return;
        }
        TicketTarget target = targetFor(caseValue, home, spirits.size());
        MaterialSource source = materialSource(level);
        Map<String, Integer> need = materials(source, caseValue, target);
        Map<String, Integer> missing = ResourceGate.missing(new StockNeed(need), stock);
        if (!missing.isEmpty()) {
            LOGGER.info("Кейс первых шагов {} ждёт: не хватает {}", caseValue, missing);
            for (Map.Entry<String, Integer> entry : missing.entrySet()) {
                postGather(service, source, home, entry.getKey(), entry.getValue(), now);
            }
            return;
        }
        String id = "first:" + caseValue.name().toLowerCase(Locale.ROOT);
        LOGGER.info("Кейс первых шагов {} открыт: материалы на месте", caseValue);
        service.submit(
                new BoardTicket(
                        id,
                        caseValue,
                        Optional.empty(),
                        BoardRules.priorityOf(caseValue),
                        now,
                        1,
                        BoardTicket.ALWAYS_CAUSE,
                        List.of(),
                        Map.of()),
                target);
    }

    private static TicketTarget targetFor(BoardCase caseValue, BlockCoord home, int spirits) {
        TicketTarget target = TicketTarget.at(home);
        return ToolSet.isTool(caseValue) ? target.withToolCount(ToolSet.count(spirits)) : target;
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

    private static Map<String, Integer> materials(MaterialSource source, BoardCase caseValue, TicketTarget target) {
        try {
            TaskNode plan = TicketPlans.plan(caseValue, 1, target);
            return CaseMaterials.need(plan, source);
        } catch (RuntimeException failure) {
            LOGGER.error("Материалы шага первых шагов {} не посчитаны: {}", caseValue, failure.getMessage());
            return Map.of();
        }
    }

    private static MaterialSource materialSource(ServerLevel level) {
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
                return PresetLibrary.countMaterials(SchematicResources.load(file));
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

    private static SettlementFacts buildFacts(
            ServerLevel level, BlockCoord home, StockView stock, boolean groundsDone) {
        boolean hasHome = true;
        boolean groundsCleared = groundsDone;
        int chests = 0;
        boolean craftingStation = false;
        boolean mineEntrance = false;
        boolean fence = false;
        boolean lighting = false;
        boolean houses = false;
        boolean smithingTable = false;
        boolean anvil = false;
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
        return new SettlementFacts(
                hasHome,
                groundsCleared,
                chests,
                craftingStation,
                tools,
                mineEntrance,
                fence,
                lighting,
                houses,
                smithingTable,
                anvil);
    }

    private static Set<String> toolStock(StockView stock) {
        Set<String> tools = new HashSet<>();
        for (BoardCase toolCase : ToolSet.chain()) {
            String key = ToolSet.stockKey(toolCase);
            if (stock.available().containsKey(key)) {
                tools.add(key);
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
