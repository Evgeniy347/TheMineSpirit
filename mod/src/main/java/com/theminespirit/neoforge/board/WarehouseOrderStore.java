package com.theminespirit.neoforge.board;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.board.WarehouseOrder;
import com.theminespirit.core.observation.BlockCoord;
import java.util.Objects;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.jspecify.annotations.Nullable;

public final class WarehouseOrderStore extends SavedData {
    public static final String NAME = ModIdentity.MOD_ID + "_warehouse_order";
    private static final String VERSION_KEY = "Version";
    private static final int VERSION = 1;
    private static final String LEVEL_KEY = "Level";
    private static final String X_KEY = "X";
    private static final String Y_KEY = "Y";
    private static final String Z_KEY = "Z";
    private static final String BY_KEY = "By";
    private static final String REASON_KEY = "Reason";
    private static final String TICK_KEY = "Tick";
    private static final SavedData.Factory<WarehouseOrderStore> FACTORY =
            new SavedData.Factory<>(WarehouseOrderStore::new, WarehouseOrderStore::load, null);

    private @Nullable WarehouseOrder order;

    public WarehouseOrderStore() {}

    public static WarehouseOrderStore get(ServerLevel level) {
        Objects.requireNonNull(level, "Уровень не может быть null: решение о складе живёт в мире");
        return level.getDataStorage().computeIfAbsent(FACTORY, NAME);
    }

    public @Nullable WarehouseOrder order() {
        return order;
    }

    public void order(WarehouseOrder value) {
        order = Objects.requireNonNull(value, "Решение о складе не может быть null: пустое снимают clear()");
        setDirty();
    }

    public void clear() {
        if (order != null) {
            order = null;
            setDirty();
        }
    }

    public static WarehouseOrderStore load(CompoundTag tag, HolderLookup.Provider registries) {
        WarehouseOrderStore store = new WarehouseOrderStore();
        int version = tag.getInt(VERSION_KEY);
        if (version != VERSION) {
            throw new IllegalStateException("Решение о складе записано версией " + version + ", поддерживается "
                    + VERSION + ": обновите мод, старое решение не читается");
        }
        if (!tag.contains(LEVEL_KEY)) {
            return store;
        }
        store.order = new WarehouseOrder(
                tag.getInt(LEVEL_KEY),
                new BlockCoord(tag.getInt(X_KEY), tag.getInt(Y_KEY), tag.getInt(Z_KEY)),
                tag.getString(BY_KEY),
                tag.getString(REASON_KEY),
                tag.getLong(TICK_KEY));
        return store;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt(VERSION_KEY, VERSION);
        if (order != null) {
            tag.putInt(LEVEL_KEY, order.level());
            tag.putInt(X_KEY, order.site().x());
            tag.putInt(Y_KEY, order.site().y());
            tag.putInt(Z_KEY, order.site().z());
            tag.putString(BY_KEY, order.by());
            tag.putString(REASON_KEY, order.reason());
            tag.putLong(TICK_KEY, order.tick());
        }
        return tag;
    }
}
