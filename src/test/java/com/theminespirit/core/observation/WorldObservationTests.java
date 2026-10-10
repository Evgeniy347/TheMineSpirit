package com.theminespirit.core.observation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class WorldObservationTests {
    private static WorldObservation observation(List<InventoryEntry> inventory, List<EntityRef> entities) {
        return new WorldObservation(
                "spirit_1",
                123456L,
                new BlockPos(10, 64, -3),
                inventory,
                List.of(new InventoryEntry("oak_log", 12), new InventoryEntry("grass_block", 340)),
                entities,
                List.of(new ContainerRef("minecraft:chest", new BlockPos(8, 63, -5))),
                32);
    }

    @Test
    void hash_SameDataDifferentListOrder_IsEqual() {
        WorldObservation first = observation(
                List.of(new InventoryEntry("oak_log", 5), new InventoryEntry("iron_pickaxe", 1)),
                List.of(new EntityRef("minecraft:cow", new BlockPos(14, 64, -1), 5.0),
                        new EntityRef("minecraft:sheep", new BlockPos(2, 64, 1), 9.0)));
        WorldObservation second = observation(
                List.of(new InventoryEntry("iron_pickaxe", 1), new InventoryEntry("oak_log", 5)),
                List.of(new EntityRef("minecraft:sheep", new BlockPos(2, 64, 1), 9.0),
                        new EntityRef("minecraft:cow", new BlockPos(14, 64, -1), 5.0)));

        assertEquals(first.hash(), second.hash(),
                "hash наблюдения обязан быть стабильным при любом порядке входных списков: иначе две одинаковые "
                        + "картины мира дают разные хэши, и кэш решений ИИ промахивается при каждом пересборе списка");
        assertEquals(first, second,
                "Наблюдения с одними данными и разным порядком списков должны быть равны: канонизация в конструкторе "
                        + "обязана сортировать списки");
    }

    @Test
    void hash_DataChanged_Differs() {
        WorldObservation base = observation(List.of(new InventoryEntry("oak_log", 5)), List.of());
        WorldObservation changed = observation(List.of(new InventoryEntry("oak_log", 6)), List.of());

        assertNotEquals(base.hash(), changed.hash(),
                "hash обязан меняться при изменении данных наблюдения: иначе ИИ считает изменившийся мир прежним "
                        + "и не перепланирует");
    }

    @Test
    void fromJson_AfterToJson_EqualsOriginal() {
        WorldObservation original = observation(
                List.of(new InventoryEntry("oak_log", 5), new InventoryEntry("iron_pickaxe", 1)),
                List.of(new EntityRef("minecraft:cow", new BlockPos(14, 64, -1), 5.0)));

        WorldObservation restored = WorldObservation.fromJson(original.toJson());

        assertEquals(original, restored,
                "fromJson(toJson(x)) обязан возвращать равное наблюдение: иначе bridge теряет данные при передаче "
                        + "наблюдения по REST и решение принимается по испорченной картине мира");
    }

    @Test
    void hash_HexForm_IsSha256Length() {
        String hash = observation(List.of(), List.of()).hash();

        assertEquals(64, hash.length(),
                "hash — SHA-256 в hex, ровно 64 символа: другая длина означает, что используется не SHA-256 "
                        + "и хэши несравнимы между версиями");
    }
}
