package com.theminespirit.core.route;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.movement.SurfaceEscape;
import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SurfaceEscapeLiveTests {
    private static final int ORIGIN_X = -160;
    private static final int ORIGIN_Y = -60;
    private static final int ORIGIN_Z = -440;
    private static final int SIZE_X = 70;
    private static final int SIZE_Z = 80;
    private static final SnapshotWorld WORLD = SnapshotWorld.load("/live/waterstuck/cave.txt");
    private static final int[] TOPS = tops();

    @Test
    void path_FromLiveWaterPool_ReachesSkyWithoutBreaking() {
        Optional<List<BlockCoord>> found =
                SurfaceEscape.path(new BlockCoord(-104, -52, -380), WORLD, SurfaceEscapeLiveTests::sky);

        assertTrue(
                found.isPresent(),
                "В observe14 дух стоял в воде -104,-52,-380, а над ним до y=100 сплошная порода: ближайший "
                        + "естественный выход на поверхность существует, но RoutePlanner даёт только PARTIAL "
                        + "и дух не выходит. Путь по проходимым клеткам обязан найтись без ломания блоков");
        List<BlockCoord> path = found.orElseThrow();
        assertTrue(path.size() > 100, "Путь до поверхности длинный, а найден " + path.size() + " клеток");
        BlockCoord end = path.get(path.size() - 1);
        assertTrue(sky(end), "Путь обязан кончаться под небом, а кончается в " + end);
    }

    @Test
    void path_FromDryCavern_ReachesSky() {
        Optional<List<BlockCoord>> found =
                SurfaceEscape.path(new BlockCoord(-104, -20, -412), WORLD, SurfaceEscapeLiveTests::sky);

        assertTrue(
                found.isPresent(),
                "После dig_out дух оказывается в сухой пещере -104,-20,-412 и там падает/теряется: путь из "
                        + "пещеры к небу тоже обязан находиться, иначе спасение снова не закончится");
    }

    @Test
    void path_LiveWaterPool_EveryStepIsWalkable() {
        List<BlockCoord> path = SurfaceEscape.path(new BlockCoord(-104, -52, -380), WORLD, SurfaceEscapeLiveTests::sky)
                .orElseThrow();

        for (int index = 1; index < path.size(); index++) {
            BlockCoord from = path.get(index - 1);
            BlockCoord to = path.get(index);
            int rise = to.y() - from.y();
            assertTrue(
                    Math.abs(to.x() - from.x()) + Math.abs(to.z() - from.z()) <= 1 && rise <= 1,
                    "Шаг " + from + " → " + to + " не соседний: дух не перепрыгнет");
            if (rise == 0) {
                assertTrue(
                        supported(to) || water(from) || climbable(to),
                        "Ровный шаг " + from + " → " + to + " ведёт в неподпёртую клетку: дух упадёт, а не пройдёт");
            }
            if (rise == 1) {
                assertTrue(
                        supported(to) || climbable(to),
                        "Подъём " + from + " → " + to + " без опоры или лианы: в воздухе не подняться");
            }
        }
    }

    private static boolean supported(BlockCoord cell) {
        BlockCoord below = new BlockCoord(cell.x(), cell.y() - 1, cell.z());
        return solid(below.x(), below.y(), below.z()) || water(below) || water(cell) || climbable(cell);
    }

    private static boolean water(BlockCoord cell) {
        return RouteCell.kind(WORLD.cell(cell.x(), cell.y(), cell.z())) == RouteCell.WATER;
    }

    private static boolean climbable(BlockCoord cell) {
        return RouteCell.kind(WORLD.cell(cell.x(), cell.y(), cell.z())) == RouteCell.CLIMB;
    }

    private static boolean solid(int x, int y, int z) {
        int kind = RouteCell.kind(WORLD.cell(x, y, z));
        return kind == RouteCell.SOLID || kind == RouteCell.BLOCKED || kind == RouteCell.TALL;
    }

    private static boolean sky(BlockCoord cell) {
        return cell.y() > top(cell.x(), cell.z());
    }

    private static int top(int x, int z) {
        int dx = x - ORIGIN_X;
        int dz = z - ORIGIN_Z;
        if (dx < 0 || dz < 0 || dx >= SIZE_X || dz >= SIZE_Z) {
            return ORIGIN_Y + 164;
        }
        return TOPS[dz * SIZE_X + dx];
    }

    private static int[] tops() {
        int[] tops = new int[SIZE_X * SIZE_Z];
        for (int dx = 0; dx < SIZE_X; dx++) {
            for (int dz = 0; dz < SIZE_Z; dz++) {
                int top = ORIGIN_Y;
                for (int y = ORIGIN_Y + 164; y >= ORIGIN_Y; y--) {
                    int kind = RouteCell.kind(WORLD.cell(ORIGIN_X + dx, y, ORIGIN_Z + dz));
                    if (kind == RouteCell.SOLID || kind == RouteCell.BLOCKED || kind == RouteCell.TALL) {
                        top = y;
                        break;
                    }
                }
                tops[dz * SIZE_X + dx] = top;
            }
        }
        return tops;
    }
}
