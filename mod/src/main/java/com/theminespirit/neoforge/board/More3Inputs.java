package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardLogEntry;
import com.theminespirit.core.board.BoardStatus;
import com.theminespirit.core.board.BoardTiming;
import com.theminespirit.core.board.graph.GraphFacts;
import com.theminespirit.core.board.graph.GraphState;
import com.theminespirit.core.board.graph.More3Rules;
import com.theminespirit.core.board.graph.SupplyGraph;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.town.SiteLevels;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

final class More3Inputs {
    private static final int DROP_RADIUS = 8;
    private static final int DECAY_RADIUS = 16;
    private static final int DECAY_BELOW = 4;
    private static final int DECAY_ABOVE = 8;
    private static final int TRAMPLED_NEIGHBOURS = 2;
    private static final int JOURNAL_LOOKBACK = 512;
    private static final String STATION = "station:";
    private static final String GRAPH = "graph:";
    private static final char AT = '@';

    private More3Inputs() {}

    static GraphState apply(
            GraphState state,
            ServerLevel level,
            BoardService service,
            List<SpiritEntity> spirits,
            SupplyGraph graph,
            GraphSurroundings.Around around,
            FirstStepsRunner.StageScan scan) {
        long now = service.board().now();
        More3Store store = More3Store.get(level);
        store.noteStage(state.stage(), now);
        store.migrateColumns(SiteLevels.fenceY(scan.stage()));
        More3Rules.Signals signals = signals(level, service, state, graph, around, scan, store);
        return events(state, More3Rules.events(signals));
    }

    static GraphState events(GraphState state, Set<String> more) {
        Set<String> events = new LinkedHashSet<>(state.events());
        events.addAll(more);
        return new GraphState(
                state.stock(),
                state.stage(),
                state.kits(),
                state.buildings(),
                state.materials(),
                state.storageFreePercent(),
                Set.copyOf(events),
                state.world(),
                state.distances(),
                state.stations(),
                state.orders(),
                state.facts());
    }

    private static More3Rules.Signals signals(
            ServerLevel level,
            BoardService service,
            GraphState state,
            SupplyGraph graph,
            GraphSurroundings.Around around,
            FirstStepsRunner.StageScan scan,
            More3Store store) {
        long now = service.board().now();
        GraphFacts facts = state.facts();
        Decay decay = decay(level, scan.home());
        return new More3Rules.Signals(
                dangerSpots(store, facts, around, now),
                deathDrops(level, store, now),
                slowWorks(service, graph),
                More3Rules.trash(graph, carried(facts)),
                More3Rules.unlabeledChests(facts.chests()),
                More3Rules.workConflicts(facts.spirits().values()),
                More3Rules.idleStations(facts.stations(), busyStations(service)),
                level.players().stream().anyMatch(ServerPlayer::isSleeping),
                decay.sand(),
                decay.gravel(),
                decay.trampled(),
                around.events().contains(GraphSurroundings.LEAF_DECAY),
                store.stageStartedAt() > 0 && More3Rules.stageSlow(now - store.stageStartedAt(), state.stage()));
    }

    private static int dangerSpots(More3Store store, GraphFacts facts, GraphSurroundings.Around around, long now) {
        int known = store.recentDangers(now).size();
        int lava = around.resource("lava").isPresent() ? 1 : 0;
        int cliff = facts.terrain().heightDrop() >= 4 ? 1 : 0;
        return known + lava + cliff;
    }

    private static int deathDrops(ServerLevel level, More3Store store, long now) {
        int alive = 0;
        for (More3Store.DeathDrop drop : store.activeDrops(now)) {
            if (itemsAt(level, drop.position())) {
                alive++;
            } else {
                store.forgetDrop(drop);
            }
        }
        return alive;
    }

    private static boolean itemsAt(ServerLevel level, BlockCoord at) {
        AABB box = new AABB(new BlockPos(at.x(), at.y(), at.z())).inflate(DROP_RADIUS);
        return level.getEntitiesOfClass(ItemEntity.class, box).stream()
                .anyMatch(item -> !item.isRemoved() && !item.getItem().isEmpty());
    }

    private static int slowWorks(BoardService service, SupplyGraph graph) {
        List<BoardLogEntry> entries = service.journal().last(JOURNAL_LOOKBACK);
        Map<String, BoardTiming> timings = BoardTiming.fromJournal(entries);
        Map<String, BoardCase> cases = new HashMap<>();
        for (BoardLogEntry entry : entries) {
            cases.put(entry.ticketId(), entry.boardCase());
        }
        int slow = 0;
        for (Map.Entry<String, BoardTiming> entry : timings.entrySet()) {
            BoardTiming timing = entry.getValue();
            BoardCase boardCase = cases.get(entry.getKey());
            if (boardCase == null || !timing.started() || !timing.finished()) {
                continue;
            }
            String way = way(entry.getKey());
            if (graph.way(way).isEmpty()) {
                continue;
            }
            int expected = graph.costs().ticks(way, boardCase);
            if (More3Rules.workSlow(timing.durationTicks(0), expected)) {
                slow++;
            }
        }
        return slow;
    }

    private static String way(String ticketId) {
        String id = ticketId.startsWith(GRAPH) ? ticketId.substring(GRAPH.length()) : ticketId;
        int at = id.indexOf(AT);
        return at < 0 ? id : id.substring(0, at);
    }

    private static Map<String, Integer> carried(GraphFacts facts) {
        Map<String, Integer> items = new LinkedHashMap<>();
        facts.spirits()
                .values()
                .forEach(spirit -> spirit.inventory().forEach((item, count) -> items.merge(item, count, Integer::sum)));
        facts.chests()
                .forEach(chest -> chest.contents().forEach((item, count) -> items.merge(item, count, Integer::sum)));
        return items;
    }

    private static Set<String> busyStations(BoardService service) {
        Set<String> busy = new HashSet<>();
        for (var ticket : service.board().tickets()) {
            if (!open(service.board().statusOf(ticket.id()).orElse(BoardStatus.DONE))) {
                continue;
            }
            int at = ticket.id().indexOf(STATION);
            if (at >= 0) {
                busy.add(ticket.id().substring(at + STATION.length()));
            }
        }
        return busy;
    }

    private static boolean open(BoardStatus status) {
        return status == BoardStatus.PENDING
                || status == BoardStatus.WAITING_RESOURCES
                || status == BoardStatus.ASSIGNED;
    }

    private static Decay decay(ServerLevel level, BlockCoord home) {
        int sand = 0;
        int gravel = 0;
        int trampled = 0;
        for (int dx = -DECAY_RADIUS; dx <= DECAY_RADIUS; dx++) {
            for (int dz = -DECAY_RADIUS; dz <= DECAY_RADIUS; dz++) {
                for (int dy = -DECAY_BELOW; dy <= DECAY_ABOVE; dy++) {
                    BlockPos at = new BlockPos(home.x() + dx, home.y() + dy, home.z() + dz);
                    if (!level.isLoaded(at)) {
                        continue;
                    }
                    BlockState state = level.getBlockState(at);
                    if ((state.is(Blocks.SAND) || state.is(Blocks.RED_SAND)) && unsupported(level, at)) {
                        sand++;
                    } else if (state.is(Blocks.GRAVEL) && unsupported(level, at)) {
                        gravel++;
                    } else if (state.is(Blocks.DIRT) && trampled(level, at)) {
                        trampled++;
                    }
                }
            }
        }
        return new Decay(sand, gravel, trampled);
    }

    private static boolean unsupported(ServerLevel level, BlockPos at) {
        return level.getBlockState(at.below()).isAir();
    }

    private static boolean trampled(ServerLevel level, BlockPos at) {
        if (!level.getBlockState(at.above()).isAir()) {
            return false;
        }
        int neighbours = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                if (level.getBlockState(at.offset(dx, 0, dz)).is(Blocks.FARMLAND)) {
                    neighbours++;
                }
            }
        }
        return neighbours >= TRAMPLED_NEIGHBOURS;
    }

    private record Decay(int sand, int gravel, int trampled) {}
}
