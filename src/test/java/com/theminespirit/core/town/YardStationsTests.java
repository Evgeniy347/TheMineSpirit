package com.theminespirit.core.town;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import org.junit.jupiter.api.Test;

class YardStationsTests {
    @Test
    void nearest_TableOneBlockFromYardPoint_WinsOverFarTable() {
        BlockCoord from = new BlockCoord(-181, 70, -519);
        BlockCoord near = new BlockCoord(-180, 69, -519);
        BlockCoord far = new BlockCoord(-170, 69, -519);

        assertEquals(
                near,
                YardStations.nearest(List.of(far, near), from, from),
                "Цель крафта — реальный ближний верстак (-180,69,-519), а не клетка плана на 1 блок выше/западнее");
    }

    @Test
    void nearest_NoTable_FallsBackToPlanPoint() {
        BlockCoord from = new BlockCoord(-181, 70, -519);
        assertEquals(
                from,
                YardStations.nearest(List.of(), from, from),
                "Без верстака цель остаётся прежней: план не ломается на пустом дворе");
    }
}
