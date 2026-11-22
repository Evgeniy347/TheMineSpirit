package com.theminespirit.core.movement;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.movement.api.RouteDescent;
import java.util.List;
import org.junit.jupiter.api.Test;

class RouteDescentTests {
    @Test
    void surfaceGoal_PathDropsIntoCave_Refused() {
        String refusal = RouteDescent.refusal(70, 70, List.of(70, 60, 47, 47));

        assertTrue(
                refusal != null && refusal.contains("47"),
                "Маршрут к поверхности не уходит на 23 блока вниз: это пещера, откуда дух не выйдет (observe24 B1): "
                        + refusal
                        + " Иначе тест surfaceGoal_PathDropsIntoCave_Refused перестанет ловить эту поломку. При смене surfaceGoal_PathDropsIntoCave_Refused проверь это утверждение.");
    }

    @Test
    void surfaceGoal_PathStaysOnSurface_Allowed() {
        assertNull(
                RouteDescent.refusal(70, 70, List.of(70, 69, 70, 71)),
                "Обычный рельеф с подъёмом и спуском на блок не запрещён"
                        + " Иначе тест surfaceGoal_PathStaysOnSurface_Allowed перестанет ловить эту поломку. При смене surfaceGoal_PathStaysOnSurface_Allowed проверь это утверждение.");
    }

    @Test
    void lowerGoal_DescentToIt_Allowed() {
        assertNull(
                RouteDescent.refusal(70, 40, List.of(70, 50, 40)),
                "Спуск к цели, которая сама ниже, — работа, а не ловушка: маршрут разрешён"
                        + " Иначе тест lowerGoal_DescentToIt_Allowed перестанет ловить эту поломку. При смене lowerGoal_DescentToIt_Allowed проверь это утверждение.");
    }

    @Test
    void deepStart_PathGoesUp_Allowed() {
        assertNull(
                RouteDescent.refusal(27, 70, List.of(27, 30, 45, 70)),
                "Дух уже в яме и идёт вверх: маршрут спасения не режется по глубине старта"
                        + " Иначе тест deepStart_PathGoesUp_Allowed перестанет ловить эту поломку. При смене deepStart_PathGoesUp_Allowed проверь это утверждение.");
    }

    @Test
    void surfaceGoal_PathDropsTenBlocks_Refused() {
        String refusal = RouteDescent.refusal(70, 70, List.of(70, 60));

        assertTrue(
                refusal != null,
                "Даже 10 блоков ниже цели — уже ловушка: предел " + RouteDescent.MAX_BELOW + " блоков"
                        + " Иначе тест surfaceGoal_PathDropsTenBlocks_Refused перестанет ловить эту поломку. При смене surfaceGoal_PathDropsTenBlocks_Refused проверь это утверждение.");
    }

    @Test
    void swimNodes_BelowShore_Ignored() {
        assertNull(
                RouteDescent.refusalOf(
                        63, 63, List.of(new RouteDescent.Height(62, true), new RouteDescent.Height(54, true))),
                "Переправа вплавь: дно русла ниже берега на 9 блоков — это вода, а не яма, спуск разрешён"
                        + " Иначе тест swimNodes_BelowShore_Ignored перестанет ловить эту поломку. При смене swimNodes_BelowShore_Ignored проверь это утверждение.");
    }

    @Test
    void dryCaveNode_BesideSwimNodes_Refused() {
        String refusal = RouteDescent.refusalOf(
                63, 63, List.of(new RouteDescent.Height(62, true), new RouteDescent.Height(50, false)));

        assertTrue(
                refusal != null && refusal.contains("50"),
                "Сухой узел на y=50 под водой — настоящая пещерная ловушка: отказ сохраняется"
                        + " Иначе тест dryCaveNode_BesideSwimNodes_Refused перестанет ловить эту поломку. При смене dryCaveNode_BesideSwimNodes_Refused проверь это утверждение.");
    }
}
