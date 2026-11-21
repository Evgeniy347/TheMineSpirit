package com.theminespirit.core.observation;

import static com.theminespirit.core.observation.WorldObservationTestsSupport.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class WorldObservationTests {
    @Test
    void hash_SameDataDifferentListOrder_IsEqual() {
        WorldObservation first = observation(
                List.of(new ItemCount("oak_log", 5), new ItemCount("iron_pickaxe", 1)),
                List.of(
                        new EntityRef("minecraft:cow", new BlockCoord(14, 64, -1), 5.0),
                        new EntityRef("minecraft:sheep", new BlockCoord(2, 64, 1), 9.0)));
        WorldObservation second = observation(
                List.of(new ItemCount("iron_pickaxe", 1), new ItemCount("oak_log", 5)),
                List.of(
                        new EntityRef("minecraft:sheep", new BlockCoord(2, 64, 1), 9.0),
                        new EntityRef("minecraft:cow", new BlockCoord(14, 64, -1), 5.0)));

        assertEquals(
                first.hash(),
                second.hash(),
                "hash наблюдения обязан быть стабильным при любом порядке входных списков: иначе две одинаковые "
                        + "картины мира дают разные хэши, и кэш решений ИИ промахивается при каждом пересборе списка При смене hash_SameDataDifferentListOrder_IsEqual проверь это утверждение.");
        assertEquals(
                first,
                second,
                "Наблюдения с одними данными и разным порядком списков должны быть равны: канонизация в конструкторе "
                        + "обязана сортировать списки Иначе тест hash_SameDataDifferentListOrder_IsEqual перестанет ловить эту поломку. При смене hash_SameDataDifferentListOrder_IsEqual проверь это утверждение.");
    }

    @Test
    void hash_DataChanged_Differs() {
        WorldObservation base = observation(List.of(new ItemCount("oak_log", 5)), List.of());
        WorldObservation changed = observation(List.of(new ItemCount("oak_log", 6)), List.of());

        assertNotEquals(
                base.hash(),
                changed.hash(),
                "hash обязан меняться при изменении данных наблюдения: иначе ИИ считает изменившийся мир прежним "
                        + "и не перепланирует При смене hash_DataChanged_Differs проверь это утверждение.");
    }

    @Test
    void hash_HexForm_IsSha256Length() {
        String hash = observation(List.of(), List.of()).hash();

        assertEquals(
                64,
                hash.length(),
                "hash — SHA-256 в hex, ровно 64 символа: другая длина означает, что используется не SHA-256 "
                        + "и хэши несравнимы между версиями Иначе тест hash_HexForm_IsSha256Length перестанет ловить эту поломку. При смене hash_HexForm_IsSha256Length проверь это утверждение.");
    }

    @Test
    void hash_InventoryWithDuplicatesAggregated_IsEqualRegardlessOfSlots() {
        WorldObservation first = observation(
                List.of(new ItemCount("oak_log", 64), new ItemCount("oak_log", 5), new ItemCount("iron_pickaxe", 1)),
                List.of());
        WorldObservation second =
                observation(List.of(new ItemCount("iron_pickaxe", 1), new ItemCount("oak_log", 69)), List.of());

        assertEquals(
                first.hash(),
                second.hash(),
                "Одинаковый мультимножественный инвентарь с повторами должен давать одинаковый hash после агрегации Иначе тест hash_InventoryWithDuplicatesAggregated_IsEqualRegardlessOfSlots перестанет ловить эту поломку. При смене hash_InventoryWithDuplicatesAggregated_IsEqualRegardlessOfSlots проверь это утверждение.");
    }

    @Test
    void hash_SameWorldDifferentTicks_SameHash() {
        WorldObservation first = new WorldObservation(
                "spirit_1",
                1L,
                new BlockCoord(10, 64, -3),
                List.of(new ItemCount("oak_log", 5)),
                List.of(),
                List.of(),
                List.of(),
                32);
        WorldObservation second = new WorldObservation(
                "spirit_1",
                999999L,
                new BlockCoord(10, 64, -3),
                List.of(new ItemCount("oak_log", 5)),
                List.of(),
                List.of(),
                List.of(),
                32);
        assertEquals(
                first.hash(),
                second.hash(),
                "hash должен описывать картину мира без тика: одно и то же состояние в разные тики даёт одинаковый hash Иначе тест hash_SameWorldDifferentTicks_SameHash перестанет ловить эту поломку. При смене hash_SameWorldDifferentTicks_SameHash проверь это утверждение.");
    }

    @Test
    void hash_EntityMovesBetweenBlocks_HashDiffers() {
        WorldObservation first =
                observation(List.of(), List.of(new EntityRef("minecraft:cow", new BlockCoord(14, 64, -1))));
        WorldObservation second =
                observation(List.of(), List.of(new EntityRef("minecraft:cow", new BlockCoord(15, 64, -1))));
        assertNotEquals(
                first.hash(),
                second.hash(),
                "Сдвиг сущности на блок изменяет картину мира — hash должен отличаться Иначе тест hash_EntityMovesBetweenBlocks_HashDiffers перестанет ловить эту поломку. При смене hash_EntityMovesBetweenBlocks_HashDiffers проверь это утверждение.");
    }

    @Test
    void hash_HealthChanged_HashDiffers() {
        WorldObservation base = richObservation();
        WorldObservation hurt = withSpirit(
                base,
                SpiritStatus.of(
                        base.spirit().held(), 12.0, 20.0, 0.7, base.spirit().boardTask()));
        assertNotEquals(
                base.hash(),
                hurt.hash(),
                "Падение здоровья меняет наблюдение — стратег обязан перепланировать, а не считать мир прежним Иначе тест hash_HealthChanged_HashDiffers перестанет ловить эту поломку. При смене hash_HealthChanged_HashDiffers проверь это утверждение.");
    }

    @Test
    void hash_HeldItemChanged_HashDiffers() {
        WorldObservation base = richObservation();
        WorldObservation switched = withSpirit(
                base,
                SpiritStatus.of(
                        new HeldItem("minecraft:diamond_pickaxe", 150),
                        18.0,
                        20.0,
                        0.7,
                        base.spirit().boardTask()));
        assertNotEquals(
                base.hash(),
                switched.hash(),
                "Смена предмета в руке — ресурс для стратега: hash обязан измениться, иначе решение устареет При смене hash_HeldItemChanged_HashDiffers проверь это утверждение.");
    }

    @Test
    void hash_GroundItemsChanged_HashDiffers() {
        WorldObservation base = richObservation();
        WorldObservation picked = new WorldObservation(
                base.botId(),
                base.tick(),
                base.position(),
                base.inventory(),
                base.blocks(),
                base.entities(),
                base.containers(),
                base.radius(),
                base.spirit(),
                List.of(),
                base.containerItems(),
                base.time());
        assertNotEquals(
                base.hash(),
                picked.hash(),
                "Подобранные с земли предметы меняют ресурсы — hash обязан отличаться Иначе тест hash_GroundItemsChanged_HashDiffers перестанет ловить эту поломку. При смене hash_GroundItemsChanged_HashDiffers проверь это утверждение.");
    }

    @Test
    void hash_DayTimeChangedWithinSameDay_SameHash() {
        WorldObservation base = richObservation();
        WorldObservation laterSameDay = new WorldObservation(
                base.botId(),
                base.tick() + 100,
                base.position(),
                base.inventory(),
                base.blocks(),
                base.entities(),
                base.containers(),
                base.radius(),
                base.spirit(),
                base.groundItems(),
                base.containerItems(),
                new WorldTime(7000L, true));
        assertEquals(
                base.hash(),
                laterSameDay.hash(),
                "Точный тик суток в наблюдении есть, но в hash входит только день/ночь: иначе тик растёт каждый "
                        + "цикл и стратег зовёт модель впустую При смене hash_DayTimeChangedWithinSameDay_SameHash проверь это утверждение.");
    }
}
