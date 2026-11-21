package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.building.SpiritReach;
import com.theminespirit.core.building.api.BuildBox;
import com.theminespirit.core.mining.api.Cell;
import com.theminespirit.core.mining.api.MiningSafety;
import com.theminespirit.core.mining.api.SafetyView;
import com.theminespirit.core.movement.api.BodyStep;
import com.theminespirit.core.movement.network.NetworkBuilder;
import com.theminespirit.core.movement.network.NetworkRoute;
import com.theminespirit.core.movement.network.PathNetwork;
import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class WarehouseStandsTests {
    private static final BlockCoord START = new BlockCoord(-20, 1, 0);
    private static final BlockCoord DOOR = new BlockCoord(40, 1, 0);
    private static final BlockCoord CHEST = new BlockCoord(48, 1, 0);
    private static final BuildBox STREET_BOX = BuildBox.of(-25, 1, -8, 53, 3, 8);
    private static final BuildBox ARRIVAL_BOX = BuildBox.of(38, -3, -10, 58, 8, 10);
    private static final int[][] SIDES = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    @Test
    void dispatchWaves_TenSpiritsFromStreetBehindWarehouse_AllStandNearChest() {
        SpiritReach before = SpiritReach.current();
        try {
            SpiritReach.useSource(() -> new SpiritReach(4, 2, 2));
            World world = warehouse();
            PathNetwork network = network(world);
            List<BlockCoord> route =
                    NetworkRoute.plan(network, START, CHEST, world).orElseThrow();
            BlockCoord feet = route.get(route.size() - 1);
            assertRoute(route);

            Set<BlockCoord> taken = new HashSet<>();
            BlockCoord first = pick(world, feet, taken);
            taken.add(first);

            List<BlockCoord> middle = dispatch(world, feet, taken, 5);
            List<BlockCoord> late = dispatch(world, feet, taken, 4);

            assertEquals(
                    10,
                    taken.size(),
                    "десять духов не должны делить стоянки: " + taken
                            + " Иначе тест dispatchWaves_TenSpiritsFromStreetBehindWarehouse_AllStandNearChest перестанет ловить эту поломку. При смене dispatchWaves_TenSpiritsFromStreetBehindWarehouse_AllStandNearChest проверь это утверждение.");
            for (BlockCoord stand : middle) {
                assertTrue(
                        taken.contains(stand),
                        "одновременная отправка пяти духов разводит их по стоянкам: " + stand
                                + " Иначе тест dispatchWaves_TenSpiritsFromStreetBehindWarehouse_AllStandNearChest перестанет ловить эту поломку. При смене dispatchWaves_TenSpiritsFromStreetBehindWarehouse_AllStandNearChest проверь это утверждение.");
            }
            for (BlockCoord stand : late) {
                assertTrue(
                        taken.contains(stand),
                        "четвёрка, отправленная на середине пути, тоже получает свою стоянку"
                                + " Иначе тест dispatchWaves_TenSpiritsFromStreetBehindWarehouse_AllStandNearChest перестанет ловить эту поломку. При смене dispatchWaves_TenSpiritsFromStreetBehindWarehouse_AllStandNearChest проверь это утверждение.");
            }
            for (BlockCoord stand : taken) {
                assertReach(world, network, stand);
            }
        } finally {
            SpiritReach.useSource(() -> before);
        }
    }

    @Test
    void stand_TakenWhileWalking_ShiftsToFreeCellInsteadOfFailing() {
        SpiritReach before = SpiritReach.current();
        try {
            SpiritReach.useSource(() -> new SpiritReach(4, 2, 2));
            World world = warehouse();
            PathNetwork network = network(world);
            List<BlockCoord> route =
                    NetworkRoute.plan(network, START, CHEST, world).orElseThrow();
            BlockCoord feet = route.get(route.size() - 1);
            BlockCoord chosen = pick(world, feet, Set.of());
            Set<BlockCoord> taken = new HashSet<>();
            taken.add(chosen);
            BlockCoord settled = settle(world, feet, chosen, taken);
            assertNotEquals(
                    chosen,
                    settled,
                    "клетку заняли, пока дух шёл: последний участок пересчитан на свободную стоянку"
                            + " Иначе тест stand_TakenWhileWalking_ShiftsToFreeCellInsteadOfFailing перестанет ловить эту поломку. При смене stand_TakenWhileWalking_ShiftsToFreeCellInsteadOfFailing проверь это утверждение.");
            assertTrue(
                    MiningSafety.canStand(settled, world),
                    "новая стоянка проходима: " + settled
                            + " Иначе тест stand_TakenWhileWalking_ShiftsToFreeCellInsteadOfFailing перестанет ловить эту поломку. При смене stand_TakenWhileWalking_ShiftsToFreeCellInsteadOfFailing проверь это утверждение.");
            assertTrue(
                    SpiritReach.reachable(settled, CHEST),
                    "новая стоянка в досягаемости сундука: " + settled
                            + " Иначе тест stand_TakenWhileWalking_ShiftsToFreeCellInsteadOfFailing перестанет ловить эту поломку. При смене stand_TakenWhileWalking_ShiftsToFreeCellInsteadOfFailing проверь это утверждение.");
            assertTrue(
                    NetworkRoute.plan(network, START, settled, world).isPresent(),
                    "до новой стоянки есть маршрут"
                            + " Иначе тест stand_TakenWhileWalking_ShiftsToFreeCellInsteadOfFailing перестанет ловить эту поломку. При смене stand_TakenWhileWalking_ShiftsToFreeCellInsteadOfFailing проверь это утверждение.");
        } finally {
            SpiritReach.useSource(() -> before);
        }
    }

    private static List<BlockCoord> dispatch(World world, BlockCoord feet, Set<BlockCoord> taken, int count) {
        List<BlockCoord> stands = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            BlockCoord stand = pick(world, feet, taken);
            taken.add(stand);
            stands.add(stand);
        }
        return stands;
    }

    private static BlockCoord pick(World world, BlockCoord feet, Set<BlockCoord> taken) {
        Set<BlockCoord> walkable = walkableFrom(world, feet, ARRIVAL_BOX);
        return WorkStand.near(CHEST, world.ground(), walkable::contains, taken)
                .orElseThrow(() -> new AssertionError("у сундука нет свободной стоянки: " + taken));
    }

    private static BlockCoord settle(World world, BlockCoord feet, BlockCoord walkedTo, Set<BlockCoord> taken) {
        BlockCoord stand = walkedTo;
        for (int shift = 0; shift <= 3 && taken.contains(stand); shift++) {
            stand = pick(world, feet, taken);
        }
        return stand;
    }

    private static void assertRoute(List<BlockCoord> route) {
        assertTrue(
                route.size() >= 2,
                "маршрут из-за склада не пустой: " + route
                        + " Иначе тест assertRoute перестанет ловить эту поломку. При смене assertRoute проверь это утверждение.");
        int span = route.get(route.size() - 1).x() - route.get(0).x();
        assertTrue(
                span >= 32,
                "маршрут длиной " + span + " блоков идёт по тропинке: короче 32 игра бы не нашла путь"
                        + " Иначе тест assertRoute перестанет ловить эту поломку. При смене assertRoute проверь это утверждение.");
        for (BlockCoord point : route) {
            if (point.x() < DOOR.x()) {
                assertEquals(
                        0,
                        point.z(),
                        "по тропинке дух идёт по улице z=0, не срезает через стены: " + point
                                + " Иначе тест assertRoute перестанет ловить эту поломку. При смене assertRoute проверь это утверждение.");
            }
        }
    }

    private static void assertReach(World world, PathNetwork network, BlockCoord stand) {
        int dx = Math.abs(stand.x() - CHEST.x());
        int dz = Math.abs(stand.z() - CHEST.z());
        assertTrue(
                dx <= BoardTuning.workStandRadius() && dz <= BoardTuning.workStandRadius(),
                "стоянка " + stand + " рядом с сундуком"
                        + " Иначе тест assertReach перестанет ловить эту поломку. При смене assertReach проверь это утверждение.");
        assertTrue(
                Math.abs(stand.y() - CHEST.y()) <= 1,
                "стоянка " + stand + " на полу склада"
                        + " Иначе тест assertReach перестанет ловить эту поломку. При смене assertReach проверь это утверждение.");
        assertTrue(
                MiningSafety.canStand(stand, world),
                "стоянка " + stand + " проходима"
                        + " Иначе тест assertReach перестанет ловить эту поломку. При смене assertReach проверь это утверждение. Иначе поломка пройдёт незамеченной в прогоне.");
        assertTrue(
                SpiritReach.reachable(stand, CHEST),
                "стоянка " + stand + " в досягаемости сундука"
                        + " Иначе тест assertReach перестанет ловить эту поломку. При смене assertReach проверь это утверждение.");
        List<BlockCoord> route = NetworkRoute.plan(network, START, stand, world)
                .orElseThrow(() -> new AssertionError("до стоянки " + stand + " нет маршрута по тропинке"));
        assertEquals(
                stand,
                route.get(route.size() - 1),
                "маршрут кончается на стоянке " + stand + ": " + route
                        + " Иначе тест assertReach перестанет ловить эту поломку. При смене assertReach проверь это утверждение.");
    }

    private static Set<BlockCoord> walkableFrom(World world, BlockCoord start, BuildBox box) {
        Set<BlockCoord> seen = new HashSet<>();
        if (!MiningSafety.canStand(start, world)) {
            return seen;
        }
        Deque<BlockCoord> queue = new ArrayDeque<>();
        seen.add(start);
        queue.add(start);
        while (!queue.isEmpty()) {
            BlockCoord from = queue.poll();
            for (int[] side : SIDES) {
                BlockCoord next = BodyStep.twoWay(from, side[0], side[1], world);
                if (next != null && box.contains(next) && seen.add(next)) {
                    queue.add(next);
                }
            }
        }
        return seen;
    }

    private static PathNetwork network(World world) {
        PathNetwork network = new PathNetwork();
        NetworkBuilder.chains(List.of(DOOR), STREET_BOX, world).forEach(network::add);
        return network;
    }

    private static World warehouse() {
        World world = new World();
        world.slab(0, -25, -8, 60, 8);
        for (int x = -25; x <= 39; x++) {
            for (int z = -8; z <= 8; z++) {
                if (z != 0) {
                    world.solid(x, 1, z).solid(x, 2, z);
                }
            }
        }
        for (int x = 40; x <= 52; x++) {
            for (int z = -6; z <= 6; z++) {
                boolean wall = x == 40 || x == 52 || z == -6 || z == 6;
                if (wall && !(x == DOOR.x() && z == DOOR.z())) {
                    for (int y = 1; y <= 3; y++) {
                        world.solid(x, y, z);
                    }
                }
            }
        }
        world.solid(CHEST.x(), CHEST.y(), CHEST.z());
        return world;
    }

    private static final class World implements SafetyView {
        private final Set<BlockCoord> solid = new HashSet<>();

        World solid(int x, int y, int z) {
            solid.add(new BlockCoord(x, y, z));
            return this;
        }

        World slab(int y, int x1, int z1, int x2, int z2) {
            for (int x = x1; x <= x2; x++) {
                for (int z = z1; z <= z2; z++) {
                    solid(x, y, z);
                }
            }
            return this;
        }

        WorkStand.Ground ground() {
            return new WorkStand.Ground() {
                @Override
                public int feet(int x, int z) {
                    for (int y = 3; y >= -2; y--) {
                        if (solid.contains(new BlockCoord(x, y, z))) {
                            return y + 1;
                        }
                    }
                    return Integer.MIN_VALUE;
                }

                @Override
                public boolean free(BlockCoord feet) {
                    return cellAt(feet) == Cell.PASSABLE
                            && cellAt(new BlockCoord(feet.x(), feet.y() + 1, feet.z())) == Cell.PASSABLE;
                }
            };
        }

        @Override
        public Cell cellAt(BlockCoord coord) {
            return solid.contains(coord) ? Cell.SOLID : Cell.PASSABLE;
        }
    }
}
