package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardState;
import com.theminespirit.core.board.BoardStatus;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.graph.GraphFacts;
import com.theminespirit.core.board.graph.GraphFeedback;
import com.theminespirit.core.board.graph.GraphState;
import com.theminespirit.core.board.graph.GraphState.Built;
import com.theminespirit.core.board.graph.MoreRules;
import com.theminespirit.core.board.graph.SupplyGraph;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.town.ColumnMask;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

final class MoreInputs {
    private static final int DROP_RADIUS = 24;
    private static final int DROP_WARN_TICKS = 1200;
    private static final int DROP_DESPAWN_TICKS = 6000;
    private static final int DARK_LEVEL = 8;
    private static final int SCAN_RADIUS = 16;
    private static final int SCAN_BELOW = 4;
    private static final int SCAN_ABOVE = 16;
    private static final int FAILURE_REPEATS = 2;
    private static final String BOARD_PREFIX = "board-";
    private static final Set<String> GOAL_PREFIXES = Set.of("goal:", "strategy:");
    private static final Set<BoardCase> STATION_CASES =
            Set.of(BoardCase.ACTIVITY_SMELT, BoardCase.SMELT_CHARCOAL, BoardCase.CRAFTING_STATION);

    private MoreInputs() {}

    static GraphState apply(
            GraphState state,
            ServerLevel level,
            BoardService service,
            FirstStepsRunner.StageScan scan,
            SupplyGraph graph) {
        MoreRules.Signals signals = signals(state, level, service, scan, graph);
        Set<String> world = new LinkedHashSet<>(state.world());
        world.addAll(MoreRules.signals(signals));
        return new GraphState(
                state.stock(),
                state.stage(),
                state.kits(),
                state.buildings(),
                state.materials(),
                state.storageFreePercent(),
                state.events(),
                world,
                state.distances(),
                state.stations(),
                state.orders(),
                state.facts());
    }

    private static MoreRules.Signals signals(
            GraphState state,
            ServerLevel level,
            BoardService service,
            FirstStepsRunner.StageScan scan,
            SupplyGraph graph) {
        MoreRules.Player player = player(state.facts().feedback().player());
        Blocks blocks = blocks(level, scan.home());
        return new MoreRules.Signals(
                player,
                darkCells(level, scan.stage().enclosure()),
                clock(state.facts().feedback().clock(), level),
                repeatedFailures(service),
                stationWaiters(service),
                drops(level, scan.home()),
                new MoreRules.Resources(
                        blocks.trees(),
                        blocks.saplings(),
                        saplingStock(state, graph),
                        blocks.ore(),
                        state.built("mine") == Built.BUILT),
                new MoreRules.Security(fenceGaps(service), blocks.openDoors(), blocks.hoppers(), level.isNight()),
                reserves(state.facts()),
                strategicGoals(service));
    }

    private static MoreRules.Player player(GraphFeedback.Player fact) {
        return new MoreRules.Player(fact.near(), fact.holdingBlock(), fact.usingItem(), fact.digging());
    }

    private static int darkCells(ServerLevel level, ColumnMask enclosure) {
        int dark = 0;
        for (ColumnMask.Cell cell : enclosure.cells()) {
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, cell.x(), cell.z());
            BlockPos at = new BlockPos(cell.x(), y, cell.z());
            if (level.isLoaded(at) && level.getMaxLocalRawBrightness(at) < DARK_LEVEL) {
                dark++;
            }
        }
        return dark;
    }

    private static MoreRules.Clock clock(GraphFeedback.Clock fact, ServerLevel level) {
        boolean badWeather = level.isRaining() || level.isThundering();
        return new MoreRules.Clock(fact.duskSoon(), badWeather);
    }

    private static int repeatedFailures(BoardService service) {
        int repeated = 0;
        for (BoardState.Entry entry : service.board().snapshot().entries()) {
            if (entry.failures() >= FAILURE_REPEATS) {
                repeated++;
            }
        }
        return repeated;
    }

    private static int stationWaiters(BoardService service) {
        int waiting = 0;
        for (BoardTicket ticket : service.board().tickets()) {
            if (STATION_CASES.contains(ticket.boardCase()) && open(service, ticket.id())) {
                waiting++;
            }
        }
        return waiting;
    }

    private static int fenceGaps(BoardService service) {
        int gaps = 0;
        for (BoardTicket ticket : service.board().tickets()) {
            if (ticket.boardCase() == BoardCase.REPAIR_FENCE && open(service, ticket.id())) {
                gaps++;
            }
        }
        return gaps;
    }

    private static int strategicGoals(BoardService service) {
        int goals = 0;
        for (BoardTicket ticket : service.board().tickets()) {
            boolean goal = ticket.id().startsWith(BOARD_PREFIX)
                    || GOAL_PREFIXES.stream().anyMatch(ticket.cause()::startsWith);
            if (goal && open(service, ticket.id())) {
                goals++;
            }
        }
        return goals;
    }

    private static boolean open(BoardService service, String ticketId) {
        return service.board()
                .statusOf(ticketId)
                .filter(status -> status == BoardStatus.PENDING
                        || status == BoardStatus.WAITING_RESOURCES
                        || status == BoardStatus.ASSIGNED)
                .isPresent();
    }

    private static MoreRules.Drops drops(ServerLevel level, BlockCoord home) {
        BlockPos center = new BlockPos(home.x(), home.y(), home.z());
        int total = 0;
        int expiring = 0;
        for (ItemEntity drop : level.getEntitiesOfClass(ItemEntity.class, new AABB(center).inflate(DROP_RADIUS))) {
            if (drop.isRemoved()) {
                continue;
            }
            total++;
            if (drop.getAge() >= DROP_DESPAWN_TICKS - DROP_WARN_TICKS) {
                expiring++;
            }
        }
        return new MoreRules.Drops(total, expiring);
    }

    private record Blocks(int trees, int saplings, int openDoors, int hoppers, int ore) {}

    private static Blocks blocks(ServerLevel level, BlockCoord home) {
        int trees = 0;
        int saplings = 0;
        int doors = 0;
        int hoppers = 0;
        int ore = 0;
        for (int dx = -SCAN_RADIUS; dx <= SCAN_RADIUS; dx++) {
            for (int dz = -SCAN_RADIUS; dz <= SCAN_RADIUS; dz++) {
                for (int dy = -SCAN_BELOW; dy <= SCAN_ABOVE; dy++) {
                    BlockPos at = new BlockPos(home.x() + dx, home.y() + dy, home.z() + dz);
                    if (!level.isLoaded(at)) {
                        continue;
                    }
                    BlockState state = level.getBlockState(at);
                    String id = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
                    if (id.endsWith("_log") || "mangrove_roots".equals(id)) {
                        trees++;
                    }
                    if (id.endsWith("_ore") || "ancient_debris".equals(id)) {
                        ore++;
                    }
                    if (state.getBlock() instanceof SaplingBlock) {
                        saplings++;
                    }
                    if (state.getBlock() instanceof DoorBlock && state.getValue(DoorBlock.OPEN)) {
                        doors++;
                    }
                    if (state.getBlock() instanceof HopperBlock) {
                        hoppers++;
                    }
                }
            }
        }
        return new Blocks(trees, saplings, doors, hoppers, ore);
    }

    private static int saplingStock(GraphState state, SupplyGraph graph) {
        if (!graph.isGroup("#saplings")) {
            return 0;
        }
        return graph.members("#saplings").stream()
                .mapToInt(member -> state.count(member))
                .sum();
    }

    private static MoreRules.Reserves reserves(GraphFacts facts) {
        int unprotected = 0;
        int spareArmor = 0;
        Map<String, Integer> activities = new HashMap<>();
        for (GraphFacts.Spirit spirit : facts.spirits().values()) {
            if (spirit.armor().isEmpty()) {
                unprotected++;
            }
            for (Map.Entry<String, Integer> item : spirit.inventory().entrySet()) {
                if (armor(item.getKey())) {
                    spareArmor += item.getValue();
                }
            }
            activity(activities, spirit.activity());
        }
        int duplicate = activities.values().stream()
                .mapToInt(Integer::intValue)
                .filter(count -> count > 1)
                .sum();
        return new MoreRules.Reserves(unprotected, spareArmor, duplicate);
    }

    private static void activity(Map<String, Integer> activities, @Nullable String activity) {
        if (activity != null && !activity.isBlank()) {
            activities.merge(activity, 1, Integer::sum);
        }
    }

    private static boolean armor(String item) {
        return item.endsWith("_helmet")
                || item.endsWith("_chestplate")
                || item.endsWith("_leggings")
                || item.endsWith("_boots")
                || item.endsWith("shield");
    }
}
