package com.theminespirit.core.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.theminespirit.core.town.TownFixtures;
import com.theminespirit.core.town.TownPlan;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class TownMarkerPngTests {
    @Test
    void render_SpiritMarker_HoverShowsNameAndOccupation() throws IOException {
        TownPlan plan = TownFixtures.hillyPlan(TownMapPng.SEEDS[0]);
        TownMapView view = TownMapView.of(TownMapPng.snapshot(plan));
        TerrainRaster terrain = TownMapPng.relief(view);
        MapViewport viewport =
                MapViewport.fit(view.bounds().union(terrain.bounds()), 0, 0, TownMapPng.WIDTH, TownMapPng.HEIGHT);
        List<MapMarker> markers = TownMapPng.markers(plan);
        MapMarker spirit = markers.stream()
                .filter(marker -> marker.kind() == MapMarker.Kind.SPIRIT)
                .findFirst()
                .orElseThrow();
        MapPaint.Font font = TownMapPng.font();
        double x = viewport.screenX(spirit.x() + 0.5);
        double y = viewport.screenY(spirit.z() + 0.5);
        Optional<MapMarker> hovered = MapMarkers.at(markers, viewport, font, x, y);
        assertEquals(Optional.of(spirit), hovered, "Курсор на точке духа — подсказка именно этого духа");
        TownMapPng.draw(view, terrain, viewport, Optional.empty(), markers, hovered, "plan-markers.png");
        TownMapPng.assertMarkerLabelsApart(markers, viewport, font, TownMapPng.SEEDS[0]);
    }
}
