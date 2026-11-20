package com.theminespirit.core.town;

import static com.theminespirit.core.town.WallLayoutTests.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class WallLayoutTestsExtraTests {

    @Test
    void demoWall_ClosedRing_HasBothCornerKindsAndWicket() {
        FenceLine line = WallDemo.line();
        List<WallPiece> pieces = WallDemo.pieces(PRESETS);

        assertExact(line.posts(), Set.of(line.posts().get(WallDemo.GATE)), pieces);
        assertEquals(
                2,
                count(pieces, "fence_corner"),
                "Витрина обязана показать два угла 90°: на одном угле не увидеть, что линия поворачивает в обе стороны: "
                        + pieces
                        + " Иначе тест demoWall_ClosedRing_HasBothCornerKindsAndWicket перестанет ловить эту поломку. При смене demoWall_ClosedRing_HasBothCornerKindsAndWicket проверь это утверждение.");
        assertEquals(
                1,
                count(pieces, "fence_corner45_left"),
                "Витрина обязана показать угол 45° влево: иначе 45° в демо нет: " + pieces
                        + " При смене demoWall_ClosedRing_HasBothCornerKindsAndWicket проверь это утверждение.");
        assertEquals(
                1,
                count(pieces, "fence_corner45_right"),
                "Витрина обязана показать угол 45° вправо: левый и правый — разные схемы: " + pieces
                        + " Иначе тест demoWall_ClosedRing_HasBothCornerKindsAndWicket перестанет ловить эту поломку. При смене demoWall_ClosedRing_HasBothCornerKindsAndWicket проверь это утверждение.");
        assertEquals(
                1,
                count(pieces, "fence_diagonal"),
                "Витрина обязана показать участок диагонали, а не только повороты: " + pieces
                        + " Иначе тест demoWall_ClosedRing_HasBothCornerKindsAndWicket перестанет ловить эту поломку. При смене demoWall_ClosedRing_HasBothCornerKindsAndWicket проверь это утверждение.");
        assertEquals(
                1,
                count(pieces, "fence_wicket"),
                "В витрине одни ворота: калитка — единственный проход в замкнутом контуре: " + pieces
                        + " Иначе тест demoWall_ClosedRing_HasBothCornerKindsAndWicket перестанет ловить эту поломку. При смене demoWall_ClosedRing_HasBothCornerKindsAndWicket проверь это утверждение.");
        assertRing(line.posts());
    }

    @Test
    void demoWall_Contour_IsCompact() {
        List<ColumnMask.Cell> posts = WallDemo.line().posts();
        int minX = posts.stream().mapToInt(ColumnMask.Cell::x).min().orElseThrow();
        int maxX = posts.stream().mapToInt(ColumnMask.Cell::x).max().orElseThrow();
        int minZ = posts.stream().mapToInt(ColumnMask.Cell::z).min().orElseThrow();
        int maxZ = posts.stream().mapToInt(ColumnMask.Cell::z).max().orElseThrow();

        assertTrue(
                maxX - minX + 1 <= 9 && maxZ - minZ + 1 <= 7 && posts.size() <= 30,
                "Витрина ограды обязана быть компактной и строиться рядом с остальным демо: получено "
                        + (maxX - minX + 1) + "×" + (maxZ - minZ + 1) + ", клеток " + posts.size()
                        + " Иначе тест demoWall_Contour_IsCompact перестанет ловить эту поломку. При смене demoWall_Contour_IsCompact проверь это утверждение.");
    }

    @Test
    void wall_StoneStage_HasNo45Schemes() {
        assertTrue(
                StageSchemes.FENCE.contains("fence_diagonal")
                        && StageSchemes.FENCE.contains("fence_corner45_left")
                        && StageSchemes.FENCE.contains("fence_corner45_right"),
                "Уровень забора обязан иметь диагональ и оба угла 45°: на нём и держится витрина"
                        + " Иначе тест wall_StoneStage_HasNo45Schemes перестанет ловить эту поломку. При смене wall_StoneStage_HasNo45Schemes проверь это утверждение.");
        assertFalse(
                StageSchemes.WALL.stream()
                        .anyMatch(scheme -> scheme.contains("diagonal") || scheme.contains("corner45")),
                "У каменной стены этапа 2 нет схем диагонали и угла 45°: витрину 45° строить не из чего, поэтому "
                        + "витрина — забор этапа 0"
                        + " Иначе тест wall_StoneStage_HasNo45Schemes перестанет ловить эту поломку. При смене wall_StoneStage_HasNo45Schemes проверь это утверждение.");
    }
}
