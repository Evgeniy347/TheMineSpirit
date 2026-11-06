package com.theminespirit.core.movement.network;

import static com.theminespirit.core.movement.network.GridWorld.cell;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class TrailTrackTests {
    private static final int FLOOR = 0;
    private static final int FEET = 1;

    @Test
    void record_WalkedPath_IsRoutableBothWays() {
        GridWorld world = new GridWorld().slab(FLOOR, 0, 0, 20, 3);
        TrailTrack track = new TrailTrack();
        track.record(straight(0, 20, 1));

        Optional<List<BlockCoord>> forward = track.route(cell(0, FEET, 1), cell(20, FEET, 1), world);
        Optional<List<BlockCoord>> backward = track.route(cell(20, FEET, 1), cell(0, FEET, 1), world);
        assertTrue(forward.isPresent(), "по записанной тропе есть маршрут туда: " + forward);
        assertTrue(backward.isPresent(), "по записанной тропе есть маршрут обратно: " + backward);
        assertTrue(track.walked(cell(10, FEET, 1)), "каждая пройденная клетка помнится: к ней вернутся");
    }

    @Test
    void record_LongPath_PiecesFitGamePathLimit() {
        GridWorld world = new GridWorld().slab(FLOOR, 0, 0, 70, 3);
        TrailTrack track = new TrailTrack();
        track.record(straight(0, 70, 1));

        assertTrue(track.segments() > 1, "длинный след режется на отрезки, а не хранится одним куском");
        for (PathSegment segment : track.network().segments()) {
            assertTrue(
                    segment.length() <= PathSegment.MAX_LENGTH + 1e-9,
                    "отрезок тропы длиннее " + PathSegment.MAX_LENGTH + ": поиск пути игры его не пройдёт — "
                            + segment.length());
        }
        assertTrue(
                track.route(cell(0, FEET, 1), cell(70, FEET, 1), world).isPresent(),
                "разрезанная тропа остаётся связной от начала до конца");
    }

    @Test
    void record_OverlappingRoutes_ShareOneGraph() {
        GridWorld world = new GridWorld().slab(FLOOR, 0, 0, 30, 3);
        TrailTrack track = new TrailTrack();
        track.record(straight(0, 15, 1));
        track.record(straight(10, 30, 1));

        assertTrue(
                track.route(cell(0, FEET, 1), cell(30, FEET, 1), world).isPresent(),
                "пересекающиеся следы сливаются в один граф: дух пройдёт от начала первого до конца второго");
    }

    @Test
    void record_Gap_DoesNotBridgeTeleport() {
        GridWorld world = new GridWorld().slab(FLOOR, 0, 0, 50, 3);
        TrailTrack track = new TrailTrack();
        List<BlockCoord> path = new ArrayList<>(straight(0, 5, 1));
        path.add(cell(40, FEET, 1));
        track.record(path);

        assertFalse(
                track.route(cell(0, FEET, 1), cell(40, FEET, 1), world).isPresent(),
                "разрыв в следе (перенос, вылет) не сшивается прямой: иначе дух пойдёт сквозь стену");
    }

    @Test
    void record_DuplicateConsecutiveCells_AreIgnored() {
        GridWorld world = new GridWorld().slab(FLOOR, 0, 0, 5, 3);
        TrailTrack track = new TrailTrack();
        track.record(List.of(cell(0, FEET, 1), cell(0, FEET, 1), cell(1, FEET, 1), cell(1, FEET, 1), cell(2, FEET, 1)));

        assertTrue(
                track.route(cell(0, FEET, 1), cell(2, FEET, 1), world).isPresent(),
                "повторы клетки не мешают маршруту");
        assertEquals(3, track.cells(), "повторные клетки не раздувают память тропы");
    }

    @Test
    void route_StartOffTrail_ConnectsWithinAccess() {
        GridWorld world = new GridWorld().slab(FLOOR, 0, 0, 20, 5);
        TrailTrack track = new TrailTrack();
        track.record(straight(0, 20, 2));

        assertTrue(
                track.route(cell(0, FEET, 0), cell(20, FEET, 4), world).isPresent(),
                "дух в стороне от тропы входит на неё шагами доступа");
    }

    @Test
    void route_EmptyTrack_IsEmpty() {
        GridWorld world = new GridWorld().slab(FLOOR, 0, 0, 5, 5);
        assertTrue(
                new TrailTrack()
                        .route(cell(0, FEET, 0), cell(5, FEET, 5), world)
                        .isEmpty(),
                "без записанных троп маршрута по графу нет: вызывающий ищет путь обычным A*");
    }

    private static List<BlockCoord> straight(int fromX, int toX, int z) {
        List<BlockCoord> cells = new ArrayList<>();
        for (int x = fromX; x <= toX; x++) {
            cells.add(cell(x, FEET, z));
        }
        return cells;
    }
}
