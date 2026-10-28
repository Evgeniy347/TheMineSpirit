package com.theminespirit.neoforge.terminal;

import com.theminespirit.core.town.SettlementReach;
import com.theminespirit.neoforge.board.SettlementPlotStore;
import com.theminespirit.neoforge.terminal.CraftTerminalChannel.Action;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;

public final class CraftTerminalService {
    public static final int MAX_REQUEST = 64;
    private static final String KEY = "theminespirit.craft.";

    private CraftTerminalService() {}

    public static void handle(ServerPlayer player, Action action) {
        switch (action.kind()) {
            case OPEN -> open(player);
            case REFRESH -> sendSnapshot(player);
            case TAKE -> menu(player).ifPresent(menu -> take(player, action.stack(), action.amount(), action.flag()));
            case PUT -> menu(player).ifPresent(menu -> putCarried(player, action.flag()));
            case FILL -> menu(player).ifPresent(menu -> fill(player, menu, action.text()));
            case CLEAR -> menu(player).ifPresent(menu -> clear(player, menu));
            case CHAIN_PREVIEW ->
                CraftTerminalChannel.send(
                        player, CraftChain.chain(player, action.text(), action.flag(), action.amount(), false));
            case CHAIN_CRAFT ->
                CraftTerminalChannel.send(
                        player, CraftChain.chain(player, action.text(), action.flag(), action.amount(), true));
            case ORDER -> CraftOrders.place(player, action.text(), action.amount());
            case CANCEL -> CraftOrders.cancel(player, action.amount());
        }
    }

    public static void open(ServerPlayer player) {
        player.openMenu(new SimpleMenuProvider(
                (id, inventory, who) -> new CraftTerminalMenu(id, inventory), Component.translatable(KEY + "title")));
        sendSnapshot(player);
    }

    private static Optional<CraftTerminalMenu> menu(ServerPlayer player) {
        return player.containerMenu instanceof CraftTerminalMenu menu ? Optional.of(menu) : Optional.empty();
    }

    public static Optional<SettlementReach> reach(ServerLevel overworld) {
        SettlementPlotStore store = SettlementPlotStore.get(overworld);
        return store.town()
                .map(plan -> SettlementReach.of(
                        plan, store.site().map(site -> site.ring().posts()).orElse(List.of())));
    }

    public static SettlementReach.Access access(ServerPlayer player) {
        return SettlementReach.access(
                reach(player.server.overworld()),
                Level.OVERWORLD.equals(player.level().dimension()),
                player.getX(),
                player.getZ());
    }

    public static Optional<SettlementStorage> storage(ServerPlayer player) {
        SettlementReach.Access access = access(player);
        if (!access.allowed()) {
            player.displayClientMessage(denial(access), true);
            return Optional.empty();
        }
        ServerLevel overworld = player.server.overworld();
        return reach(overworld).map(found -> SettlementStorage.open(overworld, found));
    }

    public static Component denial(SettlementReach.Access access) {
        return switch (access.verdict()) {
            case ALLOWED -> Component.empty();
            case NO_SETTLEMENT -> Component.translatable(KEY + "denied.no_settlement");
            case OTHER_DIMENSION -> Component.translatable(KEY + "denied.other_dimension");
            case TOO_FAR -> Component.translatable(KEY + "denied.too_far", access.distance(), SettlementReach.MARGIN);
        };
    }

    public static void sendSnapshot(ServerPlayer player) {
        SettlementReach.Access access = access(player);
        List<CraftTerminalChannel.Entry> entries = new ArrayList<>();
        if (access.allowed()) {
            ServerLevel overworld = player.server.overworld();
            reach(overworld).ifPresent(found -> {
                for (SettlementStorage.Kind kind :
                        SettlementStorage.open(overworld, found).kinds()) {
                    entries.add(new CraftTerminalChannel.Entry(kind.sample(), kind.count(), kind.free()));
                }
            });
        }
        CraftTerminalChannel.send(
                player,
                new CraftTerminalChannel.Snapshot(
                        access.verdict(), access.distance(), entries, CraftOrders.view(player.server.overworld())));
    }

    public static ItemStack deposit(ServerPlayer player, ItemStack stack) {
        Optional<SettlementStorage> storage = storage(player);
        if (storage.isEmpty()) {
            return stack;
        }
        ItemStack rest = storage.get().insert(stack);
        sendSnapshot(player);
        return rest;
    }

    public static ItemStack take(ServerPlayer player, ItemStack kind, int amount, boolean toInventory) {
        Optional<SettlementStorage> storage = storage(player);
        if (storage.isEmpty() || kind.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack carried = player.containerMenu.getCarried();
        int room = toInventory || carried.isEmpty()
                ? kind.getMaxStackSize()
                : ItemStack.isSameItemSameComponents(carried, kind) ? kind.getMaxStackSize() - carried.getCount() : 0;
        ItemStack taken = storage.get().extract(kind, Math.min(Math.clamp(amount, 1, MAX_REQUEST), room));
        ItemStack given = taken.copy();
        if (!taken.isEmpty()) {
            if (toInventory) {
                player.getInventory().add(taken);
                storage.get().insert(taken);
                given.shrink(taken.getCount());
            } else if (carried.isEmpty()) {
                player.containerMenu.setCarried(taken);
            } else {
                carried.grow(taken.getCount());
            }
            player.containerMenu.broadcastChanges();
        }
        sendSnapshot(player);
        return given;
    }

    public static void putCarried(ServerPlayer player, boolean single) {
        ItemStack carried = player.containerMenu.getCarried();
        Optional<SettlementStorage> storage = storage(player);
        if (carried.isEmpty() || storage.isEmpty()) {
            return;
        }
        ItemStack part = carried.split(single ? 1 : carried.getCount());
        carried.grow(storage.get().insert(part).getCount());
        player.containerMenu.setCarried(carried);
        player.containerMenu.broadcastChanges();
        sendSnapshot(player);
    }

    static void refill(ServerPlayer player, CraftingContainer grid, List<ItemStack> pattern) {
        if (!access(player).allowed()) {
            return;
        }
        ServerLevel overworld = player.server.overworld();
        reach(overworld).ifPresent(found -> {
            SettlementStorage storage = SettlementStorage.open(overworld, found);
            for (int slot = 0; slot < grid.getContainerSize() && slot < pattern.size(); slot++) {
                ItemStack wanted = pattern.get(slot);
                if (grid.getItem(slot).isEmpty() && !wanted.isEmpty()) {
                    ItemStack part = storage.extract(wanted, 1);
                    if (!part.isEmpty()) {
                        grid.setItem(slot, part);
                    }
                }
            }
        });
        sendSnapshot(player);
    }

    public static void clear(ServerPlayer player, CraftTerminalMenu menu) {
        Optional<SettlementStorage> storage = storage(player);
        for (ItemStack stack : menu.takeGrid()) {
            ItemStack rest = storage.map(found -> found.insert(stack)).orElse(stack);
            give(player, rest, Optional.empty());
        }
        menu.broadcastChanges();
        sendSnapshot(player);
    }

    public static boolean fill(ServerPlayer player, CraftTerminalMenu menu, String recipeId) {
        Optional<RecipeHolder<CraftingRecipe>> holder = RecipeGrid.crafting(player.serverLevel(), recipeId);
        Optional<SettlementStorage> storage = storage(player);
        if (holder.isEmpty() || storage.isEmpty()) {
            return false;
        }
        clear(player, menu);
        List<Optional<Ingredient>> layout = RecipeGrid.layout(holder.get().value());
        List<ItemStack> picked = new ArrayList<>();
        boolean complete = true;
        for (Optional<Ingredient> ingredient : layout) {
            ItemStack part =
                    ingredient.map(wanted -> pick(storage.get(), wanted)).orElse(ItemStack.EMPTY);
            complete &= ingredient.isEmpty() || !part.isEmpty();
            picked.add(part);
        }
        if (!complete) {
            picked.forEach(part -> storage.get().insert(part));
            CraftTerminalChannel.send(player, CraftChain.chain(player, recipeId, true, 1, false));
            sendSnapshot(player);
            return false;
        }
        for (int slot = 0; slot < picked.size(); slot++) {
            menu.grid().setItem(slot, picked.get(slot));
        }
        menu.broadcastChanges();
        sendSnapshot(player);
        return true;
    }

    private static ItemStack pick(SettlementStorage storage, Ingredient ingredient) {
        return storage.kinds().stream()
                .filter(kind -> kind.free() > 0 && ingredient.test(kind.sample()))
                .max(Comparator.comparingInt(SettlementStorage.Kind::free))
                .map(best -> storage.extract(best.sample(), 1))
                .orElse(ItemStack.EMPTY);
    }

    static void give(ServerPlayer player, ItemStack stack, Optional<SettlementStorage> storage) {
        if (stack.isEmpty()) {
            return;
        }
        ItemStack rest = stack.copy();
        player.getInventory().add(rest);
        if (!rest.isEmpty() && storage.isPresent()) {
            rest = storage.get().insert(rest);
        }
        if (!rest.isEmpty()) {
            player.drop(rest, false);
        }
    }
}
