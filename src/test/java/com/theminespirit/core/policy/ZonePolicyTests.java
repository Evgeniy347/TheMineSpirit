package com.theminespirit.core.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.theminespirit.core.observation.BlockCoord;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ZonePolicyTests {
    private static final WorkZone ZONE = new WorkZone(new BlockCoord(100, 64, 100), 10);

    static Stream<Arguments> targets() {
        return Stream.of(
                Arguments.of("центр", new BlockCoord(100, 64, 100), 0),
                Arguments.of("на восточном краю x=110", new BlockCoord(110, 64, 100), 0),
                Arguments.of("за восточным краем x=111", new BlockCoord(111, 64, 100), 1),
                Arguments.of("на западном краю x=90", new BlockCoord(90, 64, 100), 0),
                Arguments.of("за западным краем x=89", new BlockCoord(89, 64, 100), 1),
                Arguments.of("на верхнем краю y=74", new BlockCoord(100, 74, 100), 0),
                Arguments.of("за верхним краем y=75", new BlockCoord(100, 75, 100), 1),
                Arguments.of("на нижнем краю y=54", new BlockCoord(100, 54, 100), 0),
                Arguments.of("за нижним краем y=53", new BlockCoord(100, 53, 100), 1),
                Arguments.of("угол зоны", new BlockCoord(110, 74, 110), 0),
                Arguments.of("за углом по двум осям считает наибольший выход", new BlockCoord(113, 64, 120), 10),
                Arguments.of("далеко", new BlockCoord(150, 64, 100), 40));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("targets")
    void checkTarget_Position_IsAllowedOnlyInsideTheBox(String name, BlockCoord pos, int excess) {
        ZonePolicy.TargetCheck check = ZonePolicy.checkTarget(pos, ZONE);

        if (excess == 0) {
            assertEquals(
                    new ZonePolicy.Allowed(),
                    check,
                    "Цель «" + name + "» лежит в зоне радиуса 10 (границы включены) и должна быть разрешена");
        } else {
            assertEquals(
                    new ZonePolicy.Refused("вне рабочей зоны: цель " + pos.x() + " " + pos.y() + " " + pos.z() + " на "
                            + excess + " блоков за границей зоны (центр 100 64 100, радиус 10)"),
                    check,
                    "Цель «" + name
                            + "» вне зоны: отказ называет цель и расстояние за границей, по нему итог задачи понятен игроку");
        }
    }

    static Stream<Arguments> drifts() {
        return Stream.of(
                Arguments.of("внутри зоны", new BlockCoord(105, 64, 95), null),
                Arguments.of("вышел на 1", new BlockCoord(111, 64, 100), null),
                Arguments.of("вышел на допуск 3", new BlockCoord(113, 64, 100), null),
                Arguments.of("вышел на допуск плюс 1", new BlockCoord(114, 64, 100), new BlockCoord(110, 64, 100)),
                Arguments.of("вышел вверх на 6", new BlockCoord(100, 80, 100), new BlockCoord(100, 74, 100)),
                Arguments.of("вышел за угол", new BlockCoord(120, 64, 125), new BlockCoord(110, 64, 110)),
                Arguments.of("вышел на запад", new BlockCoord(80, 60, 100), new BlockCoord(90, 60, 100)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("drifts")
    void evaluateInside_Position_ReturnsToTheNearestInsidePointBeyondMargin(
            String name, BlockCoord pos, BlockCoord expectedReturn) {
        PolicyVerdict verdict = ZonePolicy.evaluateInside(pos, ZONE);

        if (expectedReturn == null) {
            assertEquals(
                    new PolicyVerdict.Clear(),
                    verdict,
                    "Позиция «" + name + "» в зоне или в допуске 3 блока: возвращать духа рано");
        } else {
            PolicyVerdict.Reflex reflex = (PolicyVerdict.Reflex) verdict;
            assertEquals(
                    PolicyId.WORK_ZONE,
                    reflex.policy(),
                    "Позиция «" + name + "»: рефлекс возврата принадлежит политике WORK_ZONE");
            assertEquals(
                    new ReflexAction.ReturnToZone(expectedReturn),
                    reflex.action(),
                    "Позиция «" + name + "»: возврат в ближайшую точку зоны");
        }
    }

    @Test
    void margin_Constant_IsThreeBlocks() {
        assertEquals(
                3,
                ZonePolicy.ZONE_MARGIN,
                "Допуск 3 блока: дух, которого сносит течением или отбросило ударом, не дёргается назад на каждом шаге");
    }

    @Test
    void workZone_ZeroRadius_ThrowsWithValue() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> new WorkZone(new BlockCoord(0, 0, 0), 0),
                "зона радиуса 0 запрещает любое движение");

        assertEquals(
                "Радиус рабочей зоны 0 меньше 1: зона радиуса 0 запретила бы духу любое движение, для снятия зоны используйте «zone clear»",
                error.getMessage(),
                "ошибка называет значение и подсказывает, как снять зону");
    }
}
