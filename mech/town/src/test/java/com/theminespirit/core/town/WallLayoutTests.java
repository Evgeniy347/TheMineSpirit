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
    static final PresetLibrary PRESETS = DataTables.INSTANCE.presets().current();
    static final WallLayout FENCE = WallLayout.of(PRESETS, StageSchemes.FENCE);
    static final WallLayout WALL = WallLayout.of(PRESETS, StageSchemes.WALL);

    @Test
    void open_StraightLine_LongFragmentAndPost() {
        List<ColumnMask.Cell> line = run(new ArrayList<>(), 0, 0, 1, 0, 9);
        List<WallPiece> pieces = FENCE.open(line, List.of());
        assertExact(line, Set.of(), pieces);
        assertEquals(
                2,
                pieces.size(),
                "РїСЂСЏРјР°СЏ РёР· 9 РєР»РµС‚РѕРє вЂ” РґР»РёРЅРЅС‹Р№ С„СЂР°РіРјРµРЅС‚ РЅР° 8 Рё СЃС‚РѕР»Р±: РєСЂСѓРїРЅС‹Рµ С„СЂР°РіРјРµРЅС‚С‹ СЌРєРѕРЅРѕРјСЏС‚ СЃС‚СЂРѕР№РєРё"
                        + " Иначе тест open_StraightLine_LongFragmentAndPost перестанет ловить эту поломку. При смене open_StraightLine_LongFragmentAndPost проверь это утверждение.");
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
                        + pieces
                        + " Иначе тест open_RightAngle_CornerFragmentClosesTurn перестанет ловить эту поломку. При смене open_RightAngle_CornerFragmentClosesTurn проверь это утверждение.");
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
                        + pieces
                        + " Иначе тест open_Staircase_DiagonalAndCorner45 перестанет ловить эту поломку. При смене open_Staircase_DiagonalAndCorner45 проверь это утверждение.");
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
                        + pieces
                        + " Иначе тест closed_RingWithGates_WicketOnEveryGate перестанет ловить эту поломку. При смене closed_RingWithGates_WicketOnEveryGate проверь это утверждение.");
    }

    @Test
    void wall_StraightLine_StoneFragments() {
        List<ColumnMask.Cell> line = run(new ArrayList<>(), 0, 0, 1, 0, 9);
        List<WallPiece> pieces = WALL.open(line, List.of());
        assertExact(line, Set.of(), pieces);
        assertTrue(
                pieces.stream().allMatch(piece -> piece.scheme().startsWith("wall_3")),
                "этап 2 поднимает забор каменной стеной: линия идёт фрагментами стены, а не забора: " + pieces
                        + " Иначе тест wall_StraightLine_StoneFragments перестанет ловить эту поломку. При смене wall_StraightLine_StoneFragments проверь это утверждение.");
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
                "каждые ворота этапа 2 — фрагмент ворот со створкой: " + pieces
                        + " Иначе тест wall_ClosedRingWithGates_GateFragments перестанет ловить эту поломку. При смене wall_ClosedRingWithGates_GateFragments проверь это утверждение.");
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
                "РєР°Р»РёС‚РєР° РЅР° СѓРіР»Сѓ РЅРµ Р»РѕР¶РёС‚СЃСЏ РІРѕ С„СЂР°РіРјРµРЅС‚ СЃ РєР°Р»РёС‚РєРѕР№: С‚РёС…Рѕ РїРѕС‚РµСЂСЏС‚СЊ РїСЂРѕС…РѕРґ РЅРµР»СЊР·СЏ"
                        + " Иначе тест closed_GateOnTurn_FailsLoudly перестанет ловить эту поломку. При смене closed_GateOnTurn_FailsLoudly проверь это утверждение.");
    }

    @Test
    void parts_RingWithGates_SplitsSamePiecesWithoutLossOrRepeats() {
        List<ColumnMask.Cell> ring = run(new ArrayList<>(), 0, 0, 1, 0, 12);
        run(ring, 11, 1, 0, 1, 11);
        run(ring, 10, 11, -1, 0, 11);
        run(ring, 0, 10, 0, -1, 10);
        List<Integer> gates = List.of(5, 27);

        List<WallPiece> whole = FENCE.closed(ring, gates);
        List<List<WallPiece>> parts = FENCE.parts(ring, gates, 5);
        List<WallPiece> flat = new ArrayList<>();
        parts.forEach(flat::addAll);

        assertEquals(
                5,
                parts.size(),
                "Ограда делится ровно на запрошенное число частей"
                        + " Иначе тест parts_RingWithGates_SplitsSamePiecesWithoutLossOrRepeats перестанет ловить эту поломку. При смене parts_RingWithGates_SplitsSamePiecesWithoutLossOrRepeats проверь это утверждение.");
        assertEquals(
                whole,
                flat,
                "Части ограды вместе дают ту же раскладку, что и целый обход: иначе часть забора пропадает или "
                        + "строится дважды"
                        + " При смене parts_RingWithGates_SplitsSamePiecesWithoutLossOrRepeats проверь это утверждение.");
    }

    @Test
    void parts_MorePartsThanPieces_KeepsEmptyPartsAndSamePieces() {
        List<ColumnMask.Cell> line = run(new ArrayList<>(), 0, 0, 1, 0, 9);
        List<WallPiece> whole = FENCE.open(line, List.of());
        List<List<WallPiece>> parts = FENCE.parts(line, List.of(), 5);
        List<WallPiece> flat = new ArrayList<>();
        parts.forEach(flat::addAll);

        assertEquals(
                5,
                parts.size(),
                "Пустые части остаются в списке: номер части — это id задания доски"
                        + " Иначе тест parts_MorePartsThanPieces_KeepsEmptyPartsAndSamePieces перестанет ловить эту поломку. При смене parts_MorePartsThanPieces_KeepsEmptyPartsAndSamePieces проверь это утверждение.");
        assertEquals(
                whole,
                flat,
                "Делёж не теряет и не повторяет куски, даже если частей больше, чем кусков"
                        + " Иначе тест parts_MorePartsThanPieces_KeepsEmptyPartsAndSamePieces перестанет ловить эту поломку. При смене parts_MorePartsThanPieces_KeepsEmptyPartsAndSamePieces проверь это утверждение.");
        assertEquals(
                2,
                parts.stream().filter(part -> !part.isEmpty()).count(),
                "Пустых частей три, а куски попадают ровно в две части"
                        + " Иначе тест parts_MorePartsThanPieces_KeepsEmptyPartsAndSamePieces перестанет ловить эту поломку. При смене parts_MorePartsThanPieces_KeepsEmptyPartsAndSamePieces проверь это утверждение.");
    }

    @Test
    void parts_CountBelowOne_Throws() {
        List<ColumnMask.Cell> line = run(new ArrayList<>(), 0, 0, 1, 0, 9);

        assertThrows(
                IllegalArgumentException.class,
                () -> FENCE.parts(line, List.of(), 0),
                "Ноль частей — ошибка вызова: иначе ограда молча не строится"
                        + " При смене parts_CountBelowOne_Throws проверь это утверждение.");
    }

    static long count(List<WallPiece> pieces, String scheme) {
        return pieces.stream().filter(piece -> piece.scheme().equals(scheme)).count();
    }

    static void assertRing(List<ColumnMask.Cell> posts) {
        Set<ColumnMask.Cell> cells = Set.copyOf(posts);
        assertEquals(
                posts.size(),
                cells.size(),
                "В контуре одна и та же клетка не повторяется: иначе столб строится дважды"
                        + " При смене assertRing проверь это утверждение. Иначе поломка пройдёт незамеченной в прогоне.");
        for (int index = 0; index < posts.size(); index++) {
            ColumnMask.Cell here = posts.get(index);
            ColumnMask.Cell next = posts.get((index + 1) % posts.size());
            assertEquals(
                    1,
                    Math.abs(here.x() - next.x()) + Math.abs(here.z() - next.z()),
                    "Соседние клетки контура обязаны стоять вплотную: иначе в ограде дыра между " + here + " и " + next
                            + " При смене assertRing проверь это утверждение.");
            int neighbours = 0;
            for (ColumnMask.Cell other : cells) {
                if (!other.equals(here) && Math.abs(here.x() - other.x()) + Math.abs(here.z() - other.z()) == 1) {
                    neighbours++;
                }
            }
            assertEquals(
                    2,
                    neighbours,
                    "У клетки " + here + " обязано быть ровно два соседа по контуру: иначе ограда ветвится или "
                            + "касается сама себя" + " При смене assertRing проверь это утверждение.");
        }
    }

    static List<ColumnMask.Cell> run(List<ColumnMask.Cell> line, int x, int z, int dx, int dz, int count) {
        for (int step = 0; step < count; step++) {
            line.add(new ColumnMask.Cell(x + dx * step, z + dz * step));
        }
        return line;
    }

    static boolean uses(List<WallPiece> pieces, String scheme) {
        return pieces.stream().anyMatch(piece -> piece.scheme().equals(scheme));
    }

    static void assertExact(List<ColumnMask.Cell> line, Set<ColumnMask.Cell> gates, List<WallPiece> pieces) {
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
                "С„СЂР°РіРјРµРЅС‚С‹ Р·Р°РєСЂС‹РІР°СЋС‚ СЂРѕРІРЅРѕ Р»РёРЅРёСЋ СЃС‚РµРЅС‹: РґС‹СЂР° вЂ” РїСЂРѕС…РѕРґ РґР»СЏ РјРѕР±РѕРІ, Р»РёС€РЅСЏСЏ РєР»РµС‚РєР° вЂ” Р·Р°Р±РѕСЂ РїРѕРїРµСЂС‘Рє"
                        + " Иначе тест assertExact перестанет ловить эту поломку. При смене assertExact проверь это утверждение.");
        assertTrue(
                covered.values().stream().allMatch(count -> count == 1),
                "С„СЂР°РіРјРµРЅС‚С‹ СЃС‚С‹РєСѓСЋС‚СЃСЏ Р±РµР· РЅР°Р»РѕР¶РµРЅРёР№: РґРІРѕР№РЅР°СЏ РєР»РµС‚РєР° вЂ” РґРІРµ СЃС‚СЂРѕР№РєРё СЃРїРѕСЂСЏС‚ Рѕ Р±Р»РѕРєРµ "
                        + covered
                        + " Иначе тест assertExact перестанет ловить эту поломку. При смене assertExact проверь это утверждение.");
        assertEquals(
                gates,
                gated,
                "РєР°Р»РёС‚РєРё СЃС‚РѕСЏС‚ СЂРѕРІРЅРѕ РІ РєР»РµС‚РєР°С… РєР°Р»РёС‚РѕРє РїР»Р°РЅР°"
                        + " Иначе тест assertExact перестанет ловить эту поломку. При смене assertExact проверь это утверждение.");
    }
}
