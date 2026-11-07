package com.theminespirit.neoforge.intellect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.StockView;
import com.theminespirit.core.intellect.Expedition;
import com.theminespirit.core.inventory.Stack;
import com.theminespirit.core.tactics.Point;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ExpeditionControllerTests {
    private static final UUID FIRST = new UUID(0, 1);
    private static final UUID SECOND = new UUID(0, 2);

    private static final class Member implements ExpeditionMember {
        private final UUID id;
        private final String name;
        private final List<Stack> inventory;
        private final boolean pickaxe;
        private boolean removed;
        private String script;

        private Member(UUID id, String name, List<Stack> inventory, boolean pickaxe) {
            this.id = id;
            this.name = name;
            this.inventory = inventory;
            this.pickaxe = pickaxe;
        }

        @Override
        public UUID id() {
            return id;
        }

        @Override
        public boolean removed() {
            return removed;
        }

        @Override
        public String displayName() {
            return name;
        }

        @Override
        public Optional<String> currentScript() {
            return Optional.ofNullable(script);
        }

        @Override
        public List<Stack> inventory() {
            return inventory;
        }

        @Override
        public boolean hasPickaxe() {
            return pickaxe;
        }
    }

    private static List<Stack> kit() {
        return List.of(
                new Stack("minecraft:diamond_pickaxe", 0, 1, 1),
                new Stack("minecraft:torch", 0, 8, 64),
                new Stack("minecraft:bread", 0, 3, 64));
    }

    private static Expedition expedition() {
        return new Expedition(List.of(FIRST, SECOND), new Point(0, 60, 0), 0);
    }

    @Test
    void update_TeamWithKit_DispatchesWholeParty() {
        ExpeditionController controller = new ExpeditionController();
        Member first = new Member(FIRST, "s1", kit(), true);
        Member second = new Member(SECOND, "s2", List.of(), true);
        controller.offer(expedition());
        List<String> scripts = new ArrayList<>();

        controller.update(List.of(first, second), new StockView(Map.of()), 0, (id, script, task, now) -> {
            scripts.add(id + ":" + script);
            return true;
        });

        assertTrue(controller.lastDispatch().isPresent(), "Собранный набор запускает поход, а не откладывает его");
        assertEquals(2, scripts.size(), "В шахту идёт вся команда: ведущий и напарник получают свои скрипты");
        assertTrue(scripts.contains(FIRST + ":mine_vein"), "Ведущий копает жилу: " + scripts);
    }

    @Test
    void update_InventoryWithEmptySlots_DispatchesWholeParty() {
        ExpeditionController controller = new ExpeditionController();
        List<Stack> first = Arrays.asList(
                null,
                new Stack("minecraft:diamond_pickaxe", 0, 1, 1),
                null,
                new Stack("minecraft:torch", 0, 8, 64),
                null,
                new Stack("minecraft:bread", 0, 3, 64),
                null);
        Member leader = new Member(FIRST, "s1", first, true);
        Member partner = new Member(SECOND, "s2", Arrays.asList(null, null), true);
        controller.offer(expedition());
        List<String> scripts = new ArrayList<>();

        controller.update(List.of(leader, partner), new StockView(Map.of()), 0, (id, script, task, now) -> {
            scripts.add(id + ":" + script);
            return true;
        });

        assertTrue(
                controller.lastDispatch().isPresent(),
                "Пустые слоты инвентаря не мешают походу: набор собран, команда обязана выйти в шахту");
        assertEquals(2, scripts.size(), "В шахту идёт вся команда, а не только дух с непустыми слотами: " + scripts);
    }

    @Test
    void update_InventoryWithEmptySlotsAndGap_RefusesWithReason() {
        ExpeditionController controller = new ExpeditionController();
        Member leader = new Member(
                FIRST, "s1", Arrays.asList(null, new Stack("minecraft:diamond_pickaxe", 0, 1, 1), null), true);
        Member partner = new Member(SECOND, "s2", Arrays.asList(null, null), true);
        controller.offer(expedition());
        List<UUID> launched = new ArrayList<>();

        controller.update(
                List.of(leader, partner), new StockView(Map.of("minecraft:bread", 3)), 0, (id, script, task, now) -> {
                    launched.add(id);
                    return true;
                });

        assertTrue(controller.lastDispatch().isEmpty(), "Без факелов поход ждёт, а не выходит в шахту");
        assertTrue(
                controller.refusal().orElse("").contains("torch"),
                "Причина ожидания называет недостающие факелы, а не пустые слоты: " + controller.refusal());
        assertEquals(List.of(), launched, "Никто не выходит в шахту, пока набор не собран");
    }

    @Test
    void update_LeaderWithoutPickaxe_RefusesWithReason() {
        ExpeditionController controller = new ExpeditionController();
        Member first = new Member(FIRST, "s1", kit(), false);
        Member second = new Member(SECOND, "s2", List.of(), false);
        controller.offer(expedition());
        List<UUID> launched = new ArrayList<>();

        controller.update(
                List.of(first, second),
                new StockView(Map.of("minecraft:torch", 8, "minecraft:bread", 3)),
                0,
                (id, script, task, now) -> {
                    launched.add(id);
                    return true;
                });

        assertTrue(controller.lastDispatch().isEmpty(), "Без кирки поход не начинается");
        assertTrue(
                controller.refusal().orElse("").contains("нет кирки"),
                "Причина отказа называет нехватку кирки: " + controller.refusal());
        assertEquals(List.of(), launched, "Ни один дух не выходит в шахту без кирки у лидера");
    }

    @Test
    void update_PartnerLost_RemainingSpiritReturnsHome() {
        ExpeditionController controller = new ExpeditionController();
        Member first = new Member(FIRST, "s1", kit(), true);
        Member second = new Member(SECOND, "s2", List.of(), true);
        controller.offer(expedition());
        List<String> scripts = new ArrayList<>();
        ExpeditionController.Launcher launcher = (id, script, task, now) -> {
            scripts.add(id + ":" + script);
            return true;
        };
        controller.update(List.of(first, second), new StockView(Map.of()), 0, launcher);
        scripts.clear();
        second.removed = true;

        controller.update(List.of(first, second), new StockView(Map.of()), 1, launcher);

        assertTrue(
                scripts.contains(FIRST + ":return_home"),
                "Потерянный напарник возвращает оставшегося домой, а не оставляет его одного: " + scripts);
    }
}
