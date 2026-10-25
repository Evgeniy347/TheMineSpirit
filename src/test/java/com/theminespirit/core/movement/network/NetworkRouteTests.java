package com.theminespirit.core.movement.network;

import static com.theminespirit.core.movement.network.GridWorld.cell;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.building.BuildBox;
import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class NetworkRouteTests {
    private static final BuildBox HOUSE = BuildBox.of(-1, 0, -1, 20, 8, 5);
    private static final BlockCoord DOOR = cell(0, 1, 2);

    private static PathNetwork network(GridWorld world) {
        PathNetwork network = new PathNetwork();
        NetworkBuilder.chains(List.of(DOOR), HOUSE, world).forEach(network::add);
        return network;
    }

    @Test
    void plan_FarCornerUpstairsToDoor_RoutesOverNetworkInShortLegs() {
        GridWorld world = GridWorld.twoFloors();
        PathNetwork network = network(world);
        Optional<List<BlockCoord>> route = NetworkRoute.plan(network, cell(19, 5, 0), cell(1, 1, 2), world);
        assertTrue(route.isPresent(), "из дальнего угла второго этажа к двери есть маршрут по сети");
        List<BlockCoord> points = route.get();
        assertEquals(1, points.get(points.size() - 1).y(), "маршрут кончается на первом этаже у двери: " + points);
        assertTrue(points.size() >= 3, "путь со второго этажа к двери длиннее отрезка — идёт через узлы: " + points);
    }

    @Test
    void plan_TargetIsChestBlock_ExitsBesideIt() {
        GridWorld world = GridWorld.twoFloors().solid(2, 1, 2);
        PathNetwork network = network(world);
        Optional<List<BlockCoord>> route = NetworkRoute.plan(network, cell(19, 5, 4), cell(2, 1, 2), world);
        assertTrue(route.isPresent(), "цель — сундук (твёрдый блок): выход сети — клетка рядом, где можно встать");
    }

    @Test
    void repair_WallAcrossCorridorWithDetour_FindsNewPath() {
        GridWorld world = new GridWorld().slab(0, 0, 0, 8, 2);
        List<BlockCoord> straight = new ArrayList<>();
        for (int x = 0; x <= 8; x++) {
            straight.add(cell(x, 1, 1));
        }
        PathSegment segment = new PathSegment(0, straight);
        world.solid(4, 1, 1).solid(4, 2, 1);
        Optional<List<BlockCoord>> repaired = NetworkRoute.repair(segment, world);
        assertTrue(repaired.isPresent(), "столб поперёк коридора шириной 3 обходится: отрезок пересчитан, не удалён");
        assertTrue(!repaired.get().contains(cell(4, 1, 1)), "новый путь не идёт сквозь столб: " + repaired.get());
    }

    @Test
    void repair_CorridorWalledOff_IsGone() {
        GridWorld world = new GridWorld().slab(0, 0, 0, 8, 0);
        List<BlockCoord> straight = new ArrayList<>();
        for (int x = 0; x <= 8; x++) {
            straight.add(cell(x, 1, 0));
        }
        world.solid(4, 1, 0).solid(4, 2, 0).solid(4, 3, 0);
        assertEquals(
                Optional.empty(),
                NetworkRoute.repair(new PathSegment(0, straight), world),
                "проход заложен — отрезок не пересчитать, его убирают из сети");
    }

    @Test
    void repair_SegmentInHugeHall_ReadsAsInSmallRoom() {
        GridWorld room = new GridWorld().slab(0, 0, -5, 8, 7);
        GridWorld hall = new GridWorld().slab(0, 0, -200, 8, 200);
        List<BlockCoord> straight = new ArrayList<>();
        for (int x = 0; x <= 8; x++) {
            straight.add(cell(x, 1, 1));
        }
        PathSegment segment = new PathSegment(0, straight);
        room.solid(4, 1, 1).solid(4, 2, 1);
        hall.solid(4, 1, 1).solid(4, 2, 1);
        NetworkRoute.repair(segment, room);
        NetworkRoute.repair(segment, hall);
        assertEquals(
                room.reads(),
                hall.reads(),
                "пересчёт отрезка в зале 9×401 читает столько же клеток, сколько в комнате 9×13: обход ограничен "
                        + "габаритом отрезка с запасом, иначе изменение блока стоит обхода здания");
    }
}
