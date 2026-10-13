package com.theminespirit.core.intellect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.coordinator.SpiritRole;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ActivityAssignerTests {
    private static final UUID FIRST = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID SECOND = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID THIRD = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final ActivityAssigner ASSIGNER = new ActivityAssigner();

    private static List<ActivityDemand> demands(double chop, double farm, double mine, double rest, double defend) {
        return List.of(new ActivityDemand(ActivityId.CHOP, chop), new ActivityDemand(ActivityId.FARM, farm),
                new ActivityDemand(ActivityId.MINE, mine), new ActivityDemand(ActivityId.REST, rest),
                new ActivityDemand(ActivityId.DEFEND, defend));
    }

    private static void syncLedger(ActivityLedger ledger, ActivityPlan plan) {
        for (ActivityAssignment assignment : plan.assignments()) {
            ledger.assign(assignment.spirit(), assignment.activity(), assignment.since(), assignment.demand());
        }
    }

    @Test
    void assign_EqualDemands_ThreeDifferent() {
        SettlementView view = SettlementViews.withSpirits(
                List.of(SettlementViews.spirit(FIRST), SettlementViews.spirit(SECOND), SettlementViews.spirit(THIRD)));

        ActivityPlan plan = ASSIGNER.assign(view,
                List.of(new ActivityDemand(ActivityId.CHOP, 5), new ActivityDemand(ActivityId.FARM, 5),
                        new ActivityDemand(ActivityId.MINE, 5)),
                new ActivityLedger(), 100);

        assertEquals(3, plan.assignments().size(),
                "Три духа разбирают три равные работы: иначе толпа стоит на одной");
        assertEquals(3,
                plan.assignments().stream().map(ActivityAssignment::activity).distinct().count(),
                "Равный спрос разводит духов по разным деятельностям: штраф за толпу мягко распределяет");
    }

    @Test
    void assign_ThreatPresent_DefendOnlyThreatened() {
        SettlementView view = new SettlementView(3, 0, 2, 2, 64, 32, 2, 1, 0.2,
                List.of(SettlementViews.threatenedSpirit(FIRST), SettlementViews.spirit(SECOND),
                        SettlementViews.spirit(THIRD)),
                100);

        ActivityPlan plan =
                ASSIGNER.assign(view, demands(3, 1, 1, 1, 10), new ActivityLedger(), 100);

        assertEquals(1, plan.assignments().stream().filter(a -> a.activity() == ActivityId.DEFEND).count(),
                "Против одной угрозы защищается один дух: остальные работают, а не толпятся в обороне");
        assertEquals(ActivityId.DEFEND,
                plan.assignments().stream().filter(a -> a.spirit().equals(FIRST)).findFirst().orElseThrow().activity(),
                "Защищается дух под угрозой: иначе оборону держит тот, кого не бьют");
    }

    @Test
    void assign_SameConditionsNextTick_HoldsAssignments() {
        SettlementView view = SettlementViews.withSpirits(
                List.of(SettlementViews.spirit(FIRST), SettlementViews.spirit(SECOND)));
        List<ActivityDemand> current = demands(5, 4, 3, 2, 0.5);
        ActivityLedger ledger = new ActivityLedger();
        ActivityPlan first = ASSIGNER.assign(view, current, ledger, 100);
        syncLedger(ledger, first);

        ActivityPlan second = ASSIGNER.assign(view, current, ledger, 150);

        assertEquals(first.assignments(), second.assignments(),
                "При тех же условиях назначения не меняются: иначе задачи дёргаются туда-сюда каждый тик");
    }

    @Test
    void assign_SharpDemandRise_SwitchesActivity() {
        SettlementView view = SettlementViews.withSpirits(List.of(SettlementViews.spirit(FIRST)));
        ActivityLedger ledger = new ActivityLedger();
        ledger.assign(FIRST, ActivityId.CHOP, 100, 3.0);

        ActivityPlan plan = ASSIGNER.assign(view, demands(3, 1, 9, 1, 0.5), ledger, 150);

        assertEquals(ActivityId.MINE, plan.assignments().get(0).activity(),
                "Рост спроса добычи 3 → 9 сильнее порога 0.15: дух переключается, несмотря на окно удержания");
        assertEquals(150, plan.assignments().get(0).since(),
                "Переключение сдвигает since: иначе новое занятие унаследует чужой гистерезис");
    }

    @Test
    void assign_IneligibleSpirit_TakesNothing() {
        SettlementView view = SettlementViews.withSpirits(
                List.of(SettlementViews.spiritWithRole(FIRST, SpiritRole.DEFENDER)));

        ActivityPlan plan =
                ASSIGNER.assign(view, List.of(new ActivityDemand(ActivityId.MINE, 9)), new ActivityLedger(), 100);

        assertTrue(plan.assignments().isEmpty(),
                "Защитник не берёт добычу: иначе пост останется без охраны ради кирки");
    }

    @Test
    void assign_AllBusy_EmptyPlan() {
        SettlementView view = SettlementViews
                .withSpirits(List.of(SettlementViews.busySpirit(FIRST), SettlementViews.busySpirit(SECOND)));

        ActivityPlan plan = ASSIGNER.assign(view, demands(5, 5, 5, 5, 0.5), new ActivityLedger(), 100);

        assertTrue(plan.assignments().isEmpty(),
                "Занятые координатором духи не получают деятельность: иначе интеллект отнимет чужую цель");
    }

    @Test
    void assign_DepartedSpirit_AppearsDropped() {
        SettlementView view = SettlementViews.withSpirits(List.of(SettlementViews.spirit(SECOND)));
        ActivityLedger ledger = new ActivityLedger();
        ledger.assign(FIRST, ActivityId.CHOP, 100, 3.0);

        ActivityPlan plan = ASSIGNER.assign(view, demands(5, 4, 3, 2, 0.5), ledger, 150);

        assertEquals(List.of(FIRST), plan.dropped(),
                "Выбывший дух попадает в dropped: иначе его занятие навсегда займёт место в учёте");
        assertTrue(plan.assignments().stream().noneMatch(a -> a.spirit().equals(FIRST)),
                "Выбывший не получает назначений: иначе работа уйдёт несуществующему духу");
    }

    @Test
    void assign_NullInputs_Throw() {
        SettlementView view = SettlementViews.withSpirits(List.of(SettlementViews.spirit(FIRST)));

        assertThrows(IllegalArgumentException.class,
                () -> ASSIGNER.assign(null, demands(1, 1, 1, 1, 0.5), new ActivityLedger(), 100),
                "Распределение без обстановки не считается: зависит от нужд поселения");
        assertThrows(IllegalArgumentException.class, () -> ASSIGNER.assign(view, null, new ActivityLedger(), 100),
                "Распределение без спроса не считается: спрос даёт DemandModel");
    }
}
