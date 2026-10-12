package com.theminespirit.core.tactics.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.tactics.RetreatMethod;
import com.theminespirit.core.tactics.TacticalAction;
import com.theminespirit.core.tactics.TacticalOption;
import com.theminespirit.core.tactics.TacticalOutcome;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class RetreatTrackerTests {
    private static final int HOLD = TacticsAdapterSettings.defaults().retreatHoldTicks();
    private static final TacticalOutcome FIGHT = AdapterFixtures.outcome(TacticalOption.FIGHT, TacticalAction.ENGAGE,
            RetreatMethod.NONE);

    @ParameterizedTest(name = "{0}")
    @MethodSource("rows")
    void advance_TrackerAndOutcome_ProducesExpectedState(Row row) {
        RetreatTracker result = row.before().advance(row.outcome(), row.nowTick(), HOLD);

        assertEquals(row.expected(), result,
                "Ход удержания отхода «" + row.name() + "»: способ меняется только по истечении удержания, а простой "
                        + "наступает, когда решение не просит отход и удержание истекло");
    }

    static Stream<Row> rows() {
        TacticalOutcome retreatHome = AdapterFixtures.outcome(TacticalOption.RETREAT, TacticalAction.RETREAT,
                RetreatMethod.HOME);
        TacticalOutcome retreatCover = AdapterFixtures.outcome(TacticalOption.RETREAT, TacticalAction.RETREAT,
                RetreatMethod.COVER);
        TacticalOutcome retreatNone = AdapterFixtures.outcome(TacticalOption.RETREAT, TacticalAction.RETREAT,
                RetreatMethod.NONE);
        TacticalOutcome ignore = AdapterFixtures.outcome(TacticalOption.IGNORE, TacticalAction.CONTINUE,
                RetreatMethod.NONE);
        return Stream.of(
                new Row("простой остаётся простым", RetreatTracker.IDLE, ignore, 0, RetreatTracker.IDLE),
                new Row("бой не начинает отход", RetreatTracker.IDLE, FIGHT, 0, RetreatTracker.IDLE),
                new Row("отход начинается", RetreatTracker.IDLE, retreatHome, 10,
                        new RetreatTracker(RetreatMethod.HOME, 10)),
                new Row("отход без способа не начинается", RetreatTracker.IDLE, retreatNone, 10, RetreatTracker.IDLE),
                new Row("тот же способ держит прогресс", new RetreatTracker(RetreatMethod.HOME, 10), retreatHome, 50,
                        new RetreatTracker(RetreatMethod.HOME, 10)),
                new Row("смена способа до удержания отложена", new RetreatTracker(RetreatMethod.HOME, 10), retreatCover,
                        100, new RetreatTracker(RetreatMethod.HOME, 10)),
                new Row("смена способа на исходе удержания", new RetreatTracker(RetreatMethod.HOME, 10), retreatCover,
                        210, new RetreatTracker(RetreatMethod.COVER, 210)),
                new Row("удержание способа при бое", new RetreatTracker(RetreatMethod.HOME, 10), FIGHT, 100,
                        new RetreatTracker(RetreatMethod.HOME, 10)),
                new Row("за тик до исхода ещё держит", new RetreatTracker(RetreatMethod.HOME, 10), FIGHT, 209,
                        new RetreatTracker(RetreatMethod.HOME, 10)),
                new Row("на исходе удержания простой", new RetreatTracker(RetreatMethod.HOME, 10), FIGHT, 210,
                        RetreatTracker.IDLE),
                new Row("после удержания простой", new RetreatTracker(RetreatMethod.COVER, 0), ignore, 250,
                        RetreatTracker.IDLE));
    }

    @Test
    void advance_ConfiguredSettings_UsesItsHoldTicks() {
        TacticsAdapterSettings settings = TacticsAdapterSettings.defaults();
        RetreatTracker started = RetreatTracker.IDLE.advance(
                AdapterFixtures.outcome(TacticalOption.RETREAT, TacticalAction.RETREAT, RetreatMethod.HOME), 5, settings);

        RetreatTracker held = started.advance(FIGHT, 5 + settings.retreatHoldTicks() - 1, settings);
        RetreatTracker released = started.advance(FIGHT, 5 + settings.retreatHoldTicks(), settings);

        assertEquals(new RetreatTracker(RetreatMethod.HOME, 5), held,
                "До retreatHoldTicks из настроек отход ещё удерживается: иначе дух бросит манёвр на полпути");
        assertEquals(RetreatTracker.IDLE, released,
                "На исходе retreatHoldTicks из настроек простой наступает: удержание ограничено настройкой, а не вечно");
    }

    @Test
    void progressTicks_Retreating_CountsFromStart() {
        RetreatTracker tracker = new RetreatTracker(RetreatMethod.HOME, 40);

        assertEquals(15, tracker.progressTicks(55),
                "Прогресс отхода считается от тика старта: по нему решается, пора ли менять способ");
    }

    @Test
    void progressTicks_Idle_IsZero() {
        assertEquals(0, RetreatTracker.IDLE.progressTicks(123),
                "У простоя нет прогресса отхода: ноль в любом тике");
    }

    @Test
    void retreating_Idle_IsFalse() {
        assertFalse(RetreatTracker.IDLE.retreating(),
                "Простой не считается отходом: иначе исполнитель начнёт манёвр без способа");
        assertTrue(new RetreatTracker(RetreatMethod.AWAY, 7).retreating(),
                "Исполняемый способ отхода означает отход идёт");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidInputs")
    void tracker_InvalidInput_IsRejected(String field, Executable build) {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, build,
                "Ошибочный вход отхода обязан падать сразу с именем поля: иначе дух пойдёт по фиктивному манёвру");

        assertTrue(error.getMessage().contains(field),
                "Сообщение называет поле " + field + "; получили: " + error.getMessage());
    }

    static Stream<Arguments> invalidInputs() {
        return Stream.of(
                Arguments.of("method", (Executable) () -> new RetreatTracker(null, 0)),
                Arguments.of("sinceTick", (Executable) () -> new RetreatTracker(RetreatMethod.HOME, -1)),
                Arguments.of("sinceTick", (Executable) () -> new RetreatTracker(RetreatMethod.NONE, 5)),
                Arguments.of("outcome", (Executable) () -> RetreatTracker.IDLE.advance(null, 0, HOLD)),
                Arguments.of("holdTicks", (Executable) () -> RetreatTracker.IDLE.advance(FIGHT, 0, 0)),
                Arguments.of("nowTick",
                        (Executable) () -> new RetreatTracker(RetreatMethod.HOME, 10).advance(FIGHT, 5, HOLD)),
                Arguments.of("nowTick",
                        (Executable) () -> new RetreatTracker(RetreatMethod.HOME, 10).progressTicks(5)));
    }

    private record Row(String name, RetreatTracker before, TacticalOutcome outcome, long nowTick,
            RetreatTracker expected) {
        @Override
        public String toString() {
            return name;
        }
    }
}
