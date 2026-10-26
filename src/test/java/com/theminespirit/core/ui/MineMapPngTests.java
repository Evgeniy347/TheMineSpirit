package com.theminespirit.core.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class MineMapPngTests {
    @Test
    void render_SliceAtTunnel_ShowsOreFluidsAndPlans() throws IOException {
        MineSnapshot snapshot = MineMapPng.marked(2);
        MapViewport viewport = MapViewport.fit(snapshot.bounds(), 0, 0, MineMapPng.WIDTH, MineMapPng.HEIGHT);
        List<MineSnapshot.Kind> kinds =
                snapshot.cells().stream().map(MineSnapshot.Cell::kind).toList();
        assertTrue(kinds.contains(MineSnapshot.Kind.PASSAGE), "В срезе есть проходы");
        assertTrue(kinds.contains(MineSnapshot.Kind.DESCENT), "В срезе есть спуск");
        assertTrue(kinds.contains(MineSnapshot.Kind.ORE), "В срезе есть оставленная руда");
        assertTrue(kinds.contains(MineSnapshot.Kind.LUCK_ORE), "Руда на удачу показана отдельным цветом");
        assertTrue(kinds.contains(MineSnapshot.Kind.WATER), "Вода показана цветом");
        assertTrue(kinds.contains(MineSnapshot.Kind.LAVA), "Лава показана цветом");
        MineSnapshot.Rect tunnel = snapshot.rects().stream()
                .filter(rect -> MineSnapshot.TUNNEL.equals(rect.label()))
                .findFirst()
                .orElseThrow();
        double x = viewport.screenX((tunnel.x1() + tunnel.x2() + 1) / 2.0);
        double y = viewport.screenY((tunnel.z1() + tunnel.z2() + 1) / 2.0);
        Optional<MineSnapshot.Rect> hovered = MineHit.rectAt(snapshot, viewport, x, y);
        assertEquals(Optional.of(tunnel), hovered, "Курсор в центре хода — подсказка о нём");
        MineMapPng.draw(
                snapshot, viewport, hovered, Optional.of(snapshot.markers().get(0)), "mine-slice-2.png");
        assertTrue(Files.exists(MineMapPng.OUT.resolve("mine-slice-2.png")), "Скрин среза записан");
        MapViewport zoomed = tunnelViewport(snapshot);
        double zoomX = zoomed.screenX((tunnel.x1() + tunnel.x2() + 1) / 2.0);
        double zoomY = zoomed.screenY((tunnel.z1() + tunnel.z2() + 1) / 2.0);
        MineMapPng.draw(
                snapshot, zoomed, MineHit.rectAt(snapshot, zoomed, zoomX, zoomY), Optional.empty(), "mine-tunnel.png");
        assertTrue(Files.exists(MineMapPng.OUT.resolve("mine-tunnel.png")), "Скрин хода записан");
    }

    @Test
    void render_BottomSlice_ShowsFloor() throws IOException {
        MineSnapshot snapshot = MineMapPng.marked(1);
        assertTrue(
                snapshot.cells().stream().anyMatch(cell -> cell.kind() == MineSnapshot.Kind.FLOOR),
                "На нижнем срезе виден пол");
        MapViewport zoomed = tunnelViewport(snapshot);
        MineMapPng.draw(snapshot, zoomed, Optional.empty(), Optional.empty(), "mine-floor-1.png");
        assertTrue(Files.exists(MineMapPng.OUT.resolve("mine-floor-1.png")), "Скрин пола записан");
    }

    private static MapViewport tunnelViewport(MineSnapshot snapshot) {
        MineSnapshot.Rect tunnel = snapshot.rects().stream()
                .filter(rect -> MineSnapshot.TUNNEL.equals(rect.label()))
                .findFirst()
                .orElseThrow();
        TownMapView.Bounds bounds =
                new TownMapView.Bounds(tunnel.x1() - 8, tunnel.z1() - 8, tunnel.x2() + 8, tunnel.z2() + 8);
        return MapViewport.fit(bounds, 0, 0, MineMapPng.WIDTH, MineMapPng.HEIGHT);
    }
}
