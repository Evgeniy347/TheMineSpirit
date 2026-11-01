package com.theminespirit.core.town;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import org.junit.jupiter.api.Test;

class TreeSupportTests {
    @Test
    void candidates_CoordinatesNearIntegerLimit_CenterComputedWithoutOverflow() {
        int x = 1_500_000_000;
        int z = -1_500_000_002;
        List<BlockCoord> cells = List.of(new BlockCoord(x, 70, z), new BlockCoord(x + 1, 70, z + 1));

        List<BlockCoord> columns = TreeSupport.candidates(cells);

        assertEquals(
                x,
                columns.get(4).x(),
                "Середина по X вышла за int: сумма крайних координат переполнилась, и опора встала не рядом с деревом");
        assertEquals(
                x,
                columns.get(5).x(),
                "Середина по X вышла за int: сумма крайних координат переполнилась, и опора встала не рядом с деревом");
        assertEquals(
                z + 1,
                columns.get(6).z(),
                "Середина по Z вышла за int: сумма крайних координат переполнилась, и опора встала не рядом с деревом");
        assertEquals(
                z + 1,
                columns.get(7).z(),
                "Середина по Z вышла за int: сумма крайних координат переполнилась, и опора встала не рядом с деревом");
    }
}
