package com.theminespirit.core.intellect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.coordinator.WorkKind;
import com.theminespirit.core.coordinator.WorkReservations;
import com.theminespirit.core.coordinator.WorkTarget;
import com.theminespirit.core.intellect.activity.ChopActivity;
import com.theminespirit.core.intellect.activity.DefendActivity;
import com.theminespirit.core.intellect.activity.FarmActivity;
import com.theminespirit.core.intellect.activity.MineActivity;
import com.theminespirit.core.intellect.activity.RestActivity;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.tactics.Point;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TacticalIntellectTests {
    private static final UUID FIRST = StrengthAssessorTests.FIRST;
    private static final UUID SECOND = StrengthAssessorTests.SECOND;
    private static final UUID ENEMY = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    private static TacticalIntellect intellect() {
        return new TacticalIntellect(
                ActivityCatalog.adultsOnly(List.of(new ChopActivity(), new FarmActivity(), new MineActivity(),
                        new RestActivity(), new DefendActivity())),
                StrengthAssessorTests.PROFILES, StrengthAssessorTests.ARMOR);
    }

    private static SettlementView calm() {
        return SettlementViews.withSpirits(
                List.of(SettlementViews.spirit(FIRST), SettlementViews.spirit(SECOND)));
    }

    @Test
    void decide_StrategicOrder_BeatsActivity() {
        TacticalIntellect mind = intellect();
        StrategicQueue queue = new StrategicQueue();
        queue.enqueue(new StrategicOrder(FIRST, "s-1", new ScriptCall("wait", Map.of()), 0, 6000));

        TacticalDecision decision = mind.decide(calm(), new ActivityLedger(), new WorkReservations(), queue, 100);

        assertEquals(List.of("s-1"),
                decision.orders().stream().map(StrategicOrder::requestId).toList(),
                "Задание стратега исполняется первым: иначе расчёт спроса перебьёт приказ");
        assertTrue(decision.activities().assignments().stream().noneMatch(a -> a.spirit().equals(FIRST)),
                "Заказанный дух не получает деятельность: иначе у него будет и приказ, и работа");
        assertEquals(Optional.empty(), decision.expedition(),
                "Без жилы экспедиции нет: иначе команда уйдёт вслепую");
    }

    @Test
    void decide_ThreatPresent_VerdictDisplacesActivity() {
        TacticalIntellect mind = intellect();
        mind.trackEnemies(List.of(StrengthAssessorTests.zombie(ENEMY, 5)));
        SettlementView danger = new SettlementView(2, 0, 2, 2, 64, 32, 2, 1, 0.2, SettlementViews.farmWork(),
                List.of(StrengthAssessorTests.defender(FIRST, 1, 1), SettlementViews.spirit(SECOND)), 100);
        ActivityLedger ledger = new ActivityLedger();
        ledger.assign(FIRST, ActivityId.CHOP, 0, 3.0);

        TacticalDecision decision =
                mind.decide(danger, ledger, new WorkReservations(), new StrategicQueue(), 100);

        assertTrue(decision.strengths().stream()
                .anyMatch(v -> v.spirit().equals(FIRST) && v.action() == StrengthAction.FIGHT),
                "Угрожаемый дух получает боевой вердикт: иначе оценка сил не вытеснит его работу");
        assertTrue(decision.activities().assignments().stream().noneMatch(a -> a.spirit().equals(FIRST)),
                "Деятельность угрожаемого снята: иначе он продолжит рубить под ударом");
        assertEquals(Optional.empty(), ledger.of(FIRST),
                "Учёт очищен: иначе снятая деятельность удержит место в ledger");
        assertTrue(decision.activities().dropped().stream().noneMatch(id -> id.equals(FIRST)),
                "Вытесненный не числится выбывшим: он в поселении, dropped — только для ушедших");
    }

    @Test
    void decide_CalmSettlement_ActivitiesWithoutStrengths() {
        TacticalDecision decision =
                intellect().decide(calm(), new ActivityLedger(), new WorkReservations(), new StrategicQueue(), 100);

        assertTrue(decision.orders().isEmpty(), "Без приказов стратега очередь пуста: иначе phantom-приказ");
        assertTrue(decision.strengths().isEmpty(), "Без врагов вердиктов нет: иначе мирные духи получат бой");
        assertEquals(2, decision.activities().assignments().size(),
                "Спокойные духи получают деятельности: иначе поселение встанет без угроз");
        assertEquals(Optional.empty(), decision.expedition(),
                "Без жилы экспедиции нет: иначе команда уйдёт вслепую");
    }

    @Test
    void decide_VeinAndNeed_ExpeditionPlanned() {
        TacticalIntellect mind = intellect();
        mind.trackVein(new Point(100, 40, 100));
        SettlementView needy = SettlementViews.withSpirits(
                List.of(SettlementViews.spirit(FIRST), SettlementViews.spirit(SECOND), SettlementViews.spirit(
                        UUID.fromString("33333333-3333-3333-3333-333333333333"))));

        TacticalDecision decision = mind.decide(
                new SettlementView(3, 0, 2, 2, 0, 0, 0, 0, 0.2, SettlementViews.farmWork(), needy.spirits(), 100),
                new ActivityLedger(), new WorkReservations(), new StrategicQueue(), 100);

        assertTrue(decision.expedition().isPresent(),
                "При нужде в руде и известной жиле экспедиция планируется: иначе шахта не закроет нужду");
        assertTrue(decision.expedition().orElseThrow().team().size() >= 2,
                "Команда экспедиции — минимум двое: в одиночку в шахту не ходят");
    }

    @Test
    void decide_DepartedSpirit_ReservationsReleased() {
        WorkReservations reservations = new WorkReservations();
        WorkTarget target = new WorkTarget(WorkKind.TREE, new BlockCoord(4, 64, 2));
        reservations.reserve(target, FIRST, 0, 100000);
        ActivityLedger ledger = new ActivityLedger();
        ledger.assign(FIRST, ActivityId.CHOP, 0, 3.0);
        SettlementView without = SettlementViews.withSpirits(List.of(SettlementViews.spirit(SECOND)));

        TacticalDecision decision =
                intellect().decide(without, ledger, reservations, new StrategicQueue(), 100);

        assertEquals(List.of(FIRST), decision.activities().dropped(),
                "Выбывший попадает в dropped: иначе его занятие удержит место в учёте");
        assertEquals(Optional.empty(), reservations.owner(target),
                "Брони выбывшего сняты: иначе его цель навсегда занята");
    }

    @Test
    void decide_NullInputs_Throw() {
        TacticalIntellect mind = intellect();
        SettlementView view = calm();

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> mind.decide(null, new ActivityLedger(), new WorkReservations(), new StrategicQueue(), 100),
                "Решение без обстановки не принимается: зависит от нужд поселения");

        assertTrue(error.getMessage().contains("обстановки"),
                "Сообщение называет причину: при падении видно, чего не хватает решению");
        assertThrows(IllegalArgumentException.class,
                () -> mind.decide(view, new ActivityLedger(), new WorkReservations(), null, 100),
                "Решение без очереди теряет приказы: пустая очередь — не null");
    }
}
