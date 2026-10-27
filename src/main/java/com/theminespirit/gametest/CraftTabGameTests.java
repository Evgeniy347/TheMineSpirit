package com.theminespirit.gametest;

import com.theminespirit.core.crafting.OrderBook;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.town.ColumnMask;
import com.theminespirit.core.town.FenceLine;
import com.theminespirit.core.town.TownPlan;
import com.theminespirit.core.town.TownStreets;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.SettlementChestStore;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.board.SettlementPlotStore;
import com.theminespirit.neoforge.terminal.CraftChain;
import com.theminespirit.neoforge.terminal.CraftOrderStore;
import com.theminespirit.neoforge.terminal.CraftOrders;
import com.theminespirit.neoforge.terminal.CraftTerminalChannel;
import com.theminespirit.neoforge.terminal.CraftTerminalChannel.ChainReply;
import com.theminespirit.neoforge.terminal.CraftTerminalService;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
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
    private static final int FAR = 70;

    private CraftTabGameTests() {}

    @SceneStep(template = "arena", timeoutTicks = 40)
    public static void chainCraft_LogAndCoalInChests_TorchesAndLeftoversToPlayer(GameTestHelper helper) {
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

    @SceneStep(template = "arena", timeoutTicks = 40)
    public static void chainCraft_PlayerBeyondMargin_RefusedChestsIntact(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        settle(helper);
        chest(helper, LOGS, new ItemStack(Items.OAK_LOG, 2));
        chest(helper, COAL, new ItemStack(Items.COAL, 1));
        ServerPlayer player = GameTestFixtures.mockPlayer(helper, PLAYER, "craft_tab_far");
        player.moveTo(player.getX() + FAR + helper.getBounds().getXsize(), player.getY(), player.getZ());

        ChainReply reply = CraftChain.chain(player, "minecraft:torch", false, 1, true);
        ItemStack taken = CraftTerminalService.take(player, new ItemStack(Items.OAK_LOG), 1, true);

        int torches = GameTestFixtures.countInInventory(player.getInventory(), Items.TORCH);
        int logs = inChest(helper, LOGS, Items.OAK_LOG);
        unsettle(helper);
        helper.assertTrue(
                torches == 0 && taken.isEmpty() && logs == 2 && reply.crafts() == 0,
                "За 64 блоками от границы поселения сервер отказывает и в крафте, и во взятии: факелов " + torches
                        + ", взято " + taken + ", брёвен в сундуке " + logs);
        helper.succeed();
    }

    @SceneStep(template = "arena", timeoutTicks = 40)
    public static void takeAndDeposit_LogsInChest_MovesExactly(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        settle(helper);
        chest(helper, LOGS, new ItemStack(Items.OAK_LOG, 10));
        chest(helper, COAL, new ItemStack(Items.COAL, 1));
        ServerPlayer player = GameTestFixtures.mockPlayer(helper, PLAYER, "craft_tab_take");

        CraftTerminalService.take(player, new ItemStack(Items.OAK_LOG), 6, true);
        int afterTake = inChest(helper, LOGS, Items.OAK_LOG);
        ItemStack rest = CraftTerminalService.deposit(player, new ItemStack(Items.OAK_LOG, 2));

        int inInventory = GameTestFixtures.countInInventory(player.getInventory(), Items.OAK_LOG);
        int afterDeposit = inChest(helper, LOGS, Items.OAK_LOG);
        unsettle(helper);
        helper.assertTrue(
                afterTake == 4 && inInventory == 6,
                "Взятие из сети переносит ровно 6 брёвен из сундука в инвентарь: в сундуке " + afterTake + ", у игрока "
                        + inInventory);
        helper.assertTrue(
                rest.isEmpty() && afterDeposit == 6,
                "Положенное в сеть ложится в сундук с тем же предметом, а не в первый пустой: брёвен в сундуке "
                        + afterDeposit + ", не влезло " + rest);
        helper.succeed();
    }

    @SceneStep(template = "arena", timeoutTicks = 40)
    public static void order_EmptyChests_ShortageQueuedAndSurvivesSave(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        settle(helper);
        chest(helper, LOGS, ItemStack.EMPTY);
        ServerPlayer player = GameTestFixtures.mockPlayer(helper, PLAYER, "craft_tab_order");

        CraftTerminalService.handle(player, order("minecraft:torch"));

        CraftOrderStore store = CraftOrderStore.get(helper.getLevel());
        Map<String, Integer> shortage = CraftOrders.shortage(helper.getLevel());
        CraftOrderStore restored = CraftOrderStore.load(
                store.save(new CompoundTag(), helper.getLevel().registryAccess()),
                helper.getLevel().registryAccess());
        Map<String, Integer> afterRestart = restored.book().shortage();
        boolean event = CraftOrders.waiting(helper.getLevel());
        cancelOwn(store, player);
        unsettle(helper);
        helper.assertTrue(
                Map.of("coal", 1, "oak_log", 1).equals(shortage) && event,
                "Заказ факелов при пустых сундуках ждёт сырья до бревна и угля, и событие player_craft_order "
                        + "включено (по нему граф ставит задания с приоритетом игрока): недостача " + shortage);
        helper.assertTrue(
                shortage.equals(afterRestart),
                "Очередь заказов переживает перезапуск (SavedData): после загрузки недостача " + afterRestart);
        helper.succeed();
    }

    @SceneStep(template = "arena", timeoutTicks = 40)
    public static void order_ResourcesArrive_SpiritCraftsIntoChestAndReportsInChat(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        settle(helper);
        chest(helper, LOGS, ItemStack.EMPTY);
        chest(helper, COAL, ItemStack.EMPTY);
        List<Component> chat = new ArrayList<>();
        ServerPlayer player = GameTestFixtures.mockPlayer(helper, PLAYER, "craft_tab_done", chat);
        CraftTerminalService.handle(player, order("minecraft:torch"));
        fill(helper, LOGS, new ItemStack(Items.OAK_LOG, 1));
        fill(helper, COAL, new ItemStack(Items.COAL, 1));
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(5, 1, 5));
        player.moveTo(player.getX() + FAR + helper.getBounds().getXsize(), player.getY(), player.getZ());

        int done = CraftOrders.refresh(helper.getLevel());

        int torches = inChest(helper, LOGS, Items.TORCH) + inChest(helper, COAL, Items.TORCH);
        int logs = inChest(helper, LOGS, Items.OAK_LOG);
        boolean told = chat.stream()
                .anyMatch(line -> line.getContents() instanceof TranslatableContents text
                        && text.getKey().equals("theminespirit.order.ready"));
        spirit.discard();
        cancelOwn(CraftOrderStore.get(helper.getLevel()), player);
        unsettle(helper);
        helper.assertTrue(
                done == 1 && torches == 4 && logs == 0,
                "Сырьё появилось — дух крафтит заказ из сундуков поселения и кладёт 4 факела в сундук (игрок "
                        + "далеко): готово заказов " + done + ", факелов в сундуках " + torches + ", брёвен " + logs);
        helper.assertTrue(told, "О готовом заказе дух пишет заказчику в чат: сообщения " + chat);
        helper.succeed();
    }

    private static CraftTerminalChannel.Action order(String recipe) {
        return new CraftTerminalChannel.Action(CraftTerminalChannel.Kind.ORDER, ItemStack.EMPTY, 1, true, recipe);
    }

    private static void cancelOwn(CraftOrderStore store, ServerPlayer player) {
        for (OrderBook.Order waiting : store.book().waiting()) {
            store.book().cancel(waiting.id(), player.getUUID().toString());
        }
        store.changed();
    }

    private static void fill(GameTestHelper helper, BlockPos relative, ItemStack content) {
        if (helper.getBlockEntity(relative) instanceof Container container) {
            container.setItem(0, content);
        }
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
