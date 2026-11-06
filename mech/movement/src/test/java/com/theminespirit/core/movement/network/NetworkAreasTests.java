package com.theminespirit.core.movement.network;

import static com.theminespirit.core.movement.network.GridWorld.cell;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.building.BuildBox;
import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class NetworkAreasTests {
    private static final BlockCoord FEET = cell(5, 70, 5);
    private static final BlockCoord CHEST = cell(20, 64, 3);

    @Test
    void prepare_SameHouseTwice_BuildsOnce() {
        NetworkAreas areas = new NetworkAreas();
        List<Integer> first = areas.prepare(FEET, CHEST, 0);
        assertEquals(1, first.size(), "первый маршрут в округе строит участок: " + first);
        areas.rebuilt(first.get(0), Set.of(1, 2), 0);
        assertEquals(
                List.of(),
                areas.prepare(cell(6, 70, 4), CHEST, 1000),
                "второй маршрут в том же доме участок не перестраивает: сеть строится один раз, а не на каждый подход");
    }

    @Test
    void prepare_LostSegment_RebuildsNotBeforeThrottle() {
        NetworkAreas areas = new NetworkAreas();
        int area = areas.prepare(FEET, CHEST, 0).get(0);
        areas.rebuilt(area, Set.of(7), 0);
        areas.segmentLost(7);
        assertEquals(
                List.of(),
                areas.prepare(FEET, CHEST, NetworkAreas.REBUILD_TICKS - 1),
                "участок без отрезка перестраивается не чаще раза в " + NetworkAreas.REBUILD_TICKS + " тиков");
        assertEquals(
                List.of(area),
                areas.prepare(FEET, CHEST, NetworkAreas.REBUILD_TICKS),
                "по истечении паузы участок, потерявший отрезок (проход заложили), строится заново");
    }

    @Test
    void prepare_SiteWithPlacedBlock_RebuildsAfterThrottleOnly() {
        NetworkAreas areas = new NetworkAreas();
        BuildBox box = BuildBox.of(0, 60, 0, 25, 80, 10);
        int site = areas.beginSite(box, List.of(cell(10, 64, 0)));
        List<Integer> build = areas.prepare(FEET, CHEST, 0);
        assertTrue(build.contains(site), "площадка стройки строится при первой нужде: " + build);
        build.forEach(id -> areas.rebuilt(id, Set.of(), 0));
        areas.blockPlaced(cell(12, 66, 4));
        assertEquals(
                List.of(),
                areas.prepare(FEET, CHEST, 30),
                "поставленный блок пересчитывает площадку не сразу, а не чаще раза в " + NetworkAreas.REBUILD_TICKS
                        + " тиков (ответ владельца 2: «раз в несколько секунд»)");
        assertEquals(
                List.of(site),
                areas.prepare(FEET, CHEST, NetworkAreas.REBUILD_TICKS),
                "по истечении паузы площадка с поставленным блоком пересчитывается целиком");
        areas.rebuilt(site, Set.of(), NetworkAreas.REBUILD_TICKS);
        assertEquals(
                List.of(),
                areas.prepare(FEET, CHEST, 10 * NetworkAreas.REBUILD_TICKS),
                "без новых блоков площадка не пересчитывается");
    }

    @Test
    void entrances_PlainAreaOverSite_IncludesSiteDoorway() {
        NetworkAreas areas = new NetworkAreas();
        BlockCoord doorway = cell(10, 64, 0);
        areas.beginSite(BuildBox.of(0, 60, 0, 12, 80, 10), List.of(doorway));
        int plain = areas.prepare(cell(5, 70, 5), cell(40, 64, 30), 0).stream()
                .filter(id -> areas.box(id).contains(cell(40, 64, 30)))
                .findFirst()
                .orElseThrow();
        assertTrue(
                areas.entrances(plain).contains(doorway),
                "проём двери стройки — начало сети и для участка вокруг: дверь как будто уже стоит, иначе сеть "
                        + "участка не заходит на площадку");
    }

    @Test
    void prepare_FarApart_NoArea() {
        NetworkAreas areas = new NetworkAreas();
        assertEquals(
                List.of(),
                areas.prepare(cell(0, 64, 0), cell(500, 64, 0), 0),
                "цель дальше стороны участка " + NetworkAreas.MAX_SIDE + " — участок не строится (дальний путь — "
                        + "отдельная задача, обход мира такого размера разом — лаг)");
    }
}
