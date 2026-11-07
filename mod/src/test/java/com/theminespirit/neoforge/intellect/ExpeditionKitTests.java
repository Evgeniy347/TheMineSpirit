package com.theminespirit.neoforge.intellect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.StockView;
import com.theminespirit.core.inventory.Stack;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ExpeditionKitTests {
    private static final UUID FIRST = new UUID(0, 11);
    private static final UUID SECOND = new UUID(0, 12);

    private static final class Member implements ExpeditionMember {
        private final UUID id;
        private final List<Stack> inventory;

        private Member(UUID id, List<Stack> inventory) {
            this.id = id;
            this.inventory = inventory;
        }

        @Override
        public UUID id() {
            return id;
        }

        @Override
        public boolean removed() {
            return false;
        }

        @Override
        public String displayName() {
            return id.toString();
        }

        @Override
        public Optional<String> currentScript() {
            return Optional.empty();
        }

        @Override
        public List<Stack> inventory() {
            return inventory;
        }

        @Override
        public boolean hasPickaxe() {
            return true;
        }
    }

    private static Stack stack(String item, int count) {
        return new Stack(item, 0, count, 64);
    }

    private static List<ExpeditionMember> team(List<Stack> first, List<Stack> second) {
        return List.of(new Member(FIRST, first), new Member(SECOND, second));
    }

    @Test
    void missing_InventoryWithEmptySlots_CountsPresentKit() {
        List<Stack> first =
                Arrays.asList(null, stack("minecraft:iron_pickaxe", 1), null, stack("minecraft:torch", 4), null);
        List<Stack> second = Arrays.asList(null, stack("minecraft:torch", 4), null, stack("minecraft:bread", 3), null);

        List<String> missing = ExpeditionKit.missing(team(first, second), new StockView(Map.of()));

        assertEquals(
                List.of(),
                missing,
                "Пустые слоты — не предметы: набор, собранный в остальных слотах, обязан считаться полным, а не ронять поход");
    }

    @Test
    void missing_InventoryWithEmptySlotsAndGap_ListsOnlyMissingKit() {
        List<Stack> first = Arrays.asList(null, stack("minecraft:iron_pickaxe", 1), null);
        List<Stack> second = Arrays.asList(null, null);

        List<String> missing = ExpeditionKit.missing(team(first, second), new StockView(Map.of()));

        assertEquals(2, missing.size(), "Кирка есть, факелов и еды нет: ровно две нехватки, фактически: " + missing);
        assertTrue(
                missing.stream().anyMatch(line -> line.contains("torch")),
                "Причина ожидания обязана называть факелы, иначе владелец не знает, что добрать: " + missing);
        assertTrue(
                missing.stream().anyMatch(line -> line.contains("food")),
                "Причина ожидания обязана называть еду, иначе владелец не знает, что добрать: " + missing);
    }

    @Test
    void present_EmptySlots_ReturnsOnlyItems() {
        Stack torch = stack("minecraft:torch", 8);

        List<Stack> present = ExpeditionKit.present(Arrays.asList(null, torch, null));

        assertEquals(
                List.of(torch),
                present,
                "Инвентарь духа для похода — только реальные стопки: пустые слоты не должны попадать в список и ронять копирование");
    }
}
