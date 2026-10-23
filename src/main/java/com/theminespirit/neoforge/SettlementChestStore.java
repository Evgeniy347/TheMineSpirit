package com.theminespirit.neoforge;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.building.SettlementChests;
import com.theminespirit.core.observation.BlockCoord;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Сундуки поселения в данных мира: реестр переживает перезапуск сервера, а установка и снятие сундука меняют его
 * без обхода мира. Начальный реестр пуст; дальше его пополняют события установки и снятия блока.
 */
public final class SettlementChestStore extends SavedData {
    public static final String NAME = ModIdentity.MOD_ID + "_settlement_chests";

    private static final String VERSION_KEY = "Version";
    private static final int VERSION = 1;
    private static final String CHESTS_KEY = "Chests";
    private static final String X_KEY = "X";
    private static final String Y_KEY = "Y";
    private static final String Z_KEY = "Z";
    private static final SavedData.Factory<SettlementChestStore> FACTORY =
            new SavedData.Factory<>(SettlementChestStore::new, SettlementChestStore::load, null);

    private final SettlementChests chests = new SettlementChests();

    public SettlementChestStore() {}

    public static SettlementChestStore get(ServerLevel level) {
        Objects.requireNonNull(level, "Уровень не может быть null: реестр сундуков живёт на уровне");
        return level.getDataStorage().computeIfAbsent(FACTORY, NAME);
    }

    public SettlementChests chests() {
        return chests;
    }

    public boolean track(BlockPos pos) {
        Objects.requireNonNull(pos, "Сундук не может быть null: без координат его не найти");
        boolean added = chests.add(coord(pos));
        if (added) {
            setDirty();
        }
        return added;
    }

    public boolean forget(BlockPos pos) {
        Objects.requireNonNull(pos, "Сундук не может быть null: без координат нечего снимать");
        boolean removed = chests.remove(coord(pos));
        if (removed) {
            setDirty();
        }
        return removed;
    }

    public static SettlementChestStore load(CompoundTag tag, HolderLookup.Provider registries) {
        SettlementChestStore store = new SettlementChestStore();
        int version = tag.getInt(VERSION_KEY);
        if (version != VERSION && tag.contains(CHESTS_KEY, Tag.TAG_LIST)) {
            throw new IllegalStateException("Сундуки поселения записаны версией " + version + ", поддерживается "
                    + VERSION + ": обновите мод, старый реестр не читается");
        }
        ListTag list = tag.getList(CHESTS_KEY, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            store.chests.add(new BlockCoord(entry.getInt(X_KEY), entry.getInt(Y_KEY), entry.getInt(Z_KEY)));
        }
        store.setDirty(false);
        return store;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt(VERSION_KEY, VERSION);
        ListTag list = new ListTag();
        for (BlockCoord pos : chests.all()) {
            CompoundTag entry = new CompoundTag();
            entry.putInt(X_KEY, pos.x());
            entry.putInt(Y_KEY, pos.y());
            entry.putInt(Z_KEY, pos.z());
            list.add(entry);
        }
        tag.put(CHESTS_KEY, list);
        return tag;
    }

    private static BlockCoord coord(BlockPos pos) {
        return new BlockCoord(pos.getX(), pos.getY(), pos.getZ());
    }
}
