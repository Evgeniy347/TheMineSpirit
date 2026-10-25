package com.theminespirit.neoforge.movement;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.building.BuildBox;
import com.theminespirit.core.movement.network.NetworkAreas;
import com.theminespirit.core.movement.network.NetworkBuilder;
import com.theminespirit.core.movement.network.NetworkRoute;
import com.theminespirit.core.movement.network.PathNetwork;
import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import org.slf4j.Logger;

public final class PathNetworkService {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<ServerLevel, LevelNetwork> BY_LEVEL = new ConcurrentHashMap<>();

    private PathNetworkService() {}

    public static void register(IEventBus bus) {
        Objects.requireNonNull(bus, "Шина не может быть null: без неё сеть не узнает об изменении блоков");
        bus.addListener(PathNetworkService::onNeighborNotify);
        bus.addListener(PathNetworkService::onUnload);
    }

    public static Optional<List<BlockCoord>> route(ServerLevel level, BlockCoord feet, BlockCoord target) {
        return BY_LEVEL.computeIfAbsent(level, key -> new LevelNetwork()).route(level, feet, target);
    }

    public static void legFailed(ServerLevel level, BlockCoord from, BlockCoord to) {
        LevelNetwork network = BY_LEVEL.get(level);
        if (network != null) {
            network.legFailed(from, to);
        }
    }

    public static void beginSite(ServerLevel level, Object building, BuildBox box, List<BlockCoord> entrances) {
        LevelNetwork network = BY_LEVEL.computeIfAbsent(level, key -> new LevelNetwork());
        network.sites.computeIfAbsent(building, key -> network.areas.beginSite(box, entrances));
    }

    public static void endSite(ServerLevel level, Object building) {
        LevelNetwork network = BY_LEVEL.get(level);
        if (network == null) {
            return;
        }
        Integer site = network.sites.remove(building);
        if (site != null && network.areas.exists(site)) {
            network.areas.endSite(site);
        }
    }

    private static void onNeighborNotify(BlockEvent.NeighborNotifyEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        LevelNetwork network = BY_LEVEL.get(level);
        if (network != null) {
            BlockPos pos = event.getPos();
            network.blockChanged(new BlockCoord(pos.getX(), pos.getY(), pos.getZ()));
        }
    }

    private static void onUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            BY_LEVEL.remove(level);
        }
    }

    private static final class LevelNetwork {
        private final PathNetwork network = new PathNetwork();
        private final NetworkAreas areas = new NetworkAreas();
        private final Map<Object, Integer> sites = new HashMap<>();

        Optional<List<BlockCoord>> route(ServerLevel level, BlockCoord feet, BlockCoord target) {
            NetworkLevelView view = new NetworkLevelView(level);
            long now = level.getGameTime();
            for (int area : areas.prepare(feet, target, now)) {
                rebuild(level, view, area, now);
            }
            network.removeAll(areas.takeSwallowed());
            repairDirty(view);
            Optional<List<BlockCoord>> route = NetworkRoute.plan(network, feet, target, view);
            if (route.isEmpty()) {
                areas.routeFailed(feet, target);
            }
            return route;
        }

        void blockChanged(BlockCoord block) {
            network.blockChanged(block);
            areas.blockPlaced(block);
        }

        void legFailed(BlockCoord from, BlockCoord to) {
            Set<Integer> shared = new HashSet<>(network.segmentsAt(from));
            shared.retainAll(network.segmentsAt(to));
            for (int segment : shared) {
                network.remove(segment);
                areas.segmentLost(segment);
            }
        }

        private void rebuild(ServerLevel level, NetworkLevelView view, int area, long now) {
            BuildBox box = areas.box(area);
            List<BlockCoord> anchors = new ArrayList<>(areas.entrances(area));
            anchors.addAll(doors(level, box));
            Set<Integer> added = new HashSet<>();
            for (List<BlockCoord> chain : NetworkBuilder.chains(anchors, box, view)) {
                added.add(network.add(chain));
            }
            network.removeAll(areas.rebuilt(area, added, now));
            LOGGER.debug(
                    "Сеть тропинок: участок {} {} — начал {}, отрезков {}", area, box, anchors.size(), added.size());
        }

        private void repairDirty(NetworkLevelView view) {
            for (int segment : network.dirty()) {
                Optional<List<BlockCoord>> repaired = NetworkRoute.repair(network.segment(segment), view);
                if (repaired.isPresent()) {
                    network.replace(segment, repaired.get());
                } else {
                    network.remove(segment);
                    areas.segmentLost(segment);
                }
            }
        }

        private static List<BlockCoord> doors(ServerLevel level, BuildBox box) {
            List<BlockCoord> doors = new ArrayList<>();
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            for (int x = box.minX(); x <= box.maxX(); x++) {
                for (int z = box.minZ(); z <= box.maxZ(); z++) {
                    for (int y = box.minY(); y <= box.maxY(); y++) {
                        BlockState state = level.getBlockState(pos.set(x, y, z));
                        if (state.getBlock() instanceof DoorBlock
                                && state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER) {
                            doors.add(new BlockCoord(x, y, z));
                        }
                    }
                }
            }
            return doors;
        }
    }
}
