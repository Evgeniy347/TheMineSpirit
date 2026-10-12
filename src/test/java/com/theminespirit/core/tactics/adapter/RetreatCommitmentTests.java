package com.theminespirit.core.tactics.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.theminespirit.core.tactics.RetreatMethod;
import com.theminespirit.core.tactics.TacticalAction;
import com.theminespirit.core.tactics.TacticalOption;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class RetreatCommitmentTests {
    private static final TacticsAdapterSettings SETTINGS = TacticsAdapterSettings.defaults();
    private static final long START = 100;

    record Row(String name, RetreatTracker current, TacticalOption option, RetreatMethod method, long now,
            boolean building, RetreatTracker expected) {
    }

    static Stream<Row> rows() {
        RetreatTracker home = new RetreatTracker(RetreatMethod.HOME, START);
        RetreatTracker away = new RetreatTracker(RetreatMethod.AWAY, START);
        RetreatTracker barricade = new RetreatTracker(RetreatMethod.BARRICADE, START);
        return Stream.of(
                new Row("из простоя: RETREAT/HOME начинает отход", RetreatTracker.IDLE, TacticalOption.RETREAT,
                        RetreatMethod.HOME, START, false, home),
                new Row("из простоя: FIGHT отхода не начинает", RetreatTracker.IDLE, TacticalOption.FIGHT,
                        RetreatMethod.NONE, START, false, RetreatTracker.IDLE),
                new Row("бег прерывается боем", home, TacticalOption.FIGHT, RetreatMethod.NONE, START + 10, false,
                        RetreatTracker.IDLE),
                new Row("постройка бой не прерывает", barricade, TacticalOption.FIGHT, RetreatMethod.NONE, START + 10,
                        true, barricade),
                new Row("запасной AWAY вытесняется найденной водой", away, TacticalOption.RETREAT, RetreatMethod.WATER,
                        START + 10, false, new RetreatTracker(RetreatMethod.WATER, START + 10)),
                new Row("AWAY остаётся, пока лучшего способа нет", away, TacticalOption.RETREAT, RetreatMethod.AWAY,
                        START + 10, false, away),
                new Row("HOME держится против воды в пределах удержания", home, TacticalOption.RETREAT,
                        RetreatMethod.WATER, START + 10, false, home),
                new Row("HOME уступает воде после удержания", home, TacticalOption.RETREAT, RetreatMethod.WATER,
                        START + SETTINGS.retreatHoldTicks(), false,
                        new RetreatTracker(RetreatMethod.WATER, START + SETTINGS.retreatHoldTicks())),
                new Row("IGNORE в пределах удержания сохраняет отход", home, TacticalOption.IGNORE, RetreatMethod.NONE,
                        START + 50, false, home),
                new Row("IGNORE после удержания снимает отход", home, TacticalOption.IGNORE, RetreatMethod.NONE,
                        START + SETTINGS.retreatHoldTicks(), false, RetreatTracker.IDLE));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("rows")
    void next_Outcome_GivesExpectedCommitment(Row row) {
        RetreatTracker next = RetreatCommitment.next(row.current(),
                AdapterFixtures.outcome(row.option(), actionOf(row.option()), row.method()), row.now(), SETTINGS,
                row.building());

        assertEquals(row.expected(), next, "Обязательство отхода («" + row.name() + "»): бой прерывает бег, но не "
                + "постройку; запасной AWAY уступает найденному лучшему способу; остальные держатся "
                + SETTINGS.retreatHoldTicks() + " тиков");
    }

    @Test
    void next_NullOutcome_Rejected() {
        assertThrows(IllegalArgumentException.class,
                () -> RetreatCommitment.next(RetreatTracker.IDLE, null, 0, SETTINGS, false),
                "Без решения ядра обязательство не обновить: ошибка вызова, а не молчаливый простой");
    }

    private static TacticalAction actionOf(TacticalOption option) {
        return switch (option) {
            case FIGHT -> TacticalAction.ENGAGE;
            case RETREAT -> TacticalAction.RETREAT;
            case IGNORE -> TacticalAction.CONTINUE;
        };
    }
}
