package com.theminespirit.core.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.theminespirit.core.town.TownFixtures;
import com.theminespirit.core.town.TownPlan;
import java.awt.Rectangle;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class TownMapPngTests {
    @Test
    void render_ThreeReliefSeeds_LabelsInsideLotsAndApart() throws IOException {
        for (long seed : TownMapPng.SEEDS) {
            TownPlan plan = TownFixtures.hillyPlan(seed);
            TownMapView view = TownMapView.of(TownMapPng.snapshot(plan));
            TerrainRaster terrain = TownMapPng.relief(view);
            MapViewport viewport =
                    MapViewport.fit(view.bounds().union(terrain.bounds()), 0, 0, TownMapPng.WIDTH, TownMapPng.HEIGHT);
            List<Rectangle> labels = TownMapPng.draw(
                    view,
                    terrain,
                    viewport,
                    Optional.empty(),
                    TownMapPng.markers(plan),
                    Optional.empty(),
                    "plan-seed-" + seed + ".png");
            TownMapPng.assertApart(labels, seed);
            assertEquals(
                    view.rects().stream()
                            .filter(rect -> rect.kind() == TownMapView.Kind.LOT)
                            .count(),
                    plan.placements().size(),
                    "Каждая постройка плана — участок на карте, сид " + seed);
        }
    }

    @Test
    void render_HoverDamagedLot_TooltipWithRestorePercent() throws IOException {
        TownPlan plan = TownFixtures.hillyPlan(TownMapPng.SEEDS[0]);
        TownMapView view = TownMapView.of(TownMapPng.snapshot(plan));
        TerrainRaster terrain = TownMapPng.relief(view);
        TownMapView.MapRect damaged = view.rects().stream()
                .filter(TownMapView.MapRect::damaged)
                .findFirst()
                .orElseThrow();
        MapViewport fit =
                MapViewport.fit(view.bounds().union(terrain.bounds()), 0, 0, TownMapPng.WIDTH, TownMapPng.HEIGHT);
        double x = fit.screenX((damaged.x1() + damaged.x2() + 1) / 2.0);
        double y = fit.screenY((damaged.z1() + damaged.z2() + 1) / 2.0);
        MapViewport zoomed = fit.zoom(x, y, 3);
        Optional<TownMapView.MapRect> hovered = MapHit.lotAt(view, zoomed, x, y);
        assertEquals(Optional.of(damaged), hovered, "Курсор в центре повреждённого участка — подсказка о нём");
        TownMapPng.assertApart(
                TownMapPng.draw(
                        view,
                        terrain,
                        zoomed,
                        hovered,
                        TownMapPng.markers(plan),
                        Optional.empty(),
                        "plan-hover-damaged.png"),
                TownMapPng.SEEDS[0]);
    }
}
