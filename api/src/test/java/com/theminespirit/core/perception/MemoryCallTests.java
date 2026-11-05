package com.theminespirit.core.perception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class MemoryCallTests {
    private static final String OVERWORLD = "minecraft:overworld";
    private static final BlockCoord HOME = new BlockCoord(10, 64, 10);
    private static final BlockCoord MINE = new BlockCoord(-20, 12, 30);
    private static final BlockCoord LAVA = new BlockCoord(5, 8, -7);

    private static MemoryPoint point(String name, MemoryType type, BlockCoord pos) {
        return new MemoryPoint(name, type, OVERWORLD, pos);
    }

    private static Sighting sighting(MemoryPoint point) {
        return new Sighting(point.type().id(), point.name(), point.pos(), OVERWORLD);
    }

    private static MemoryStore store(MemoryPoint... points) {
        return new MemoryStore(List.of(points));
    }

    static Stream<Arguments> calls() {
        MemoryPoint home = point("home", MemoryType.BASE, HOME);
        MemoryPoint iron = point("iron", MemoryType.RESOURCE, MINE);
        MemoryPoint lake = point("lava_lake", MemoryType.DANGER, LAVA);
        MemoryPoint movedHome = point("home", MemoryType.BASE, MINE);
        return Stream.of(
                Arguments.of(
                        "remember в пустую память",
                        new MemoryCall.Remember("home", MemoryType.BASE, HOME),
                        MemoryStore.empty(),
                        store(home),
                        List.of(sighting(home))),
                Arguments.of(
                        "remember второй точки",
                        new MemoryCall.Remember("iron", MemoryType.RESOURCE, MINE),
                        store(home),
                        store(home, iron),
                        List.of(sighting(iron))),
                Arguments.of(
                        "remember того же имени заменяет",
                        new MemoryCall.Remember("home", MemoryType.BASE, MINE),
                        store(home),
                        store(movedHome),
                        List.of(sighting(movedHome))),
                Arguments.of(
                        "recall существующей",
                        new MemoryCall.Recall("iron"),
                        store(home, iron),
                        store(home, iron),
                        List.of(sighting(iron))),
                Arguments.of(
                        "recall несуществующей",
                        new MemoryCall.Recall("gold"),
                        store(home, iron),
                        store(home, iron),
                        List.of()),
                Arguments.of(
                        "recall из пустой памяти",
                        new MemoryCall.Recall("home"),
                        MemoryStore.empty(),
                        MemoryStore.empty(),
                        List.of()),
                Arguments.of(
                        "recall_all по типу",
                        new MemoryCall.RecallAll(MemoryType.DANGER),
                        store(home, iron, lake),
                        store(home, iron, lake),
                        List.of(sighting(lake))),
                Arguments.of(
                        "recall_all типа без точек",
                        new MemoryCall.RecallAll(MemoryType.RESOURCE),
                        store(home, lake),
                        store(home, lake),
                        List.of()),
                Arguments.of(
                        "forget существующей",
                        new MemoryCall.Forget("iron"),
                        store(home, iron),
                        store(home),
                        List.of(sighting(iron))),
                Arguments.of(
                        "forget несуществующей", new MemoryCall.Forget("gold"), store(home), store(home), List.of()));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("calls")
    void apply_Call_ChangesStoreAndAnswersAsExpected(
            String scene,
            MemoryCall call,
            MemoryStore before,
            MemoryStore expectedStore,
            List<Sighting> expectedSightings) {
        MemoryOutcome outcome = call.apply(before, OVERWORLD);

        assertEquals(
                expectedStore,
                outcome.store(),
                scene
                        + ": память после действия обязана быть такой, как задумано, иначе точки пропадают или дублируются");
        assertEquals(
                new Finding(call.script(), expectedSightings),
                outcome.finding(),
                scene + ": ответ действия обязан называть найденные точки, а «нет такой» — быть пустым");
    }

    @Test
    void remember_PointOfFullType_FailsWithTypeAndAdvice() {
        List<MemoryPoint> full = new ArrayList<>();
        for (int i = 0; i < MemoryStore.MAX_PER_TYPE; i++) {
            full.add(point("ore_" + i, MemoryType.RESOURCE, new BlockCoord(i, 10, 0)));
        }

        MemoryFullException error = assertThrows(
                MemoryFullException.class,
                () -> new MemoryCall.Remember("one_more", MemoryType.RESOURCE, HOME)
                        .apply(new MemoryStore(full), OVERWORLD),
                "семнадцатая точка типа обязана отклоняться ошибкой, а не вытеснять старую молча");

        assertTrue(
                error.getMessage().contains("resource") && error.getMessage().contains("забудь"),
                "сообщение обязано назвать тип и совет забыть точку, сейчас: " + error.getMessage());
    }

    @Test
    void remember_ReplacingNameInFullType_IsAllowed() {
        List<MemoryPoint> full = new ArrayList<>();
        for (int i = 0; i < MemoryStore.MAX_PER_TYPE; i++) {
            full.add(point("ore_" + i, MemoryType.RESOURCE, new BlockCoord(i, 10, 0)));
        }

        MemoryOutcome outcome =
                new MemoryCall.Remember("ore_3", MemoryType.RESOURCE, HOME).apply(new MemoryStore(full), OVERWORLD);

        assertEquals(
                HOME,
                outcome.store().recall("ore_3").pos(),
                "повтор имени в заполненном типе — замена, а не новая точка: иначе память не обновить без забывания");
        assertEquals(
                MemoryStore.MAX_PER_TYPE,
                outcome.store().recallAll(MemoryType.RESOURCE).size(),
                "после замены число точек типа не растёт");
    }

    @Test
    void remember_Dimension_IsStoredWithPointAndReported() {
        MemoryOutcome outcome = new MemoryCall.Remember("portal", MemoryType.BASE, HOME)
                .apply(MemoryStore.empty(), "minecraft:the_nether");

        assertEquals(
                "minecraft:the_nether",
                outcome.store().recall("portal").dimension(),
                "точка помнит измерение, где её запомнили: recall из другого измерения решает стратег");
        assertEquals(
                "minecraft:the_nether",
                outcome.finding().sightings().get(0).detail(),
                "находка обязана назвать измерение точки");
    }

    static Stream<Arguments> reports() {
        MemoryPoint iron = point("iron", MemoryType.RESOURCE, MINE);
        MemoryPoint lake = point("lava_lake", MemoryType.DANGER, LAVA);
        return Stream.of(
                Arguments.of(
                        "recall найден",
                        new MemoryCall.Recall("iron"),
                        store(iron),
                        "iron: resource (-20, 12, 30) в minecraft:overworld"),
                Arguments.of(
                        "recall не найден", new MemoryCall.Recall("gold"), store(iron), "Точки 'gold' в памяти нет"),
                Arguments.of(
                        "all с точками",
                        new MemoryCall.RecallAll(MemoryType.DANGER),
                        store(iron, lake),
                        "Точек типа danger: 1\nlava_lake: danger (5, 8, -7) в minecraft:overworld"),
                Arguments.of(
                        "all без точек",
                        new MemoryCall.RecallAll(MemoryType.BASE),
                        store(iron, lake),
                        "Точек типа base в памяти нет"),
                Arguments.of(
                        "forget найден",
                        new MemoryCall.Forget("iron"),
                        store(iron),
                        "Забыта точка iron: resource (-20, 12, 30) в minecraft:overworld"),
                Arguments.of(
                        "forget не найден",
                        new MemoryCall.Forget("gold"),
                        store(iron),
                        "Точки 'gold' в памяти нет, забывать нечего"),
                Arguments.of(
                        "remember",
                        new MemoryCall.Remember("home", MemoryType.BASE, HOME),
                        MemoryStore.empty(),
                        "Запомнена точка home: base (10, 64, 10) в minecraft:overworld"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("reports")
    void describe_OutcomeOfCall_ReadsAsOneLinePerPoint(
            String scene, MemoryCall call, MemoryStore before, String expected) {
        MemoryOutcome outcome = call.apply(before, OVERWORLD);

        assertEquals(
                expected,
                call.describe(outcome.finding()),
                scene + ": ответ команды /spirit memory обязан называть имя, тип, позицию и измерение точки, а "
                        + "отсутствие точки — прямо говорить, что её нет");
    }
}
