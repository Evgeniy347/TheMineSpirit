package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.Board;
import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.route.QuarryStatus;
import com.theminespirit.core.board.route.RouteMarker;
import com.theminespirit.core.board.route.RouteResource;
import com.theminespirit.core.ui.MapMarker;
import com.theminespirit.core.ui.MapQuarry;
import com.theminespirit.core.ui.TownMapView;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

public final class TownMarkers {
    private static final int MARGIN = 48;
    private static final String CASE_KEY = "theminespirit.board.case.";

    private TownMarkers() {}

    public static List<MapMarker> around(ServerLevel level, TownMapView.Bounds bounds) {
        return around(level, bounds, Integer.MIN_VALUE, Integer.MAX_VALUE);
    }

    public static List<MapMarker> around(ServerLevel level, TownMapView.Bounds bounds, int minY, int maxY) {
        TownMapView.Bounds area = new TownMapView.Bounds(
                bounds.x1() - MARGIN, bounds.z1() - MARGIN, bounds.x2() + MARGIN, bounds.z2() + MARGIN);
        Optional<BoardService> service = BoardRegistry.peek(level);
        List<MapMarker> markers = new ArrayList<>();
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof SpiritEntity spirit
                    && !spirit.isRemoved()
                    && spirit.isAlive()
                    && !spirit.isDemo()
                    && inside(area, spirit.getBlockX(), spirit.getBlockZ())
                    && insideY(minY, maxY, spirit.getBlockY())) {
                markers.add(new MapMarker(
                        MapMarker.Kind.SPIRIT,
                        spirit.displayName(),
                        occupation(service, spirit.getUUID()),
                        spirit.getBlockX(),
                        spirit.getBlockZ()));
            }
        }
        for (RouteMarker route : RouteStore.get(level).plan().markers().values()) {
            if (inside(area, route.point().x(), route.point().z())
                    && insideY(minY, maxY, route.point().y())) {
                markers.add(new MapMarker(
                        kind(route.resource()),
                        name(route.resource()),
                        "",
                        route.point().x(),
                        route.point().z()));
            }
        }
        for (ServerPlayer player : level.players()) {
            if (inside(area, player.getBlockX(), player.getBlockZ()) && insideY(minY, maxY, player.getBlockY())) {
                markers.add(new MapMarker(
                        MapMarker.Kind.PLAYER,
                        player.getGameProfile().getName(),
                        "",
                        player.getBlockX(),
                        player.getBlockZ()));
            }
        }
        return List.copyOf(markers);
    }

    private static MapMarker.Kind kind(RouteResource resource) {
        return switch (resource) {
            case DIRT -> MapMarker.Kind.DIRT;
            case SAND -> MapMarker.Kind.SAND;
            case SNOW -> MapMarker.Kind.ROUTE;
        };
    }

    private static String name(RouteResource resource) {
        return switch (resource) {
            case DIRT -> "земля";
            case SAND -> "песок";
            case SNOW -> "снег";
        };
    }

    public static List<MapQuarry> quarries(ServerLevel level) {
        List<MapQuarry> out = new ArrayList<>();
        for (QuarryStatus status : QuarryService.cachedStatuses(level)) {
            String resource = status.resource().name().toLowerCase(Locale.ROOT);
            out.add(
                    status.state() == QuarryStatus.State.UNSET
                            ? MapQuarry.unset(resource)
                            : MapQuarry.of(resource, status.stateName(), status.remainingPercent()));
        }
        return List.copyOf(out);
    }

    private static String occupation(Optional<BoardService> service, UUID spirit) {
        if (service.isEmpty()) {
            return "";
        }
        Board board = service.get().board();
        Optional<BoardCase> job = board.assignedTo(spirit).stream()
                .map(BoardTicket::boardCase)
                .findFirst()
                .or(() -> board.occupation(spirit));
        return job.map(TownMarkers::caseKey).orElse("");
    }

    private static String caseKey(BoardCase boardCase) {
        return CASE_KEY + boardCase.name().toLowerCase(Locale.ROOT);
    }

    private static boolean inside(TownMapView.Bounds bounds, int x, int z) {
        return x >= bounds.x1() && x <= bounds.x2() && z >= bounds.z1() && z <= bounds.z2();
    }

    private static boolean insideY(int minY, int maxY, int y) {
        return y >= minY && y <= maxY;
    }
}
