package com.theminespirit.neoforge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.VitalGate;
import com.theminespirit.core.board.graph.VitalNeeds;
import com.theminespirit.core.building.BuildReservations;
import com.theminespirit.core.movement.EscapeRestore;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.policy.BlockProtection;
import com.theminespirit.core.policy.PlacedBlockSet;
import com.theminespirit.core.policy.ProtectionConfig;
import com.theminespirit.core.town.ColumnMask;
import com.theminespirit.core.town.SettlementScan;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InterruptExitTests {
    private static final UUID SPIRIT = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final int LEVEL = 70;

    private static PlacedBlockSet temporaryColumn(int x, int z, int height) {
        PlacedBlockSet set = new PlacedBlockSet();
        for (int dy = 0; dy < height; dy++) {
            set.add(new BlockCoord(x, LEVEL + dy, z));
        }
        return set;
    }

    private static BoardTicket ticket(String id, BoardCase boardCase) {
        return new BoardTicket(
                id, boardCase, Optional.empty(), 50, 0, 1, BoardTicket.ALWAYS_CAUSE, List.of(), Map.of());
    }

    @Test
    void pillarUp_InterruptedOnTop_TemporaryMarksAndPerchNeedStayVisible() {
        PlacedBlockSet column = temporaryColumn(3, 3, 30);

        assertEquals(
                30,
                column.size(),
                "Столб, прерванный на середине, оставляет каждую поставленную клетку временной меткой: "
                        + "иначе граф не увидит подмости и дух останется на вершине без задачи");
        assertTrue(
                column.contains(new BlockCoord(3, LEVEL + 29, 3)),
                "Вершина столба тоже помечена: нужда perch считается по временному блоку под ногами");

        Map<String, String> live = Map.of("perch", "на своих подмостях выше безопасного прыжка");
        assertTrue(
                VitalGate.refusal(
                                ticket("graph:event:storage_mixed:sort_chest:1", BoardCase.ACTIVITY_UNLOAD),
                                live,
                                VitalNeeds.needs())
                        .isPresent(),
                "Перебитый на вершине дух не берёт работу внизу: иначе тикет уходит ему и падает «пути нет»");
        assertFalse(
                VitalGate.refusal(ticket("graph:rescue_spirit", BoardCase.RESCUE_SPIRIT), live, VitalNeeds.needs())
                        .isPresent(),
                "Спуск по своему столбу выдаётся как спасение: уборка и выход — работа графа");
    }

    @Test
    void fill_Interrupted_PartialFillStaysVisibleToAudit() {
        FakeWorld world = new FakeWorld();
        world.put(3, LEVEL, 1, "minecraft:dirt");
        world.temporary.add(new BlockCoord(3, LEVEL, 1));

        SettlementScan.Strays found =
                SettlementScan.scan(ColumnMask.rect(0, 0, 5, 3).cells(), LEVEL, rules(), world);

        assertEquals(
                1,
                found.strays().size(),
                "Недосыпанная засыпка оставляет временный блок, а скан лота видит его лишним: "
                        + "граф поставит уборку, а не забудет след");
        assertEquals("minecraft:dirt", found.strays().get(0).blockId(), "Скан называет, что именно убирать");
    }

    @Test
    void clearArea_Interrupted_ReleasesReservationsAndKeepsScaffoldMarked() {
        BuildReservations reservations = new BuildReservations();
        BlockCoord first = new BlockCoord(10, LEVEL, 10);
        BlockCoord second = new BlockCoord(11, LEVEL, 10);
        reservations.reserve(first, SPIRIT);
        reservations.reserve(second, SPIRIT);

        reservations.releaseAll(SPIRIT);

        assertEquals(0, reservations.size(), "Отмена расчистки отпускает брони клеток: иначе другой дух не возьмёт их");
        assertTrue(
                reservations.reserve(first, UUID.fromString("55555555-5555-5555-5555-555555555555")),
                "После отмены клетку может забронировать другой дух: бронь не умирает вместе с задачей");
        assertTrue(
                temporaryColumn(10, 10, 4).contains(new BlockCoord(10, LEVEL + 3, 10)),
                "Подмости остаются временной меткой: вершина даст нужду perch, а скан — задачу уборки");
    }

    @Test
    void digOut_Interrupted_RestoreReturnsSpoilExceptBody() {
        BlockCoord feet = new BlockCoord(7, 50, 7);

        assertTrue(
                EscapeRestore.blockedByBody(new BlockCoord(7, 50, 7), feet),
                "Клетку тела не закладывают: иначе дух замурует сам себя при отмене dig_out");
        assertTrue(
                EscapeRestore.blockedByBody(new BlockCoord(7, 51, 7), feet),
                "Клетку головы тоже не закладывают: возврат spoil щадит тело духа");
        assertFalse(
                EscapeRestore.blockedByBody(new BlockCoord(7, 49, 7), feet),
                "Остальные прокопанные клетки возвращаются: прерванный dig_out не оставляет за собой нору");
    }

    @Test
    void bridge_Interrupted_PlacedCellsStayMarkedTemporary() {
        PlacedBlockSet deck = new PlacedBlockSet();
        deck.add(new BlockCoord(20, LEVEL, 20));
        deck.add(new BlockCoord(21, LEVEL, 20));
        deck.add(new BlockCoord(22, LEVEL, 20));

        assertEquals(
                3,
                deck.size(),
                "Недоложенный мост оставляет настил временными метками: граф увидит след и поставит уборку");
        assertTrue(
                deck.remove(new BlockCoord(21, LEVEL, 20)),
                "Уборка снимает метку по клетке: след не остаётся навсегда после разбора моста");
        assertEquals(2, deck.size(), "Снятая клетка больше не числится временной");
    }

    @Test
    void goto_Interrupted_RoutePillarStaysMarkedTemporary() {
        PlacedBlockSet route = temporaryColumn(30, 30, 2);

        assertEquals(
                2,
                route.size(),
                "Маршрут, прерванный на подъёме, оставляет поставленные клетки временными: "
                        + "иначе граф не отличит свои подмости от природного рельефа");
    }

    @Test
    void perchNeed_Threat_DefendAndRetreatStayAllowed() {
        Map<String, String> live = Map.of("perch", "на своих подмостях");

        assertFalse(
                VitalGate.refusal(
                                ticket("graph:event:life_threat:defend", BoardCase.ACTIVITY_DEFEND),
                                live,
                                VitalNeeds.needs())
                        .isPresent(),
                "Угроза сильнее подмостей: дух отбивается или отходит, нужда perch не запирает его на вершине");
    }

    @Test
    void perchNeed_Night_SleepRefusedButRescueAllowed() {
        Map<String, String> live = Map.of("perch", "на своих подмостях");

        assertTrue(
                VitalGate.refusal(ticket("graph:event:night:sleep", BoardCase.ACTIVITY_REST), live, VitalNeeds.needs())
                        .isPresent(),
                "Сон — обычная работа: на вершине столба он не выдаётся, сначала спуск");
        assertFalse(
                VitalGate.refusal(ticket("graph:rescue_spirit", BoardCase.RESCUE_SPIRIT), live, VitalNeeds.needs())
                        .isPresent(),
                "Ночью спасение со спуском остаётся доступным");
    }

    private static BlockProtection rules() {
        String json = "{\"deny\": [\"bedrock\"], \"natural\": [\"stone\", \"dirt\", \"short_grass\"], "
                + "\"strays\": [\"*_fence\", \"cobblestone\"], \"buildingThreshold\": 6, "
                + "\"maxUnprotectVolume\": 4096}";
        return ProtectionConfig.parse(json, "interrupt-exit-test.json");
    }

    private static final class FakeWorld implements SettlementScan.World {
        private final Map<BlockCoord, String> blocks = new HashMap<>();
        private final Set<BlockCoord> temporary = new HashSet<>();

        void put(int x, int y, int z, String id) {
            blocks.put(new BlockCoord(x, y, z), id);
        }

        @Override
        public boolean loaded(int x, int z) {
            return true;
        }

        @Override
        public boolean open(int x, int y, int z) {
            String id = blockId(x, y, z);
            return "minecraft:air".equals(id) || "minecraft:water".equals(id);
        }

        @Override
        public String blockId(int x, int y, int z) {
            return blocks.getOrDefault(new BlockCoord(x, y, z), y < LEVEL ? "minecraft:stone" : "minecraft:air");
        }

        @Override
        public int top(int x, int z) {
            return blocks.keySet().stream()
                    .filter(at -> at.x() == x && at.z() == z)
                    .mapToInt(BlockCoord::y)
                    .max()
                    .orElse(LEVEL - 1);
        }

        @Override
        public boolean planned(int x, int y, int z) {
            return false;
        }

        @Override
        public OptionalInt lotLevel(int x, int z) {
            return OptionalInt.empty();
        }

        @Override
        public OptionalInt dugTop(int x, int z) {
            return OptionalInt.empty();
        }

        @Override
        public boolean playerPlaced(int x, int y, int z) {
            return false;
        }

        @Override
        public boolean spiritMarked(int x, int y, int z) {
            return false;
        }

        @Override
        public boolean temporary(int x, int y, int z) {
            return temporary.contains(new BlockCoord(x, y, z));
        }

        @Override
        public boolean registered(int x, int y, int z) {
            return false;
        }

        @Override
        public boolean plant(String blockId) {
            return false;
        }
    }
}
