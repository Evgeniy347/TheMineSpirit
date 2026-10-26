package com.theminespirit.core.town;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.building.PresetLibrary;
import com.theminespirit.core.building.Schematic;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.reload.DataTables;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class WallLayoutTests {
    private static final PresetLibrary PRESETS = DataTables.INSTANCE.presets().current();
    private static final WallLayout FENCE = WallLayout.of(PRESETS, StageSchemes.FENCE);

    @Test
    void open_StraightLine_LongFragmentAndPost() {
        List<ColumnMask.Cell> line = run(new ArrayList<>(), 0, 0, 1, 0, 9);
        List<WallPiece> pieces = FENCE.open(line, List.of());
        assertExact(line, Set.of(), pieces);
        assertEquals(
                2,
                pieces.size(),
                "прямая из 9 клеток — длинный фрагмент на 8 и столб: крупные фрагменты экономят стройки");
    }

    @Test
    void open_RightAngle_CornerFragmentClosesTurn() {
        List<ColumnMask.Cell> line = run(new ArrayList<>(), 0, 0, 1, 0, 6);
        run(line, 5, 1, 0, 1, 1);
        List<WallPiece> pieces = FENCE.open(line, List.of());
        assertExact(line, Set.of(), pieces);
        assertTrue(uses(pieces, "fence_corner"), "поворот на 90° кладётся фрагментом-углом, а не столбами: " + pieces);
    }

    @Test
    void open_Staircase_DiagonalAndCorner45() {
        List<ColumnMask.Cell> line = run(new ArrayList<>(), 0, 0, 1, 0, 3);
        for (int step = 0; step < 4; step++) {
            line.add(new ColumnMask.Cell(2 + step, 1 + step));
            line.add(new ColumnMask.Cell(3 + step, 1 + step));
        }
        List<WallPiece> pieces = FENCE.open(line, List.of());
        assertExact(line, Set.of(), pieces);
        assertTrue(
                uses(pieces, "fence_diagonal")
                        || uses(pieces, "fence_corner45_right")
                        || uses(pieces, "fence_corner45_left"),
                "лесенка ограды кладётся диагональными фрагментами: " + pieces);
    }

    @Test
    void closed_RingWithGates_WicketOnEveryGate() {
        List<ColumnMask.Cell> ring = run(new ArrayList<>(), 0, 0, 1, 0, 12);
        run(ring, 11, 1, 0, 1, 11);
        run(ring, 10, 11, -1, 0, 11);
        run(ring, 0, 10, 0, -1, 10);
        Set<Integer> gates = Set.of(5, 27);
        List<WallPiece> pieces = FENCE.closed(ring, List.copyOf(gates));
        assertExact(ring, Set.of(ring.get(5), ring.get(27)), pieces);
        assertEquals(
                2,
                pieces.stream()
                        .filter(piece -> "fence_wicket".equals(piece.scheme()))
                        .count(),
                "каждая калитка ограды — фрагмент с калиткой и факелами: " + pieces);
    }

    @Test
    void closed_GateOnTurn_FailsLoudly() {
        List<ColumnMask.Cell> ring = run(new ArrayList<>(), 0, 0, 1, 0, 6);
        run(ring, 5, 1, 0, 1, 5);
        run(ring, 4, 5, -1, 0, 5);
        run(ring, 0, 4, 0, -1, 4);
        assertThrows(
                IllegalStateException.class,
                () -> FENCE.closed(ring, List.of(5)),
                "калитка на углу не ложится во фрагмент с калиткой: тихо потерять проход нельзя");
    }

    private static List<ColumnMask.Cell> run(List<ColumnMask.Cell> line, int x, int z, int dx, int dz, int count) {
        for (int step = 0; step < count; step++) {
            line.add(new ColumnMask.Cell(x + dx * step, z + dz * step));
        }
        return line;
    }

    private static boolean uses(List<WallPiece> pieces, String scheme) {
        return pieces.stream().anyMatch(piece -> piece.scheme().equals(scheme));
    }

    private static void assertExact(List<ColumnMask.Cell> line, Set<ColumnMask.Cell> gates, List<WallPiece> pieces) {
        Map<ColumnMask.Cell, Integer> covered = new HashMap<>();
        Set<ColumnMask.Cell> gated = new HashSet<>();
        for (WallPiece piece : pieces) {
            Schematic schematic = PRESETS.byId(piece.scheme()).orElseThrow().schematic();
            for (Schematic.PlacedEntry entry :
                    schematic.entries(new BlockCoord(piece.x(), 0, piece.z()), piece.rotation())) {
                if (entry.pos().y() != 0) {
                    continue;
                }
                ColumnMask.Cell cell =
                        new ColumnMask.Cell(entry.pos().x(), entry.pos().z());
                covered.merge(cell, 1, Integer::sum);
                if (entry.block().endsWith("_gate")) {
                    gated.add(cell);
                }
            }
        }
        assertEquals(
                Set.copyOf(line),
                covered.keySet(),
                "фрагменты закрывают ровно линию стены: дыра — проход для мобов, лишняя клетка — забор поперёк");
        assertTrue(
                covered.values().stream().allMatch(count -> count == 1),
                "фрагменты стыкуются без наложений: двойная клетка — две стройки спорят о блоке " + covered);
        assertEquals(gates, gated, "калитки стоят ровно в клетках калиток плана");
    }
}
