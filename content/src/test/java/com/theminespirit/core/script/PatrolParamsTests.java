package com.theminespirit.core.script;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.movement.RoutePlan;
import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PatrolParamsTests {
    @Test
    void from_AllParamsPresent_ReturnsParams() {
        PatrolParams params =
                PatrolParams.from(Map.of("points", List.of("0,64,0", "8,64,8"), "mode", "ping_pong", "loops", 2));

        assertEquals(
                new PatrolParams(
                        List.of(new BlockCoord(0, 64, 0), new BlockCoord(8, 64, 8)), RoutePlan.Mode.PING_PONG, 2),
                params,
                "patrol обязан сохранить точки, режим и число кругов: по ним строится маршрут обхода"
                        + " Иначе тест from_AllParamsPresent_ReturnsParams перестанет ловить эту поломку. При смене from_AllParamsPresent_ReturnsParams проверь это утверждение.");
    }

    @Test
    void from_MissingPoints_IsRejected() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> PatrolParams.from(Map.of("mode", "loop", "loops", 1)),
                "patrol без точек обязан отказывать: маршруту без точек некуда идти"
                        + " Иначе тест from_MissingPoints_IsRejected перестанет ловить эту поломку. При смене from_MissingPoints_IsRejected проверь это утверждение.");

        assertTrue(
                error.getMessage().contains("points"),
                "Отказ обязан называть недостающие точки 'points'. Фактическое: " + error.getMessage()
                        + " Иначе тест from_MissingPoints_IsRejected перестанет ловить эту поломку. При смене from_MissingPoints_IsRejected проверь это утверждение.");
    }

    @Test
    void from_EmptyPoints_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> PatrolParams.from(Map.of("points", List.of(), "mode", "loop", "loops", 1)),
                "patrol с пустым маршрутом обязан отказывать: обход нуля точек завершается сразу, "
                        + "а стратег решит, что дух всё обошёл"
                        + " Иначе тест from_EmptyPoints_IsRejected перестанет ловить эту поломку. При смене from_EmptyPoints_IsRejected проверь это утверждение.");
    }

    @Test
    void from_TooManyPoints_IsRejected() {
        List<String> points = new ArrayList<>();
        for (int i = 0; i < 65; i++) {
            points.add(i + ",64,0");
        }

        assertThrows(
                IllegalArgumentException.class,
                () -> PatrolParams.from(Map.of("points", points, "mode", "loop", "loops", 1)),
                "Маршрут длиннее 64 точек обязан отвергаться: длинный маршрут режется на части, "
                        + "иначе патруль уходит из тикающей зоны"
                        + " При смене from_TooManyPoints_IsRejected проверь это утверждение.");
    }

    @Test
    void from_BadPointFormat_IsRejected() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> PatrolParams.from(Map.of("points", List.of("10,64"), "mode", "loop", "loops", 1)),
                "Точка не вида 'x,y,z' обязана отвергаться: по ней патруль не знает, куда идти"
                        + " Иначе тест from_BadPointFormat_IsRejected перестанет ловить эту поломку. При смене from_BadPointFormat_IsRejected проверь это утверждение.");

        assertTrue(
                error.getMessage().contains("10,64"),
                "Отказ обязан называть плохую точку. Фактическое: " + error.getMessage()
                        + " Иначе тест from_BadPointFormat_IsRejected перестанет ловить эту поломку. При смене from_BadPointFormat_IsRejected проверь это утверждение.");
    }

    @Test
    void from_PointOutsideWorld_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> PatrolParams.from(Map.of("points", List.of("0,400,0"), "mode", "loop", "loops", 1)),
                "Точка выше границы мира обязана отвергаться: патрулю туда не дойти"
                        + " Иначе тест from_PointOutsideWorld_IsRejected перестанет ловить эту поломку. При смене from_PointOutsideWorld_IsRejected проверь это утверждение.");
    }

    @Test
    void from_UnknownMode_IsRejectedWithAllowedList() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> PatrolParams.from(Map.of("points", List.of("0,64,0"), "mode", "circle", "loops", 1)),
                "Неизвестный режим маршрута обязан отвергаться: без него непонятно, кругом идти или туда-обратно"
                        + " Иначе тест from_UnknownMode_IsRejectedWithAllowedList перестанет ловить эту поломку. При смене from_UnknownMode_IsRejectedWithAllowedList проверь это утверждение.");

        assertTrue(
                error.getMessage().contains("circle"),
                "Отказ обязан называть неизвестный режим 'circle'. Фактическое: " + error.getMessage()
                        + " Иначе тест from_UnknownMode_IsRejectedWithAllowedList перестанет ловить эту поломку. При смене from_UnknownMode_IsRejectedWithAllowedList проверь это утверждение.");
        assertTrue(
                error.getMessage().contains("loop") && error.getMessage().contains("ping_pong"),
                "Отказ обязан перечислять допустимые режимы loop и ping_pong. Фактическое: " + error.getMessage()
                        + " Иначе тест from_UnknownMode_IsRejectedWithAllowedList перестанет ловить эту поломку. При смене from_UnknownMode_IsRejectedWithAllowedList проверь это утверждение.");
    }

    @Test
    void from_LoopsOutOfRange_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> PatrolParams.from(Map.of("points", List.of("0,64,0"), "mode", "loop", "loops", 17)),
                "Число кругов больше 16 обязано отвергаться: лимит держит патруль в разумном времени"
                        + " Иначе тест from_LoopsOutOfRange_IsRejected перестанет ловить эту поломку. При смене from_LoopsOutOfRange_IsRejected проверь это утверждение.");
    }

    @Test
    void from_NullParams_IsRejected() {
        assertThrows(
                NullPointerException.class,
                () -> PatrolParams.from(null),
                "PatrolParams.from(null) обязан отказывать: без карты параметров маппинг только читает состояние мира"
                        + " Иначе тест from_NullParams_IsRejected перестанет ловить эту поломку. При смене from_NullParams_IsRejected проверь это утверждение.");
    }
}
