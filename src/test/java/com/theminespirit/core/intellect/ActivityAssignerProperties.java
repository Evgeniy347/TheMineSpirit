package com.theminespirit.core.intellect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.LongRange;

class ActivityAssignerProperties {
    private static final ActivityAssigner ASSIGNER = new ActivityAssigner();

    @Provide
    Arbitrary<SettlementView> views() {
        return SettlementViews.views();
    }

    @Provide
    Arbitrary<List<ActivityDemand>> demandLists() {
        return Arbitraries.subsetOf(Set.of(ActivityId.values()))
                .flatMap(ids -> Arbitraries.doubles().between(0, 10).list().ofSize(ids.size())
                        .map(values -> {
                            List<ActivityDemand> demands = new ArrayList<>();
                            int index = 0;
                            for (ActivityId id : ids) {
                                demands.add(new ActivityDemand(id, values.get(index)));
                                index++;
                            }
                            return demands;
                        }));
    }

    @Provide
    Arbitrary<ActivityLedger> ledgers() {
        return Arbitraries.create(UUID::randomUUID).list().ofMaxSize(4)
                .flatMap(ids -> Arbitraries.of(ActivityId.values()).list().ofSize(ids.size())
                        .flatMap(activities -> Arbitraries.longs().between(0, 50000).map(now -> {
                            ActivityLedger ledger = new ActivityLedger();
                            for (int index = 0; index < ids.size(); index++) {
                                UUID spirit = ids.get(index);
                                if (ledger.of(spirit).isEmpty()) {
                                    ledger.assign(spirit, activities.get(index), 0, 1.0);
                                }
                            }
                            return ledger;
                        })));
    }

    @Property
    void assign_AnyInput_OneActivityPerSpirit(@ForAll("views") SettlementView view,
            @ForAll("demandLists") List<ActivityDemand> demands, @ForAll("ledgers") ActivityLedger ledger,
            @ForAll @LongRange(min = 0, max = 100000) long now) {
        ActivityPlan plan = ASSIGNER.assign(view, demands, ledger, now);

        List<UUID> spirits = plan.assignments().stream().map(ActivityAssignment::spirit).toList();
        assertEquals(new HashSet<>(spirits).size(), spirits.size(),
                "На духа не больше одного занятия: иначе дух получит две работы сразу");
    }

    @Property
    void assign_AnyDemands_AssignedIdsFromDemands(@ForAll("views") SettlementView view,
            @ForAll("demandLists") List<ActivityDemand> demands, @ForAll("ledgers") ActivityLedger ledger,
            @ForAll @LongRange(min = 0, max = 100000) long now) {
        ActivityPlan plan = ASSIGNER.assign(view, demands, ledger, now);
        Set<ActivityId> offered =
                new HashSet<>(demands.stream().map(ActivityDemand::id).toList());

        for (ActivityAssignment assignment : plan.assignments()) {
            assertTrue(offered.contains(assignment.activity()),
                    "Назначена только деятельность из спроса: иначе взрослому достанется детская работа и наоборот");
        }
    }

    @Property
    void assign_SameInput_Deterministic(@ForAll("views") SettlementView view,
            @ForAll("demandLists") List<ActivityDemand> demands, @ForAll @LongRange(min = 0, max = 100000) long now) {
        ActivityPlan first = ASSIGNER.assign(view, demands, new ActivityLedger(), now);
        ActivityPlan second = ASSIGNER.assign(view, demands, new ActivityLedger(), now);

        assertEquals(first, second, "Тот же вход даёт тот же план: иначе назначения мигают между пересборками");
    }

    @Property
    void assign_SyncedLedgerNextTick_Unchanged(@ForAll("views") SettlementView view,
            @ForAll("demandLists") List<ActivityDemand> demands,
            @ForAll @LongRange(min = 0, max = 100000) long now) {
        ActivityLedger ledger = new ActivityLedger();
        ActivityPlan first = ASSIGNER.assign(view, demands, ledger, now);
        for (ActivityAssignment assignment : first.assignments()) {
            ledger.assign(assignment.spirit(), assignment.activity(), assignment.since(), assignment.demand());
        }

        ActivityPlan second = ASSIGNER.assign(view, demands, ledger, now + 1);

        assertEquals(first.assignments(), second.assignments(),
                "При неизменном входе назначения не меняются между тиками: иначе задачи дёргаются");
    }

    @Property
    void assign_SpiritLeaves_LedgerFreesWork(@ForAll("demandLists") List<ActivityDemand> demands) {
        UUID departed = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID staying = UUID.fromString("22222222-2222-2222-2222-222222222222");
        ActivityLedger ledger = new ActivityLedger();
        ledger.assign(departed, ActivityId.CHOP, 0, 3.0);
        SettlementView view = SettlementViews.withSpirits(List.of(SettlementViews.spirit(staying)));

        ActivityPlan plan = ASSIGNER.assign(view, demands, ledger, 300);

        assertTrue(plan.dropped().contains(departed),
                "Выбывший дух попадает в dropped: иначе его занятие навсегда займёт место в учёте");
        assertTrue(plan.assignments().stream().noneMatch(a -> a.spirit().equals(departed)),
                "Выбывший не получает назначений: перераспределение отдаёт работу оставшимся");
    }
}
