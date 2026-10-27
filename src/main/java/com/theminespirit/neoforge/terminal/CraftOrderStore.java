package com.theminespirit.neoforge.terminal;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.crafting.OrderBook;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

public final class CraftOrderStore extends SavedData {
    public static final String NAME = ModIdentity.MOD_ID + "_craft_orders";

    private static final int VERSION = 1;
    private static final String VERSION_KEY = "Version";
    private static final String NEXT_KEY = "Next";
    private static final String ORDERS_KEY = "Orders";
    private static final SavedData.Factory<CraftOrderStore> FACTORY =
            new SavedData.Factory<>(CraftOrderStore::new, CraftOrderStore::load, null);

    private OrderBook book = new OrderBook();

    public CraftOrderStore() {}

    public static CraftOrderStore get(ServerLevel level) {
        Objects.requireNonNull(level, "Уровень не может быть null: очередь заказов живёт в верхнем мире");
        return level.getServer().overworld().getDataStorage().computeIfAbsent(FACTORY, NAME);
    }

    public OrderBook book() {
        return book;
    }

    public void changed() {
        setDirty();
    }

    public static CraftOrderStore load(CompoundTag tag, HolderLookup.Provider registries) {
        int version = tag.getInt(VERSION_KEY);
        if (version != VERSION && tag.contains(ORDERS_KEY, Tag.TAG_LIST)) {
            throw new IllegalStateException("Очередь заказов записана версией " + version + ", поддерживается "
                    + VERSION + ": обновите мод, старую очередь не прочитать");
        }
        List<OrderBook.Order> orders = new ArrayList<>();
        ListTag list = tag.getList(ORDERS_KEY, Tag.TAG_COMPOUND);
        for (int index = 0; index < list.size(); index++) {
            CompoundTag entry = list.getCompound(index);
            Map<String, Integer> missing = new HashMap<>();
            CompoundTag lack = entry.getCompound("Missing");
            for (String item : lack.getAllKeys()) {
                missing.put(item, lack.getInt(item));
            }
            orders.add(new OrderBook.Order(
                    entry.getInt("Id"),
                    entry.getString("Player"),
                    entry.getString("Name"),
                    entry.getString("Recipe"),
                    entry.getString("Item"),
                    entry.getInt("Crafts"),
                    OrderBook.Status.valueOf(entry.getString("Status")),
                    missing,
                    entry.getString("Where")));
        }
        CraftOrderStore store = new CraftOrderStore();
        store.book = OrderBook.of(orders, tag.getInt(NEXT_KEY));
        return store;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt(VERSION_KEY, VERSION);
        tag.putInt(NEXT_KEY, book.nextId());
        ListTag list = new ListTag();
        for (OrderBook.Order order : book.all()) {
            CompoundTag entry = new CompoundTag();
            entry.putInt("Id", order.id());
            entry.putString("Player", order.player());
            entry.putString("Name", order.playerName());
            entry.putString("Recipe", order.recipe());
            entry.putString("Item", order.item());
            entry.putInt("Crafts", order.crafts());
            entry.putString("Status", order.status().name());
            entry.putString("Where", order.where());
            CompoundTag lack = new CompoundTag();
            order.missing().forEach(lack::putInt);
            entry.put("Missing", lack);
            list.add(entry);
        }
        tag.put(ORDERS_KEY, list);
        return tag;
    }
}
