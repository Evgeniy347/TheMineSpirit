package com.theminespirit.neoforge.terminal;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.crafting.ChainCraft;
import com.theminespirit.core.crafting.ChainPlan;
import com.theminespirit.core.crafting.OrderBook;
import com.theminespirit.core.crafting.RecipeSpec;
import com.theminespirit.core.town.SettlementReach;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.crafting.ItemIds;
import com.theminespirit.neoforge.crafting.RecipeKnowledge;
import com.theminespirit.neoforge.crafting.RecipeSource;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

public final class CraftOrders {
    public static final String EVENT = "player_craft_order";
    public static final String WAITING = "waiting";
    public static final String CANCELLED = "cancelled";
    public static final String INVENTORY = "inventory";
    private static final String KEY = ModIdentity.MOD_ID + ".order.";

    private CraftOrders() {}

    static void place(ServerPlayer player, String recipeId, int crafts) {
        Optional<SettlementStorage> storage = CraftTerminalService.storage(player);
        ServerLevel level = player.serverLevel();
        Optional<RecipeSpec> spec = RecipeGrid.crafting(level, recipeId)
                .flatMap(holder -> RecipeSource.crafting(holder, level.registryAccess()));
        if (storage.isEmpty() || spec.isEmpty()) {
            return;
        }
        int count = Math.clamp(crafts, 1, CraftTerminalService.MAX_REQUEST);
        ChainPlan plan = ChainCraft.ofRecipe(
                RecipeKnowledge.of(level).index(),
                spec.get(),
                count,
                storage.get().freePlain());
        CraftOrderStore store = CraftOrderStore.get(level);
        Optional<OrderBook.Order> placed = store.book()
                .place(
                        player.getUUID().toString(),
                        player.getGameProfile().getName(),
                        recipeId,
                        spec.get().outputItem(),
                        count,
                        plan.missing());
        store.changed();
        player.displayClientMessage(
                Component.translatable(
                        placed.isPresent() ? KEY + "placed" : KEY + "limit", OrderBook.MAX_WAITING_PER_PLAYER),
                true);
        CraftTerminalService.sendSnapshot(player);
    }

    static void cancel(ServerPlayer player, int id) {
        CraftOrderStore store = CraftOrderStore.get(player.serverLevel());
        if (store.book().cancel(id, player.getUUID().toString())) {
            store.changed();
        }
        CraftTerminalService.sendSnapshot(player);
    }

    public static boolean waiting(ServerLevel level) {
        return !CraftOrderStore.get(level).book().waiting().isEmpty();
    }

    public static Map<String, Integer> shortage(ServerLevel level) {
        return CraftOrderStore.get(level).book().shortage();
    }

    public static int refresh(ServerLevel level) {
        ServerLevel overworld = level.getServer().overworld();
        Optional<SettlementReach> reach = CraftTerminalService.reach(overworld);
        CraftOrderStore store = CraftOrderStore.get(overworld);
        if (reach.isEmpty() || store.book().waiting().isEmpty()) {
            return 0;
        }
        int done = 0;
        for (OrderBook.Order order : store.book().waiting()) {
            SettlementStorage storage = SettlementStorage.open(overworld, reach.get());
            Optional<RecipeSpec> spec = RecipeGrid.crafting(overworld, order.recipe())
                    .flatMap(holder -> RecipeSource.crafting(holder, overworld.registryAccess()));
            if (spec.isEmpty()) {
                continue;
            }
            ChainPlan plan = ChainCraft.ofRecipe(
                    RecipeKnowledge.of(overworld).index(), spec.get(), order.crafts(), storage.freePlain());
            Optional<SpiritEntity> crafter = crafter(overworld, reach.get());
            if (!plan.feasible() || crafter.isEmpty()) {
                store.book().update(order.id(), plan.missing());
                continue;
            }
            if (ChainExecutor.run(overworld, storage, plan) instanceof ChainExecutor.Done crafted) {
                String where = deliver(overworld, storage, order, crafted, crafter.get());
                store.book().finish(order.id(), where);
                report(overworld, order, plan, crafter.get(), where);
                done++;
            }
        }
        store.changed();
        return done;
    }

    private static Optional<SpiritEntity> crafter(ServerLevel level, SettlementReach reach) {
        AABB box = new AABB(
                reach.minX(),
                level.getMinBuildHeight(),
                reach.minZ(),
                reach.maxX() + 1,
                level.getMaxBuildHeight(),
                reach.maxZ() + 1);
        return level.getEntities(ModEntities.SPIRIT.get(), box, Entity::isAlive).stream()
                .min(Comparator.comparing(spirit -> spirit.getUUID().toString()));
    }

    private static String deliver(
            ServerLevel level,
            SettlementStorage storage,
            OrderBook.Order order,
            ChainExecutor.Done crafted,
            SpiritEntity crafter) {
        crafted.extras().forEach(stack -> stash(storage, stack, crafter));
        Optional<ServerPlayer> owner = owner(level, order);
        if (owner.isPresent() && CraftTerminalService.access(owner.get()).allowed()) {
            crafted.targets().forEach(stack -> CraftTerminalService.give(owner.get(), stack, Optional.of(storage)));
            return INVENTORY;
        }
        Optional<BlockPos> at = Optional.empty();
        for (ItemStack stack : crafted.targets()) {
            SettlementStorage.Stored stored = storage.store(stack);
            at = at.or(stored::at);
            if (!stored.rest().isEmpty()) {
                crafter.spawnAtLocation(stored.rest());
                at = at.or(() -> Optional.of(crafter.blockPosition()));
            }
        }
        BlockPos pos = at.orElse(crafter.blockPosition());
        return pos.getX() + " " + pos.getY() + " " + pos.getZ();
    }

    private static void stash(SettlementStorage storage, ItemStack stack, SpiritEntity crafter) {
        ItemStack rest = storage.insert(stack);
        if (!rest.isEmpty()) {
            crafter.spawnAtLocation(rest);
        }
    }

    private static Optional<ServerPlayer> owner(ServerLevel level, OrderBook.Order order) {
        try {
            return Optional.ofNullable(level.getServer().getPlayerList().getPlayer(UUID.fromString(order.player())));
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                    "Заказ " + order.id() + " хранит не UUID игрока '" + order.player()
                            + "': очередь заказов повреждена",
                    e);
        }
    }

    private static void report(
            ServerLevel level, OrderBook.Order order, ChainPlan plan, SpiritEntity crafter, String where) {
        Component item = ItemIds.item(order.item())
                .map(found -> new ItemStack(found).getHoverName())
                .orElse(Component.literal(order.item()));
        Component place = INVENTORY.equals(where)
                ? Component.translatable(KEY + "where.inventory")
                : Component.translatable(KEY + "where.chest", where);
        Component message =
                Component.translatable(KEY + "ready", crafter.getDisplayName(), plan.targetCount(), item, place);
        owner(level, order).ifPresent(player -> player.sendSystemMessage(message));
    }

    static List<CraftTerminalChannel.Order> view(ServerLevel level) {
        List<CraftTerminalChannel.Order> orders = new ArrayList<>();
        for (OrderBook.Order order : CraftOrderStore.get(level).book().all()) {
            Optional<ItemStack> target = ItemIds.item(order.item()).map(ItemStack::new);
            if (target.isEmpty()) {
                continue;
            }
            String status = switch (order.status()) {
                case WAITING -> WAITING;
                case CANCELLED -> CANCELLED;
                case DONE -> order.where();
            };
            orders.add(new CraftTerminalChannel.Order(
                    order.id(), target.get(), order.crafts(), status, CraftTerminalService.lines(order.missing())));
        }
        return orders;
    }
}
