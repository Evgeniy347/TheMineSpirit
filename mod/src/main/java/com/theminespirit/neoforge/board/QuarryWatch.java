package com.theminespirit.neoforge.board;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.board.graph.GraphWorldEvents;
import com.theminespirit.core.board.route.QuarryAlerts;
import com.theminespirit.core.board.route.QuarrySites;
import com.theminespirit.core.board.route.QuarryStatus;
import com.theminespirit.core.board.route.RouteMarker;
import com.theminespirit.core.board.route.RouteResource;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.town.StageSite;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

final class QuarryWatch {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final long STATUS_TICKS = 200;

    record Result(
            Set<String> events, Set<String> world, Map<String, BlockCoord> nearest, Map<String, Integer> distances) {
        Result {
            events = Set.copyOf(events);
            world = Set.copyOf(world);
            nearest = Map.copyOf(nearest);
            distances = Map.copyOf(distances);
        }

        static Result empty() {
            return new Result(Set.of(), Set.of(), Map.of(), Map.of());
        }
    }

    private final Map<RouteResource, QuarryStatus> statuses = new EnumMap<>(RouteResource.class);
    private final Map<RouteResource, Long> checked = new EnumMap<>(RouteResource.class);

    Result tick(ServerLevel level, BlockCoord home, long now) {
        RouteStore store = RouteStore.get(level);
        List<RouteMarker> markers = List.copyOf(store.plan().markers().values());
        QuarrySites.Feed feed = QuarrySites.feed(markers, home);
        Set<String> events = new HashSet<>(feed.events());
        if (!store.retired().isEmpty()) {
            events.add(GraphWorldEvents.REMOVE_ROUTE);
        }
        if (markers.isEmpty()) {
            statuses.clear();
            checked.clear();
            return new Result(events, feed.world(), feed.nearest(), feed.distances());
        }
        Optional<StageSite> stage = SettlementPlotStore.liveSite(level);
        for (RouteMarker marker : markers) {
            RouteResource resource = marker.resource();
            long at = checked.getOrDefault(resource, Long.MIN_VALUE);
            if (at != Long.MIN_VALUE && now >= at && now - at < STATUS_TICKS) {
                continue;
            }
            checked.put(resource, now);
            QuarryStatus status = QuarryService.status(level, marker, stage.orElse(null), false);
            if (status == null) {
                continue;
            }
            statuses.put(resource, status);
            if (status.state() == QuarryStatus.State.EMPTY) {
                events.add(resource.depletedEvent());
            }
            if (status.low() && QuarryAlerts.due(now, marker.alertedAt())) {
                QuarryAlerts.message(status).ifPresent(text -> announce(level, store, resource, now, text));
            }
        }
        return new Result(events, feed.world(), feed.nearest(), feed.distances());
    }

    private static void announce(ServerLevel level, RouteStore store, RouteResource resource, long now, String text) {
        store.noteAlerted(resource, now);
        LOGGER.info("[quarry] {}", text);
        for (ServerPlayer player : level.players()) {
            player.sendSystemMessage(Component.literal(text));
        }
    }
}
