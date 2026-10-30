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
    private static final WallLayout WALL = WallLayout.of(PRESETS, StageSchemes.WALL);

    @Test
    void open_StraightLine_LongFragmentAndPost() {
        List<ColumnMask.Cell> line = run(new ArrayList<>(), 0, 0, 1, 0, 9);
        List<WallPiece> pieces = FENCE.open(line, List.of());
        assertExact(line, Set.of(), pieces);
        assertEquals(
                2,
                pieces.size(),
                "РїСЂСЏРјР°СЏ РёР· 9 РєР»РµС‚РѕРє вЂ” РґР»РёРЅРЅС‹Р№ С„СЂР°РіРјРµРЅС‚ РЅР° 8 Рё СЃС‚РѕР»Р±: РєСЂСѓРїРЅС‹Рµ С„СЂР°РіРјРµРЅС‚С‹ СЌРєРѕРЅРѕРјСЏС‚ СЃС‚СЂРѕР№РєРё");
    }

    @Test
    void open_RightAngle_CornerFragmentClosesTurn() {
        List<ColumnMask.Cell> line = run(new ArrayList<>(), 0, 0, 1, 0, 6);
        run(line, 5, 1, 0, 1, 1);
        List<WallPiece> pieces = FENCE.open(line, List.of());
        assertExact(line, Set.of(), pieces);
        assertTrue(
                uses(pieces, "fence_corner"),
                "РїРѕРІРѕСЂРѕС‚ РЅР° 90В° РєР»Р°РґС‘С‚СЃСЏ С„СЂР°РіРјРµРЅС‚РѕРј-СѓРіР»РѕРј, Р° РЅРµ СЃС‚РѕР»Р±Р°РјРё: "
                        + pieces);
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
                "Р»РµСЃРµРЅРєР° РѕРіСЂР°РґС‹ РєР»Р°РґС‘С‚СЃСЏ РґРёР°РіРѕРЅР°Р»СЊРЅС‹РјРё С„СЂР°РіРјРµРЅС‚Р°РјРё: "
                        + pieces);
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
                "РєР°Р¶РґР°СЏ РєР°Р»РёС‚РєР° РѕРіСЂР°РґС‹ вЂ” С„СЂР°РіРјРµРЅС‚ СЃ РєР°Р»РёС‚РєРѕР№ Рё С„Р°РєРµР»Р°РјРё: "
                        + pieces);
    }

    @Test
    void wall_StraightLine_StoneFragments() {
        List<ColumnMask.Cell> line = run(new ArrayList<>(), 0, 0, 1, 0, 9);
        List<WallPiece> pieces = WALL.open(line, List.of());
        assertExact(line, Set.of(), pieces);
        assertTrue(
                pieces.stream().allMatch(piece -> piece.scheme().startsWith("wall_3")),
                "этап 2 поднимает забор каменной стеной: линия идёт фрагментами стены, а не забора: " + pieces);
    }

    @Test
    void wall_ClosedRingWithGates_GateFragments() {
        List<ColumnMask.Cell> ring = run(new ArrayList<>(), 0, 0, 1, 0, 12);
        run(ring, 11, 1, 0, 1, 11);
        run(ring, 10, 11, -1, 0, 11);
        run(ring, 0, 10, 0, -1, 10);
        Set<Integer> gates = Set.of(5, 27);
        List<WallPiece> pieces = WALL.closed(ring, List.copyOf(gates));
        assertExact(ring, Set.of(ring.get(5), ring.get(27)), pieces);
        assertEquals(
                2,
                pieces.stream().filter(piece -> "gate_3".equals(piece.scheme())).count(),
                "каждые ворота этапа 2 — фрагмент ворот со створкой: " + pieces);
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
                "РєР°Р»РёС‚РєР° РЅР° СѓРіР»Сѓ РЅРµ Р»РѕР¶РёС‚СЃСЏ РІРѕ С„СЂР°РіРјРµРЅС‚ СЃ РєР°Р»РёС‚РєРѕР№: С‚РёС…Рѕ РїРѕС‚РµСЂСЏС‚СЊ РїСЂРѕС…РѕРґ РЅРµР»СЊР·СЏ");
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
                "С„СЂР°РіРјРµРЅС‚С‹ Р·Р°РєСЂС‹РІР°СЋС‚ СЂРѕРІРЅРѕ Р»РёРЅРёСЋ СЃС‚РµРЅС‹: РґС‹СЂР° вЂ” РїСЂРѕС…РѕРґ РґР»СЏ РјРѕР±РѕРІ, Р»РёС€РЅСЏСЏ РєР»РµС‚РєР° вЂ” Р·Р°Р±РѕСЂ РїРѕРїРµСЂС‘Рє");
        assertTrue(
                covered.values().stream().allMatch(count -> count == 1),
                "С„СЂР°РіРјРµРЅС‚С‹ СЃС‚С‹РєСѓСЋС‚СЃСЏ Р±РµР· РЅР°Р»РѕР¶РµРЅРёР№: РґРІРѕР№РЅР°СЏ РєР»РµС‚РєР° вЂ” РґРІРµ СЃС‚СЂРѕР№РєРё СЃРїРѕСЂСЏС‚ Рѕ Р±Р»РѕРєРµ "
                        + covered);
        assertEquals(gates, gated, "РєР°Р»РёС‚РєРё СЃС‚РѕСЏС‚ СЂРѕРІРЅРѕ РІ РєР»РµС‚РєР°С… РєР°Р»РёС‚РѕРє РїР»Р°РЅР°");
    }
}
