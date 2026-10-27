package com.theminespirit.gametest;

import static com.theminespirit.gametest.InventorySceneSupport.describe;
import static com.theminespirit.gametest.InventorySceneSupport.placeDoubleChest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.neoforge.inventory.ContainerAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
final class InventoryContainersGameTests {
    private InventoryContainersGameTests() {}

    @SceneStep(template = "arena", timeoutTicks = 200)
    public static void containerAccess_SingleChest_Opens27SlotChest(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        helper.setBlock(6, 1, 4, Blocks.CHEST);
        ContainerAccess.Open open = ContainerAccess.open(helper.getLevel(), helper.absolutePos(new BlockPos(6, 1, 4)));

        helper.assertTrue(
                open instanceof ContainerAccess.Opened opened
                        && opened.container().getContainerSize() == 27,
                "одиночный сундук обязан открываться как контейнер на 27 слотов, фактически " + describe(open));
        GameTestFixtures.finish(helper);
        helper.succeed();
    }

    @SceneStep(template = "arena", timeoutTicks = 200)
    public static void containerAccess_AdjacentChests_OpenAsDouble(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        placeDoubleChest(helper, 5, 6, 1, 4);
        ContainerAccess.Open open = ContainerAccess.open(helper.getLevel(), helper.absolutePos(new BlockPos(5, 1, 4)));

        helper.assertTrue(
                open instanceof ContainerAccess.Opened opened
                        && opened.container().getContainerSize() == 54,
                "два соседних сундука обязаны открываться как двойной на 54 слота, фактически " + describe(open));
        GameTestFixtures.finish(helper);
        helper.succeed();
    }

    @SceneStep(template = "arena", timeoutTicks = 200)
    public static void containerAccess_BarrelAndShulker_Open(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        helper.setBlock(5, 1, 3, Blocks.BARREL);
        helper.setBlock(6, 1, 5, Blocks.WHITE_SHULKER_BOX);
        ContainerAccess.Open barrel =
                ContainerAccess.open(helper.getLevel(), helper.absolutePos(new BlockPos(5, 1, 3)));
        ContainerAccess.Open shulker =
                ContainerAccess.open(helper.getLevel(), helper.absolutePos(new BlockPos(6, 1, 5)));

        helper.assertTrue(
                barrel instanceof ContainerAccess.Opened opened
                        && opened.container().getContainerSize() == 27,
                "бочка обязана открываться как контейнер на 27 слотов, фактически " + describe(barrel));
        helper.assertTrue(
                shulker instanceof ContainerAccess.Opened opened
                        && opened.container().getContainerSize() == 27,
                "шалкер-бокс обязан открываться как контейнер на 27 слотов, фактически " + describe(shulker));
        GameTestFixtures.finish(helper);
        helper.succeed();
    }

    @SceneStep(template = "arena", timeoutTicks = 200)
    public static void containerAccess_FurnaceHopperDispenserEnder_Refused(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        helper.setBlock(2, 1, 2, Blocks.FURNACE);
        helper.setBlock(3, 1, 2, Blocks.HOPPER);
        helper.setBlock(4, 1, 2, Blocks.DISPENSER);
        helper.setBlock(5, 1, 2, Blocks.ENDER_CHEST);

        for (int x = 2; x <= 5; x++) {
            ContainerAccess.Open open =
                    ContainerAccess.open(helper.getLevel(), helper.absolutePos(new BlockPos(x, 1, 2)));
            helper.assertTrue(
                    open instanceof ContainerAccess.Refused refused
                            && !refused.reason().isBlank(),
                    "печи, воронки, раздатчики и эндер-сундук запрещены: блок в (" + x + ",1,2) обязан дать отказ "
                            + "с пояснением, фактически " + describe(open));
        }
        GameTestFixtures.finish(helper);
        helper.succeed();
    }
}
