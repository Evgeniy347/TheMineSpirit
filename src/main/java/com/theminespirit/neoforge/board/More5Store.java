package com.theminespirit.neoforge.board;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.board.graph.More5Rules;
import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

public final class More5Store extends SavedData {
    private static final String DATA_NAME = ModIdentity.MOD_ID + "_more5";
    private static final String BROKEN = "Broken";
    private static final String X = "X";
    private static final String Y = "Y";
    private static final String Z = "Z";
    private static final String BLOCK = "Block";
    private static final String LAST = "Last";
    private static final int MAX_BROKEN = 256;
    private static final SavedData.Factory<More5Store> FACTORY =
            new SavedData.Factory<>(More5Store::new, More5Store::load);

    public record Broken(int x, int y, int z, String block, long lastTick) {
        public BlockCoord position() {
            return new BlockCoord(x, y, z);
        }
    }

    private final Map<String, Broken> broken = new LinkedHashMap<>();

    private More5Store() {}

    public static More5Store get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public void noteBroken(BlockCoord at, String block, long now) {
        broken.put(key(at), new Broken(at.x(), at.y(), at.z(), block, now));
        prune(now);
        setDirty();
    }

    public List<Broken> recentBreaks(long now) {
        prune(now);
        return List.copyOf(broken.values());
    }

    public void forget(BlockCoord at) {
        if (broken.remove(key(at)) != null) {
            setDirty();
        }
    }

    private void prune(long now) {
        long from = now - More5Rules.BROKEN_WINDOW_TICKS;
        broken.values().removeIf(entry -> entry.lastTick() < from);
        while (broken.size() > MAX_BROKEN) {
            broken.remove(broken.keySet().iterator().next());
        }
    }

    private static String key(BlockCoord at) {
        return at.x() + "," + at.y() + "," + at.z();
    }

    private static More5Store load(CompoundTag tag, HolderLookup.Provider ignored) {
        More5Store store = new More5Store();
        for (Tag element : tag.getList(BROKEN, Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) element;
            store.broken.put(
                    entry.getInt(X) + "," + entry.getInt(Y) + "," + entry.getInt(Z),
                    new Broken(
                            entry.getInt(X),
                            entry.getInt(Y),
                            entry.getInt(Z),
                            entry.getString(BLOCK),
                            entry.getLong(LAST)));
        }
        return store;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag known = new ListTag();
        for (Broken entry : new ArrayList<>(broken.values())) {
            CompoundTag row = new CompoundTag();
            row.putInt(X, entry.x());
            row.putInt(Y, entry.y());
            row.putInt(Z, entry.z());
            row.putString(BLOCK, entry.block());
            row.putLong(LAST, entry.lastTick());
            known.add(row);
        }
        tag.put(BROKEN, known);
        return tag;
    }
}
