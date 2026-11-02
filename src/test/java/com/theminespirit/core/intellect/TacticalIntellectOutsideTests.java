package com.theminespirit.core.intellect;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.coordinator.SpiritSnapshot;
import com.theminespirit.core.coordinator.WorkReservations;
import com.theminespirit.core.intellect.activity.ChopActivity;
import com.theminespirit.core.intellect.activity.DefendActivity;
import com.theminespirit.core.intellect.activity.FarmActivity;
import com.theminespirit.core.intellect.activity.MineActivity;
import com.theminespirit.core.intellect.activity.RestActivity;
import com.theminespirit.core.tactics.Point;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TacticalIntellectOutsideTests {
    private static final UUID FIRST = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID SECOND = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private static TacticalIntellect intellect() {
        return new TacticalIntellect(
                ActivityCatalog.adultsOnly(List.of(
                        new ChopActivity(),
                        new FarmActivity(),
                        new MineActivity(),
                        new RestActivity(),
                        new DefendActivity())),
                StrengthAssessorTests.PROFILES,
                StrengthAssessorTests.ARMOR);
    }

    private static SettlementView needy(List<UUID> ids) {
        return needyAt(ids, 0.2);
    }

    private static SettlementView needyAt(List<UUID> ids, double dayFraction) {
        List<SpiritSnapshot> spirits = ids.stream().map(SettlementViews::spirit).toList();
        return new SettlementView(
                ids.size(), 0, 2, 2, 0, 0, 0, 0, dayFraction, SettlementViews.farmWork(), Forage.NONE, spirits, 100);
    }

    @Test
    void decide_OneWorkerNoHome_MineNotDemanded() {
        TacticalIntellect mind = intellect();

        TacticalDecision decision = mind.decide(
                needy(List.of(FIRST)), new ActivityLedger(), new WorkReservations(), new StrategicQueue(), 100);

        assertFalse(
                has(decision, ActivityId.MINE),
                "Без дома поселения шахты нет: внешнюю добычу MINE не порождают и не отказывают в ней");
        assertTrue(
                decision.refusals().isEmpty(),
                "Отказ без дома — шум в журнале: решения по границе нет, фактически: " + decision.refusals());
    }

    @Test
    void decide_OneWorkerKnownSettlementVein_MineAssigned() {
        TacticalIntellect mind = intellect();
        mind.trackHome(new Point(-181, 73, -520));
        mind.trackVein(new Point(-189, 63, -518));

        TacticalDecision decision = mind.decide(
                needy(List.of(FIRST)), new ActivityLedger(), new WorkReservations(), new StrategicQueue(), 100);

        assertTrue(
                has(decision, ActivityId.MINE),
                "Шахта поселения внутри границы: даже один дух получает добычу, иначе MINE не назначается на старте");
        assertTrue(
                decision.refusals().isEmpty(),
                "Внутри поселения отказа по границе быть не может, фактически: " + decision.refusals());
        assertTrue(
                decision.notes().stream()
                        .anyMatch(note -> note.contains("MINE")
                                && note.contains("радиус")
                                && note.contains("-189.0 63.0 -518.0")),
                "Журнал обязан назвать проверенную границу и цель, фактически: " + decision.notes());
    }

    @Test
    void decide_OneWorkerHomeWithoutVein_MineAssignedAtOwnShaft() {
        TacticalIntellect mind = intellect();
        mind.trackHome(new Point(-181, 73, -520));

        TacticalDecision decision = mind.decide(
                needy(List.of(FIRST)), new ActivityLedger(), new WorkReservations(), new StrategicQueue(), 100);

        assertTrue(
                has(decision, ActivityId.MINE),
                "Без известной жилы работа идёт у ствола поселения под домом: это не выход наружу");
        assertTrue(
                decision.refusals().isEmpty(),
                "У своего ствола отказ по границе невозможен даже в одиночку, фактически: " + decision.refusals());
        assertTrue(
                decision.notes().stream()
                        .anyMatch(note -> note.contains("MINE") && note.contains("-181.0 73.0 -520.0")),
                "Журнал обязан назвать ствол поселения как цель внутри границы, фактически: " + decision.notes());
    }

    @Test
    void decide_OneWorkerKnownVeinEvening_MineAssignedInsideSettlement() {
        TacticalIntellect mind = intellect();
        mind.trackHome(new Point(-181, 73, -520));
        mind.trackVein(new Point(-189, 63, -518));

        TacticalDecision decision = mind.decide(
                needyAt(List.of(FIRST), 0.5), new ActivityLedger(), new WorkReservations(), new StrategicQueue(), 100);

        assertTrue(
                has(decision, ActivityId.MINE),
                "Работа внутри поселения — не выход наружу: ночной запрет на неё не распространяется");
        assertTrue(
                decision.refusals().isEmpty(),
                "Внутри поселения ночного отказа по границе быть не может, фактически: " + decision.refusals());
    }

    @Test
    void decide_TwoWorkersHomeKnown_MineAssignedAtOwnShaft() {
        TacticalIntellect mind = intellect();
        mind.trackHome(new Point(-181, 73, -520));

        TacticalDecision decision = mind.decide(
                needy(List.of(FIRST, SECOND)), new ActivityLedger(), new WorkReservations(), new StrategicQueue(), 100);

        assertTrue(
                has(decision, ActivityId.MINE),
                "Двое духов у ствола поселения получают добычу: шахта своя, группа не нужна");
        assertTrue(
                decision.refusals().isEmpty(),
                "Внутри поселения отказов по границе нет, фактически: " + decision.refusals());
    }

    @Test
    void decide_EveningTwoWorkersNoHome_MineNotDemanded() {
        TacticalDecision decision = intellect()
                .decide(
                        needyAt(List.of(FIRST, SECOND), 0.5),
                        new ActivityLedger(),
                        new WorkReservations(),
                        new StrategicQueue(),
                        100);

        assertFalse(
                has(decision, ActivityId.MINE),
                "Без дома шахты поселения нет: вечернюю внешнюю добычу MINE не порождают");
        assertTrue(
                decision.refusals().isEmpty(),
                "Без цели и дома отказ по границе — шум, фактически: " + decision.refusals());
    }

    @Test
    void decide_OneWorkerNoHomeWithPlayer_MineNotDemanded() {
        TacticalIntellect mind = intellect();
        mind.trackPlayerEscort(true);

        TacticalDecision decision = mind.decide(
                needy(List.of(FIRST)), new ActivityLedger(), new WorkReservations(), new StrategicQueue(), 100);

        assertFalse(
                has(decision, ActivityId.MINE), "Игрок рядом не создаёт шахту поселения: без дома MINE не порождают");
        assertTrue(
                decision.refusals().isEmpty(),
                "Без дома отказ по границе не пишется, фактически: " + decision.refusals());
    }

    private static boolean has(TacticalDecision decision, ActivityId id) {
        return decision.activities().assignments().stream().anyMatch(assignment -> assignment.activity() == id);
    }
}
