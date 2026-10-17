package com.theminespirit.neoforge.policy;

import com.theminespirit.core.observation.BlockBox;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.policy.UnprotectArea;
import com.theminespirit.core.policy.UnprotectAreas;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

public final class UnprotectAreasData extends SavedData {
    public static final String NAME = "theminespirit_unprotect_areas";

    private static final String AREAS = "Areas";
    private static final String ID = "Id";
    private static final String DIMENSION = "Dimension";
    private static final String MIN_X = "MinX";
    private static final String MIN_Y = "MinY";
    private static final String MIN_Z = "MinZ";
    private static final String MAX_X = "MaxX";
    private static final String MAX_Y = "MaxY";
    private static final String MAX_Z = "MaxZ";
    private static final String GRANTED_BY = "GrantedBy";
    private static final String GRANTED_AT = "GrantedAt";
    private static final String REASON = "Reason";
    private static final String EXPIRES_AT = "ExpiresAt";

    private final UnprotectAreas areas;

    public UnprotectAreasData() {
        this(new UnprotectAreas());
    }

    private UnprotectAreasData(UnprotectAreas areas) {
        this.areas = areas;
    }

    public static SavedData.Factory<UnprotectAreasData> factory() {
        return new SavedData.Factory<>(UnprotectAreasData::new, UnprotectAreasData::load, null);
    }

    public static UnprotectAreasData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(factory(), NAME);
    }

    public static UnprotectAreasData load(CompoundTag tag, HolderLookup.Provider registries) {
        List<UnprotectArea> decoded = new ArrayList<>();
        ListTag stored = tag.getList(AREAS, Tag.TAG_COMPOUND);
        for (int i = 0; i < stored.size(); i++) {
            decoded.add(readArea(stored.getCompound(i)));
        }
        return new UnprotectAreasData(new UnprotectAreas(decoded));
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag stored = new ListTag();
        for (UnprotectArea area : areas.all()) {
            stored.add(writeArea(area));
        }
        tag.put(AREAS, stored);
        return tag;
    }

    public UnprotectArea add(UnprotectArea area, int maxVolume, long nowMillis) {
        areas.add(area, maxVolume, nowMillis);
        setDirty();
        return area;
    }

    public boolean remove(String id) {
        if (areas.remove(id)) {
            setDirty();
            return true;
        }
        return false;
    }

    public List<UnprotectArea> all() {
        return areas.all();
    }

    public boolean covers(String dimension, BlockCoord pos, long nowMillis) {
        return areas.covers(dimension, pos, nowMillis);
    }

    private static CompoundTag writeArea(UnprotectArea area) {
        CompoundTag tag = new CompoundTag();
        tag.putString(ID, area.id());
        tag.putString(DIMENSION, area.dimension());
        tag.putInt(MIN_X, area.box().min().x());
        tag.putInt(MIN_Y, area.box().min().y());
        tag.putInt(MIN_Z, area.box().min().z());
        tag.putInt(MAX_X, area.box().max().x());
        tag.putInt(MAX_Y, area.box().max().y());
        tag.putInt(MAX_Z, area.box().max().z());
        tag.putString(GRANTED_BY, area.grantedBy());
        tag.putLong(GRANTED_AT, area.grantedAtMillis());
        tag.putString(REASON, area.reason());
        tag.putLong(EXPIRES_AT, area.expiresAtMillis());
        return tag;
    }

    private static UnprotectArea readArea(CompoundTag tag) {
        BlockBox box = new BlockBox(
                new BlockCoord(tag.getInt(MIN_X), tag.getInt(MIN_Y), tag.getInt(MIN_Z)),
                new BlockCoord(tag.getInt(MAX_X), tag.getInt(MAX_Y), tag.getInt(MAX_Z)));
        return new UnprotectArea(tag.getString(ID), tag.getString(DIMENSION), box, tag.getString(GRANTED_BY),
                tag.getLong(GRANTED_AT), tag.getString(REASON), tag.getLong(EXPIRES_AT));
    }
}
