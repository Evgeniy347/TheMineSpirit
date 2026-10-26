package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.Board;
import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.ui.MapMarker;
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
        TownMapView.Bounds area = new TownMapView.Bounds(
                bounds.x1() - MARGIN, bounds.z1() - MARGIN, bounds.x2() + MARGIN, bounds.z2() + MARGIN);
        Optional<BoardService> service = BoardRegistry.peek(level);
        List<MapMarker> markers = new ArrayList<>();
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof SpiritEntity spirit
                    && !spirit.isRemoved()
                    && spirit.isAlive()
                    && !spirit.isDemo()
                    && inside(area, spirit.getBlockX(), spirit.getBlockZ())) {
                markers.add(new MapMarker(
                        MapMarker.Kind.SPIRIT,
                        spirit.displayName(),
                        occupation(service, spirit.getUUID()),
                        spirit.getBlockX(),
                        spirit.getBlockZ()));
            }
        }
        for (ServerPlayer player : level.players()) {
            if (inside(area, player.getBlockX(), player.getBlockZ())) {
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
}
