package com.theminespirit.core.movement.network;

import static com.theminespirit.core.movement.network.GridWorld.cell;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PathNetworkTests {
    private static List<BlockCoord> line(int x1, int x2, int y, int z) {
        List<BlockCoord> cells = new ArrayList<>();
        int step = x2 >= x1 ? 1 : -1;
        for (int x = x1; x != x2 + step; x += step) {
            cells.add(cell(x, y, z));
        }
        return cells;
    }

    private static List<BlockCoord> column(int x, int z1, int z2, int y) {
        List<BlockCoord> cells = new ArrayList<>();
        for (int z = z1; z <= z2; z++) {
            cells.add(cell(x, y, z));
        }
        return cells;
    }

    @Test
    void route_ThreeSegmentsInARow_PassesThroughTheirEnds() {
        PathNetwork network = new PathNetwork();
        network.add(line(0, 10, 1, 0));
        network.add(line(10, 20, 1, 0));
        network.add(line(20, 30, 1, 0));
        Optional<List<BlockCoord>> route = network.route(cell(5, 1, 0), cell(25, 1, 0));
        assertEquals(
                Optional.of(List.of(cell(5, 1, 0), cell(10, 1, 0), cell(20, 1, 0), cell(25, 1, 0))),
                route,
                "маршрут по сети — вход, концы пройденных отрезков, выход: между соседними точками не больше отрезка, "
                        + "и каждый шаг духа укладывается в путь игры 32");
    }

    @Test
    void route_EntryAndExitOnOneSegment_GoesDirect() {
        PathNetwork network = new PathNetwork();
        network.add(line(0, 12, 1, 0));
        assertEquals(
                Optional.of(List.of(cell(3, 1, 0), cell(11, 1, 0))),
                network.route(cell(3, 1, 0), cell(11, 1, 0)),
                "вход и выход на одном отрезке — сразу к выходу, без захода на концы");
    }

    @Test
    void route_ShortcutDirty_TakesTheLongWayAround() {
        PathNetwork network = new PathNetwork();
        int shortcut = network.add(line(0, 10, 1, 0));
        network.add(column(0, 0, 10, 1));
        network.add(line(0, 10, 1, 10));
        network.add(column(10, 0, 10, 1));
        network.blockChanged(cell(5, 0, 0));
        assertEquals(List.of(shortcut), network.dirty(), "пол под отрезком изменился — к пересчёту только он");
        Optional<List<BlockCoord>> route = network.route(cell(0, 1, 0), cell(10, 1, 0));
        assertEquals(
                Optional.of(List.of(cell(0, 1, 0), cell(0, 1, 10), cell(10, 1, 10), cell(10, 1, 0))),
                route,
                "задетый, ещё не пересчитанный отрезок маршрут обходит: блок мог перекрыть проход");
    }

    @Test
    void replace_RepairedSegment_IsUsableAgain() {
        PathNetwork network = new PathNetwork();
        int id = network.add(line(0, 10, 1, 0));
        network.blockChanged(cell(5, 1, 0));
        network.replace(id, line(0, 10, 1, 0));
        assertTrue(network.dirty().isEmpty(), "пересчитанный отрезок снят с пересчёта: " + network.dirty());
        assertTrue(network.route(cell(0, 1, 0), cell(10, 1, 0)).isPresent(), "пересчитанный отрезок снова в маршрутах");
    }

    @Test
    void blockChanged_BlockAwayFromNetwork_TouchesNothing() {
        PathNetwork network = new PathNetwork();
        network.add(line(0, 10, 1, 0));
        assertEquals(
                Set.of(),
                network.blockChanged(cell(5, 4, 0)),
                "блок выше головы над отрезком на ходьбу не влияет — отрезок не трогается");
    }

    @Test
    void blockChanged_OneBlockInHugeNetwork_CostsAsInSmallOne() {
        assertEquals(
                operationsForOneChange(10),
                operationsForOneChange(2_000),
                "изменение одного блока в сети из 2 000 отрезков стоит столько же операций, сколько в сети из 10: "
                        + "событие блока идёт в тике, обход всей сети на каждое — лаг поселения");
    }

    private static long operationsForOneChange(int segments) {
        PathNetwork network = new PathNetwork();
        for (int i = 0; i < segments; i++) {
            network.add(line(0, 12, 1, i * 3));
        }
        long before = network.operations();
        Set<Integer> touched = network.blockChanged(cell(7, 0, 0));
        assertEquals(1, touched.size(), "пол под одним отрезком задевает ровно его: " + touched);
        return network.operations() - before;
    }
}
