package com.theminespirit.gametest;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.town.ColumnMask;
import com.theminespirit.core.town.FenceLine;
import com.theminespirit.core.town.TownPlan;
import com.theminespirit.core.town.TownStreets;
import com.theminespirit.neoforge.SettlementChestStore;
import com.theminespirit.neoforge.board.SettlementPlotStore;
import com.theminespirit.neoforge.terminal.CraftChain;
import com.theminespirit.neoforge.terminal.CraftTerminalChannel.ChainReply;
import java.util.BitSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

public final class CraftTabGameTests {
    private static final BlockPos LOGS = new BlockPos(3, 1, 3);
    private static final BlockPos COAL = new BlockPos(6, 1, 3);
    private static final BlockPos PLAYER = new BlockPos(5, 1, 6);

    private CraftTabGameTests() {}

    @SceneStep(template = "arena", timeoutTicks = 40)
    public static void chainCraft_LogAndCoalInChests_TorchesAndLeftoversToPlayer(GameTestHelper helper) {
        RecipeKnowledgeGameTests.check(helper);
        GameTestFixtures.reset(helper);
        settle(helper);
        chest(helper, LOGS, new ItemStack(Items.OAK_LOG, 2));
        chest(helper, COAL, new ItemStack(Items.COAL, 1));
        ServerPlayer player = GameTestFixtures.mockPlayer(helper, PLAYER, "craft_tab_chain");

        ChainReply reply = CraftChain.chain(player, "minecraft:torch", false, 1, true);

        int torches = GameTestFixtures.countInInventory(player.getInventory(), Items.TORCH);
        int planks = GameTestFixtures.countInInventory(player.getInventory(), Items.OAK_PLANKS);
        int sticks = GameTestFixtures.countInInventory(player.getInventory(), Items.STICK);
        int logs = inChest(helper, LOGS, Items.OAK_LOG);
        int coal = inChest(helper, COAL, Items.COAL);
        unsettle(helper);
        helper.assertTrue(
                torches == 4 && planks == 2 && sticks == 3,
                "Многошаговый крафт факелов из бревна: игроку 4 факела и остатки 2 доски, 3 палки (доски и палки "
                        + "в явном виде не делаются); получено факелов " + torches + ", досок " + planks
                        + ", палок " + sticks + ", ответ " + reply.notice().getString());
        helper.assertTrue(
                logs == 1 && coal == 0,
                "Сундуки поселения уменьшаются ровно на потраченное: 1 бревно и 1 уголь; осталось брёвен " + logs
                        + ", угля " + coal);
        helper.succeed();
    }

    private static void settle(GameTestHelper helper) {
        BlockPos min = helper.absolutePos(BlockPos.ZERO);
        BlockPos max = helper.absolutePos(new BlockPos(10, 0, 10));
        int x1 = Math.min(min.getX(), max.getX());
        int z1 = Math.min(min.getZ(), max.getZ());
        int x2 = Math.max(min.getX(), max.getX());
        int z2 = Math.max(min.getZ(), max.getZ());
        ColumnMask empty = new ColumnMask(x1, z1, x2 - x1 + 1, z2 - z1 + 1, new BitSet());
        TownPlan plan = new TownPlan(
                new BlockCoord(x1 + 5, min.getY() + 1, z1 + 5),
                ColumnMask.rect(x1, z1, x2, z2),
                new TownStreets(ColumnMask.rect(x1, z1, x2, z1), empty, empty),
                List.of(),
                new FenceLine(List.of(), List.of()),
                List.of());
        SettlementPlotStore.get(helper.getLevel()).fixTown(plan);
    }

    private static void unsettle(GameTestHelper helper) {
        SettlementPlotStore.get(helper.getLevel()).clear();
        SettlementChestStore store = SettlementChestStore.get(helper.getLevel());
        store.forget(helper.absolutePos(LOGS));
        store.forget(helper.absolutePos(COAL));
    }

    private static void chest(GameTestHelper helper, BlockPos relative, ItemStack content) {
        helper.setBlock(relative, Blocks.CHEST);
        if (helper.getBlockEntity(relative) instanceof Container container) {
            container.setItem(0, content);
        }
        SettlementChestStore.get(helper.getLevel()).track(helper.absolutePos(relative));
    }

    private static int inChest(GameTestHelper helper, BlockPos relative, Item item) {
        return helper.getBlockEntity(relative) instanceof Container container
                ? GameTestFixtures.countInInventory(container, item)
                : -1;
    }
}
