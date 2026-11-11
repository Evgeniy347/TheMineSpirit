package com.theminespirit.neoforge.board;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.board.graph.More3Rules;
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

public final class More3Store extends SavedData {
    private static final String DATA_NAME = ModIdentity.MOD_ID + "_more3";
    private static final String STAGE = "Stage";
    private static final String STAGE_START = "StageStart";
    private static final String DANGERS = "Dangers";
    private static final String DROPS = "Drops";
    private static final String X = "X";
    private static final String Y = "Y";
    private static final String Z = "Z";
    private static final String KIND = "Kind";
    private static final String HITS = "Hits";
    private static final String DEATHS = "Deaths";
    private static final String LAST = "Last";
    private static final String EXPIRES = "Expires";
    private static final int MAX_DANGERS = 256;
    private static final int MAX_DROPS = 64;
    private static final SavedData.Factory<More3Store> FACTORY =
            new SavedData.Factory<>(More3Store::new, More3Store::load);

    public record Danger(int x, int baseY, int z, String kind, int hits, int deaths, long lastTick) {
        public BlockCoord position() {
            return new BlockCoord(x, baseY, z);
        }
    }

    public record DeathDrop(int x, int y, int z, long expiresAt) {
        public BlockCoord position() {
            return new BlockCoord(x, y, z);
        }
    }

    private int stage;
    private long stageStartedAt;
    private boolean columnsMigrated;
    private final Map<String, Danger> dangers = new LinkedHashMap<>();
    private final List<DeathDrop> drops = new ArrayList<>();

    More3Store() {}

    public static More3Store get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public int stage() {
        return stage;
    }

    public long stageStartedAt() {
        return stageStartedAt;
    }

    public void noteStage(int next, long now) {
        if (next < 1) {
            throw new IllegalArgumentException("Этап " + next + " меньше 1: наблюдение начинается с первого этапа");
        }
        if (next != stage) {
            stage = next;
            stageStartedAt = now;
            setDirty();
        }
    }

    public void noteDamage(BlockCoord at, String kind, long now) {
        String key = key(at);
        Danger before = dangers.get(key);
        Danger next = new Danger(
                at.x(),
                at.y(),
                at.z(),
                kind,
                before == null ? 1 : before.hits() + 1,
                before == null ? 0 : before.deaths(),
                now);
        dangers.put(key, next);
        prune(now);
        setDirty();
    }

    public void noteDeath(BlockCoord danger, BlockCoord drop, long now) {
        noteDamage(danger, "death", now);
        String key = key(danger);
        Danger before = dangers.get(key);
        dangers.put(
                key,
                new Danger(
                        danger.x(),
                        danger.y(),
                        danger.z(),
                        "death",
                        before == null ? 1 : before.hits(),
                        (before == null ? 0 : before.deaths()) + 1,
                        now));
        drops.add(new DeathDrop(drop.x(), drop.y(), drop.z(), now + More3Rules.DROP_LIFETIME_TICKS));
        while (drops.size() > MAX_DROPS) {
            drops.remove(0);
        }
        setDirty();
    }

    public void migrateColumns(int baseY) {
        if (columnsMigrated) {
            return;
        }
        columnsMigrated = true;
        boolean changed = false;
        for (Map.Entry<String, Danger> entry : List.copyOf(dangers.entrySet())) {
            Danger danger = entry.getValue();
            if (danger.baseY() == baseY) {
                continue;
            }
            dangers.put(
                    entry.getKey(),
                    new Danger(
                            danger.x(),
                            baseY,
                            danger.z(),
                            danger.kind(),
                            danger.hits(),
                            danger.deaths(),
                            danger.lastTick()));
            changed = true;
        }
        if (changed) {
            setDirty();
        }
    }

    public List<Danger> recentDangers(long now) {
        long from = now - More3Rules.DANGER_WINDOW_TICKS;
        return dangers.values().stream()
                .filter(danger -> danger.lastTick() >= from)
                .toList();
    }

    public List<DeathDrop> activeDrops(long now) {
        drops.removeIf(drop -> drop.expiresAt() <= now);
        return List.copyOf(drops);
    }

    public void forgetDrop(DeathDrop drop) {
        if (drops.remove(drop)) {
            setDirty();
        }
    }

    private void prune(long now) {
        long from = now - More3Rules.DANGER_WINDOW_TICKS;
        dangers.values().removeIf(danger -> danger.lastTick() < from);
        while (dangers.size() > MAX_DANGERS) {
            dangers.remove(dangers.keySet().iterator().next());
        }
    }

    private static String key(BlockCoord at) {
        return at.x() + "," + at.y() + "," + at.z();
    }

    static More3Store load(CompoundTag tag, HolderLookup.Provider ignored) {
        More3Store store = new More3Store();
        store.stage = tag.getInt(STAGE);
        store.stageStartedAt = tag.getLong(STAGE_START);
        for (Tag element : tag.getList(DANGERS, Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) element;
            store.dangers.put(
                    entry.getInt(X) + "," + entry.getInt(Y) + "," + entry.getInt(Z),
                    new Danger(
                            entry.getInt(X),
                            entry.getInt(Y),
                            entry.getInt(Z),
                            entry.getString(KIND),
                            entry.getInt(HITS),
                            entry.getInt(DEATHS),
                            entry.getLong(LAST)));
        }
        for (Tag element : tag.getList(DROPS, Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) element;
            store.drops.add(new DeathDrop(entry.getInt(X), entry.getInt(Y), entry.getInt(Z), entry.getLong(EXPIRES)));
        }
        return store;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt(STAGE, stage);
        tag.putLong(STAGE_START, stageStartedAt);
        ListTag known = new ListTag();
        for (Danger danger : dangers.values()) {
            CompoundTag entry = new CompoundTag();
            entry.putInt(X, danger.x());
            entry.putInt(Y, danger.baseY());
            entry.putInt(Z, danger.z());
            entry.putString(KIND, danger.kind());
            entry.putInt(HITS, danger.hits());
            entry.putInt(DEATHS, danger.deaths());
            entry.putLong(LAST, danger.lastTick());
            known.add(entry);
        }
        tag.put(DANGERS, known);
        ListTag found = new ListTag();
        for (DeathDrop drop : drops) {
            CompoundTag entry = new CompoundTag();
            entry.putInt(X, drop.x());
            entry.putInt(Y, drop.y());
            entry.putInt(Z, drop.z());
            entry.putLong(EXPIRES, drop.expiresAt());
            found.add(entry);
        }
        tag.put(DROPS, found);
        return tag;
    }
}
