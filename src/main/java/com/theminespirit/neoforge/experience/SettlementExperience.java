package com.theminespirit.neoforge.experience;

import com.theminespirit.core.ModIdentity;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Пул опыта поселения: опыт духов, накопленный, пока ни один реальный игрок не в сети.
 *
 * <p>Копится на уровне Верхнего мира (одна запись на сервер), раздаётся при входе игрока. Так опыт
 * не теряется, если дух убил моба или добыл руду на пустом сервере.
 */
public final class SettlementExperience extends SavedData {
    private static final String DATA_NAME = ModIdentity.MOD_ID + "_settlement_experience";
    private static final String PENDING_KEY = "Pending";
    private static final SavedData.Factory<SettlementExperience> FACTORY =
            new SavedData.Factory<>(SettlementExperience::new, SettlementExperience::load);

    private int pending;

    private SettlementExperience() {
    }

    public static SettlementExperience get(MinecraftServer server) {
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) {
            throw new IllegalStateException("Уровень Верхнего мира не найден: пул опыта поселения хранится там, "
                    + "а его нет у сервера");
        }
        return overworld.getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    private static SettlementExperience load(CompoundTag tag, HolderLookup.Provider registries) {
        SettlementExperience data = new SettlementExperience();
        data.pending = tag.getInt(PENDING_KEY);
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt(PENDING_KEY, pending);
        return tag;
    }

    public int pending() {
        return pending;
    }

    public int add(int amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("Опыт " + amount + " отрицательный: в пул кладут только "
                    + "положительный опыт");
        }
        if (amount > 0) {
            pending += amount;
            setDirty();
        }
        return pending;
    }

    public int drain() {
        int drained = pending;
        if (drained > 0) {
            pending = 0;
            setDirty();
        }
        return drained;
    }
}
