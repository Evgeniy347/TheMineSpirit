package com.theminespirit.core.movement;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.route.RouteCell;
import com.theminespirit.core.route.RouteWorld;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SurfaceDescentTests {
    private static final BlockCoord START = new BlockCoord(0, 77, 0);
    private static final RouteWorld ROOF_OVER_WATER = (x, y, z) -> {
        if (y == 76 && Math.abs(x) <= 1 && Math.abs(z) <= 1) {
            return RouteCell.solid(1);
        }
        if (y <= 62 && y >= 60 && Math.abs(x) <= 2 && Math.abs(z) <= 2) {
            return RouteCell.WATER;
        }
        if (y <= 59) {
            return RouteCell.solid(1);
        }
        return RouteCell.AIR;
    };

    @Test
    void path_FromRoofToWaterLanding_FindsSafeFall() {
        Optional<List<BlockCoord>> found = SurfaceEscape.path(
                START,
                ROOF_OVER_WATER,
                cell -> cell.y() <= 62 && Math.abs(cell.x()) <= 2 && Math.abs(cell.z()) <= 2,
                START);

        assertTrue(
                found.isPresent(),
                "дух на крыше -172,77,-516 видит воду в 15 блоках ниже: мачта/крыша без спуска — это "
                        + "безопасное падение в воду, а не «перенос не найден»");
        BlockCoord end = found.orElseThrow().get(found.orElseThrow().size() - 1);
        assertTrue(end.y() <= 62, "путь обязан кончаться в воде/на нижней площадке, а кончается в " + end);
    }
}
