package com.theminespirit.neoforge;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.board.FenceBreaks;
import com.theminespirit.core.observation.BlockCoord;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

public final class FenceLedger extends SavedData {
    private static final String DATA_NAME = ModIdentity.MOD_ID + "_fence_breaks";
    private static final String ENTRIES = "Entries";
    private static final String BLOCK = "Block";
    private static final SavedData.Factory<FenceLedger> FACTORY =
            new SavedData.Factory<>(FenceLedger::new, FenceLedger::load);

    private final FenceBreaks breaks = new FenceBreaks(FenceBreaks.MAX_BREAKS);

    private FenceLedger() {}

    public static FenceLedger get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public void remember(BlockCoord at, String block) {
        breaks.remember(at, block);
        setDirty();
    }

    public Map<BlockCoord, String> retainLive(Predicate<BlockCoord> stillBroken) {
        int before = breaks.size();
        Map<BlockCoord, String> live = breaks.retainLive(stillBroken);
        if (breaks.size() != before) {
            setDirty();
        }
        return live;
    }

    public Map<BlockCoord, String> current() {
        return breaks.current();
    }

    private static FenceLedger load(CompoundTag tag, HolderLookup.Provider ignoredRegistries) {
        FenceLedger ledger = new FenceLedger();
        ListTag entries = tag.getList(ENTRIES, Tag.TAG_COMPOUND);
        for (int index = 0; index < entries.size(); index++) {
            CompoundTag entry = entries.getCompound(index);
            ledger.breaks.remember(
                    new BlockCoord(entry.getInt("X"), entry.getInt("Y"), entry.getInt("Z")), entry.getString(BLOCK));
        }
        return ledger;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag entries = new ListTag();
        for (Map.Entry<BlockCoord, String> entry : breaks.current().entrySet()) {
            Objects.requireNonNull(entry.getValue(), "Блок забора не может быть null: его нечего сохранять");
            CompoundTag item = new CompoundTag();
            item.putInt("X", entry.getKey().x());
            item.putInt("Y", entry.getKey().y());
            item.putInt("Z", entry.getKey().z());
            item.putString(BLOCK, entry.getValue());
            entries.add(item);
        }
        tag.put(ENTRIES, entries);
        return tag;
    }
}
