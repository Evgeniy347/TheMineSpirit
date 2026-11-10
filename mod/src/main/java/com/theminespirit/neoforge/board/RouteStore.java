package com.theminespirit.neoforge.board;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.board.route.QuarryArea;
import com.theminespirit.core.board.route.QuarrySite;
import com.theminespirit.core.board.route.RouteMarker;
import com.theminespirit.core.board.route.RoutePlan;
import com.theminespirit.core.board.route.RouteResource;
import com.theminespirit.core.board.route.RouteRetired;
import com.theminespirit.core.board.route.RouteTrail;
import com.theminespirit.core.board.route.RouteTrailBook;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.town.PlotLeveling;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.slf4j.Logger;

public final class RouteStore extends SavedData {
    private static final String DATA_NAME = ModIdentity.MOD_ID + "_routes";
    private static final String PATH = "Path";
    private static final String ROUTE = "Route";
    private static final String X = "X";
    private static final String Y = "Y";
    private static final String Z = "Z";
    private static final String RADIUS = "Radius";
    private static final String BIOME = "Biome";
    private static final String RESERVE = "Reserve";
    private static final String ALERT = "Alert";
    private static final String TRAIL = "Trail";
    private static final String TRAIL_BLOCK = "TrailBlock";
    private static final String PLANNED = "Planned";
    private static final String PLANNED_BLOCK = "PlannedBlock";
    private static final String RETIRED = "Retired";
    private static final String RETIRED_ID = "Id";
    private static final String RETIRED_RESOURCE = "Resource";
    private static final String NEXT_RETIRE = "NextRetire";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final SavedData.Factory<RouteStore> FACTORY =
            new SavedData.Factory<>(RouteStore::new, RouteStore::load);

    private final RouteTrailBook book = new RouteTrailBook();
    private RoutePlan plan = RoutePlan.empty();

    private RouteStore() {}

    public static RouteStore get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public RoutePlan plan() {
        return plan;
    }

    public Optional<BlockCoord> point(RouteResource resource) {
        return plan.point(resource);
    }

    public void apply(RoutePlan next) {
        if (!next.equals(plan)) {
            plan = next;
            setDirty();
        }
    }

    public void mark(RouteResource resource, BlockCoord point) {
        RoutePlan next = plan.mark(resource, point);
        if (next == plan) {
            return;
        }
        book.retire(resource);
        apply(next);
    }

    public void mark(QuarrySite site) {
        RoutePlan next = plan.mark(site);
        if (next == plan) {
            return;
        }
        book.retire(site.resource());
        apply(next);
    }

    public void clear(RouteResource resource) {
        book.retire(resource);
        apply(plan.unmark(resource));
    }

    public void noteAlerted(RouteResource resource, long tick) {
        if (plan.marker(resource).isPresent()) {
            apply(plan.alerted(resource, tick));
        }
    }

    public void noteTrail(RouteResource resource, PlotLeveling path) {
        book.plan(resource, path);
        setDirty();
    }

    public void markPathBuilt(RouteResource resource) {
        if (plan.marker(resource).isPresent() && !plan.pathDone(resource)) {
            book.built(resource);
            apply(plan.pathBuilt(resource));
            setDirty();
        }
    }

    public void markRouteBuilt(RouteResource resource) {
        if (plan.marker(resource).isPresent() && !plan.routeDone(resource)) {
            apply(plan.routeBuilt(resource));
        }
    }

    public List<RouteRetired> retired() {
        return book.retired();
    }

    public void retireDone(long id) {
        if (book.done(id)) {
            setDirty();
        }
    }

    public void clear() {
        plan = RoutePlan.empty();
        book.clear();
        setDirty();
    }

    private static RouteStore load(CompoundTag tag, HolderLookup.Provider ignoredRegistries) {
        RouteStore store = new RouteStore();
        List<RouteMarker> markers = new ArrayList<>();
        for (RouteResource resource : RouteResource.values()) {
            if (!tag.contains(resource.name())) {
                continue;
            }
            CompoundTag entry = tag.getCompound(resource.name());
            try {
                BlockCoord point = new BlockCoord(entry.getInt(X), entry.getInt(Y), entry.getInt(Z));
                int radius = entry.contains(RADIUS) ? entry.getInt(RADIUS) : QuarryArea.DEFAULT_RADIUS;
                markers.add(new RouteMarker(
                        resource,
                        point,
                        entry.getBoolean(PATH),
                        entry.getBoolean(ROUTE),
                        radius,
                        entry.getString(BIOME),
                        entry.getInt(RESERVE),
                        entry.getLong(ALERT)));
            } catch (IllegalArgumentException broken) {
                LOGGER.error(
                        "[route] сохранённая точка {} не читается, маршрут будет найден заново: {}",
                        resource,
                        broken.getMessage());
            }
            RouteTrail trail = RouteTrailNbt.read(entry, TRAIL, TRAIL_BLOCK);
            if (trail != null) {
                store.book.restore(resource, trail, true);
            }
            RouteTrail pending = RouteTrailNbt.read(entry, PLANNED, PLANNED_BLOCK);
            if (pending != null) {
                store.book.restore(resource, pending, false);
            }
        }
        store.plan = RoutePlan.of(markers);
        loadRetired(tag, store);
        return store;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        Map<RouteResource, RouteTrail> trails = book.trails();
        Map<RouteResource, RouteTrail> planned = book.planned();
        for (RouteMarker marker : plan.markers().values()) {
            CompoundTag entry = new CompoundTag();
            entry.putInt(X, marker.point().x());
            entry.putInt(Y, marker.point().y());
            entry.putInt(Z, marker.point().z());
            entry.putBoolean(PATH, marker.pathBuilt());
            entry.putBoolean(ROUTE, marker.routeBuilt());
            entry.putInt(RADIUS, marker.radius());
            entry.putString(BIOME, marker.biome());
            entry.putInt(RESERVE, marker.initialReserve());
            entry.putLong(ALERT, marker.alertedAt());
            RouteTrail trail = trails.get(marker.resource());
            if (trail != null) {
                RouteTrailNbt.write(entry, TRAIL, TRAIL_BLOCK, trail);
            }
            RouteTrail pending = planned.get(marker.resource());
            if (pending != null) {
                RouteTrailNbt.write(entry, PLANNED, PLANNED_BLOCK, pending);
            }
            tag.put(marker.resource().name(), entry);
        }
        List<RouteRetired> retired = book.retired();
        if (!retired.isEmpty()) {
            ListTag list = new ListTag();
            for (RouteRetired stale : retired) {
                CompoundTag item = new CompoundTag();
                item.putLong(RETIRED_ID, stale.id());
                item.putString(RETIRED_RESOURCE, stale.resource().name());
                RouteTrailNbt.write(item, TRAIL, TRAIL_BLOCK, stale.trail());
                list.add(item);
            }
            tag.put(RETIRED, list);
        }
        tag.putLong(NEXT_RETIRE, book.nextRetire());
        return tag;
    }

    private static void loadRetired(CompoundTag tag, RouteStore store) {
        ListTag list = tag.getList(RETIRED, Tag.TAG_COMPOUND);
        for (int index = 0; index < list.size(); index++) {
            CompoundTag item = list.getCompound(index);
            try {
                RouteResource resource = RouteResource.valueOf(item.getString(RETIRED_RESOURCE));
                RouteTrail trail = RouteTrailNbt.read(item, TRAIL, TRAIL_BLOCK);
                if (trail == null || trail.empty()) {
                    throw new IllegalArgumentException("пустая тропа снятия");
                }
                long id = item.getLong(RETIRED_ID);
                store.book.restore(new RouteRetired(id, resource, trail));
            } catch (IllegalArgumentException broken) {
                LOGGER.error(
                        "[route] сохранённое снятие тропы не читается, старый след останется: {}", broken.getMessage());
            }
        }
        store.book.nextRetire(tag.getLong(NEXT_RETIRE));
    }
}
