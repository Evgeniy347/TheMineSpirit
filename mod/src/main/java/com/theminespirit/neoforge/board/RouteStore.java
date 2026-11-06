package com.theminespirit.neoforge.board;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.board.route.RouteMarker;
import com.theminespirit.core.board.route.RoutePlan;
import com.theminespirit.core.board.route.RouteResource;
import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
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
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final SavedData.Factory<RouteStore> FACTORY =
            new SavedData.Factory<>(RouteStore::new, RouteStore::load);

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
        apply(plan.mark(resource, point));
    }

    public void retarget(RouteResource resource, BlockCoord point) {
        apply(plan.retarget(resource, point));
    }

    public void markPathBuilt(RouteResource resource) {
        if (plan.marker(resource).isPresent() && !plan.pathDone(resource)) {
            apply(plan.pathBuilt(resource));
        }
    }

    public void markRouteBuilt(RouteResource resource) {
        if (plan.marker(resource).isPresent() && !plan.routeDone(resource)) {
            apply(plan.routeBuilt(resource));
        }
    }

    public void clear() {
        if (!plan.markers().isEmpty()) {
            plan = RoutePlan.empty();
            setDirty();
        }
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
                markers.add(new RouteMarker(resource, point, entry.getBoolean(PATH), entry.getBoolean(ROUTE)));
            } catch (IllegalArgumentException broken) {
                LOGGER.error(
                        "[route] сохранённая точка {} не читается, маршрут будет найден заново: {}",
                        resource,
                        broken.getMessage());
            }
        }
        store.plan = RoutePlan.of(markers);
        return store;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        for (RouteMarker marker : plan.markers().values()) {
            CompoundTag entry = new CompoundTag();
            entry.putInt(X, marker.point().x());
            entry.putInt(Y, marker.point().y());
            entry.putInt(Z, marker.point().z());
            entry.putBoolean(PATH, marker.pathBuilt());
            entry.putBoolean(ROUTE, marker.routeBuilt());
            tag.put(marker.resource().name(), entry);
        }
        return tag;
    }
}
