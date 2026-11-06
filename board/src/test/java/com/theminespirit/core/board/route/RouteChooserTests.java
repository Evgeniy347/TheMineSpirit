package com.theminespirit.core.board.route;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RouteChooserTests {
    private static final BlockCoord HOME = new BlockCoord(0, 64, 0);

    @Test
    void nearest_SameResource_PicksClosestOpenBiome() {
        List<RouteSpot> spots = List.of(
                new RouteSpot(RouteResource.SAND, "minecraft:desert", new BlockCoord(80, 64, 0)),
                new RouteSpot(RouteResource.SAND, "minecraft:beach", new BlockCoord(20, 64, 0)),
                new RouteSpot(RouteResource.DIRT, "minecraft:plains", new BlockCoord(5, 64, 0)));

        Optional<RouteSpot> nearest = RouteChooser.nearest(RouteResource.SAND, HOME, spots);

        assertEquals(
                new BlockCoord(20, 64, 0),
                nearest.orElseThrow().point(),
                "Из двух песков берут ближайший: дальний биом не должен перебивать близкий");
    }

    @Test
    void nearestEach_EachResource_OwnNearestPoint() {
        List<RouteSpot> spots = List.of(
                new RouteSpot(RouteResource.SAND, "minecraft:desert", new BlockCoord(40, 64, 0)),
                new RouteSpot(RouteResource.SNOW, "minecraft:snowy_plains", new BlockCoord(0, 64, -60)),
                new RouteSpot(RouteResource.DIRT, "minecraft:plains", new BlockCoord(-30, 64, 0)),
                new RouteSpot(RouteResource.SAND, "minecraft:desert", new BlockCoord(10, 64, 0)));

        Map<RouteResource, RouteSpot> nearest = RouteChooser.nearestEach(HOME, spots);

        assertEquals(
                new BlockCoord(10, 64, 0),
                nearest.get(RouteResource.SAND).point(),
                "Песок — ближайший из двух пустынь");
        assertEquals(
                new BlockCoord(0, 64, -60),
                nearest.get(RouteResource.SNOW).point(),
                "Снег — своя ближайшая снежная точка");
        assertEquals(
                new BlockCoord(-30, 64, 0), nearest.get(RouteResource.DIRT).point(), "Земля — своя ближайшая равнина");
    }

    @Test
    void nearest_Tie_BreaksDeterministically() {
        List<RouteSpot> spots = List.of(
                new RouteSpot(RouteResource.DIRT, "minecraft:forest", new BlockCoord(0, 64, 10)),
                new RouteSpot(RouteResource.DIRT, "minecraft:plains", new BlockCoord(0, 64, -10)));

        assertEquals(
                new BlockCoord(0, 64, -10),
                RouteChooser.nearest(RouteResource.DIRT, HOME, spots)
                        .orElseThrow()
                        .point(),
                "На равном расстоянии выбор детерминирован по координатам, а не по порядку сканирования");
    }

    @Test
    void ofBiome_DesertSnowyPlains_MapToResources() {
        assertEquals(Optional.of(RouteResource.SAND), RouteResource.ofBiome("minecraft:desert"), "Пустыня — песок");
        assertEquals(
                Optional.of(RouteResource.SNOW),
                RouteResource.ofBiome("minecraft:snowy_taiga"),
                "Снежная тайга — снег");
        assertEquals(Optional.of(RouteResource.DIRT), RouteResource.ofBiome("minecraft:plains"), "Равнина — земля");
        assertEquals(
                Optional.of(RouteResource.DIRT),
                RouteResource.ofBiome("minecraft:flower_forest"),
                "Цветочный лес — земля");
        assertEquals(
                Optional.empty(),
                RouteResource.ofBiome("minecraft:ocean"),
                "Океан — не источник песка, снега или земли");
    }

    @Test
    void events_SnowAndDirt_UseGraphNames() {
        assertEquals(
                "source_depleted:snow",
                RouteResource.SNOW.depletedEvent(),
                "Граф знает источник снега как source_depleted:snow, а не snowball");
        assertEquals(
                "source_depleted:dirt",
                RouteResource.DIRT.depletedEvent(),
                "Граф знает источник земли как source_depleted:dirt");
        assertEquals(
                "route_retarget_snow",
                RouteResource.SNOW.retargetEvent(),
                "Событие переноса снежного маршрута — route_retarget_snow");
        assertEquals(
                "route_retarget_dirt",
                RouteResource.DIRT.retargetEvent(),
                "Событие переноса земляного маршрута — route_retarget_dirt");
    }

    @Test
    void ofBuilding_RouteAndPath_MapToResource() {
        assertEquals(
                Optional.of(RouteResource.SAND),
                RouteResource.ofBuilding("route_desert"),
                "Постройка маршрута пустыни — песок");
        assertEquals(Optional.of(RouteResource.SAND), RouteResource.ofBuilding("path_desert"), "Тропа пустыни — песок");
        assertTrue(RouteResource.SAND.isRoute("route_desert"), "route_desert — постройка маршрута");
        assertFalse(RouteResource.SAND.isRoute("path_desert"), "path_desert — не маршрут, а тропа");
        assertTrue(RouteResource.SAND.isPath("path_desert"), "path_desert — тропа до места добычи");
    }
}
