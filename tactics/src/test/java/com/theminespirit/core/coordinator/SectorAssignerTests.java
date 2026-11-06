package com.theminespirit.core.coordinator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.tactics.Point;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SectorAssignerTests {
    private static final UUID ALPHA = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID BETA = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID GAMMA = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final Point CENTER = new Point(0, 64, 0);

    @Test
    void assign_NoDefenders_ReturnsEmpty() {
        assertTrue(
                new SectorAssigner().assign(CENTER, 16, List.of()).isEmpty(),
                "Нет защитников — нет постов: пустой вход не даёт вырожденных секторов");
    }

    @Test
    void assign_OneDefender_ReturnsSinglePost() {
        List<DefenseAssignment> assignments = new SectorAssigner().assign(CENTER, 16, List.of(ALPHA));

        assertEquals(1, assignments.size(), "Один защитник получает ровно один пост");
        assertEquals(ALPHA, assignments.get(0).defender(), "Защитник получает свой пост");
        assertEquals(
                16,
                assignments.get(0).post().distanceTo(CENTER),
                1e-9,
                "Пост одного защитника на окружности радиуса: он не стоит в центре");
    }

    @Test
    void assign_TwoDefenders_ReturnsDifferentPosts() {
        List<DefenseAssignment> assignments = new SectorAssigner().assign(CENTER, 16, List.of(ALPHA, BETA));

        assertEquals(2, assignments.size(), "Два защитника получают два поста");
        assertNotEquals(
                assignments.get(0).post(),
                assignments.get(1).post(),
                "Посты защитников не совпадают: иначе двое толпятся на одном секторе");
    }

    @Test
    void assign_ThreeDefenders_ReturnsThreeDifferentPosts() {
        List<DefenseAssignment> assignments = new SectorAssigner().assign(CENTER, 16, List.of(ALPHA, BETA, GAMMA));

        assertEquals(3, assignments.size(), "Три защитника получают три поста");
        assertNotEquals(assignments.get(0).post(), assignments.get(1).post(), "Первый и второй посты различны");
        assertNotEquals(assignments.get(1).post(), assignments.get(2).post(), "Второй и третий посты различны");
        assertNotEquals(assignments.get(0).post(), assignments.get(2).post(), "Первый и третий посты различны");
    }

    @Test
    void assign_CenterAtOrigin_PostIsNotDegenerate() {
        List<DefenseAssignment> assignments = new SectorAssigner().assign(new Point(0, 64, 0), 4, List.of(ALPHA));

        assertNotEquals(
                new Point(0, 64, 0),
                assignments.get(0).post(),
                "Пост не вырождается в центр: защитник стоит на окружности, а не в защищаемой точке");
    }

    @Test
    void assign_NonPositiveRadius_Throws() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new SectorAssigner().assign(CENTER, 0, List.of(ALPHA)),
                "Нулевой радиус вырождает сектора: все посты слипнутся в центр");
        assertThrows(
                IllegalArgumentException.class,
                () -> new SectorAssigner().assign(CENTER, -4, List.of(ALPHA)),
                "Отрицательный радиус не имеет геометрического смысла");
    }

    @Test
    void assign_NullCenter_Throws() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new SectorAssigner().assign(null, 16, List.of(ALPHA)),
                "Круг без центра не построить: центр обязателен");
    }

    @Test
    void assign_NullDefenders_Throws() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new SectorAssigner().assign(CENTER, 16, null),
                "null вместо списка защитников не говорит, есть ли защитники: передавайте пустой список");
    }

    @Test
    void assign_NullDefenderInList_Throws() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new SectorAssigner().assign(CENTER, 16, Arrays.asList(ALPHA, null)),
                "Защитник без id не получает пост: список обязан быть целиком из id");
    }

    @Test
    void assign_SameInputTwice_ReturnsSameResult() {
        List<UUID> defenders = List.of(GAMMA, ALPHA, BETA);

        assertEquals(
                new SectorAssigner().assign(CENTER, 16, defenders),
                new SectorAssigner().assign(CENTER, 16, defenders),
                "Тот же вход даёт тот же выход: сектора не зависят от порядка наблюдения защитников");
    }
}
