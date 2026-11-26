package com.theminespirit.core.town;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.theminespirit.core.observation.BlockCoord;
import org.junit.jupiter.api.Test;

class FloatingContainerTests {
    @Test
    void classify_TemporaryChest_IsContainerNotGenericTemporaryBlock() {
        FloatingBlocksTests.Fake world = new FloatingBlocksTests.Fake();
        world.put(2, 67, 2, "minecraft:chest");
        world.markTemporary(new BlockCoord(2, 67, 2));

        assertEquals(
                FloatingBlocks.Kind.CHEST,
                world.only(2, 67, 2).kind(),
                "Временный сундук обязан идти через перенос содержимого: иначе уборка пути сломает его как "
                        + "обычный блок и потеряет вещи. При смене classify_TemporaryChest_IsContainerNotGenericTemporaryBlock "
                        + "проверь FloatingCleanup и ChestDemolition.");
    }

    @Test
    void classify_HopperAboveGround_IsContainer() {
        FloatingBlocksTests.Fake world = new FloatingBlocksTests.Fake();
        world.put(3, 68, 3, "minecraft:hopper");

        assertEquals(
                FloatingBlocks.Kind.CONTAINER,
                world.only(3, 68, 3).kind(),
                "Воронка — контейнер: её содержимое переносят до сноса, иначе вещи выбрасываются вместе "
                        + "с блоком. При смене classify_HopperAboveGround_IsContainer проверь ContainerAccess и ChestPlans.");
    }
}
