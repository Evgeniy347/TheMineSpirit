package com.theminespirit.core.tactics;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

class ActionTableProperties {
    @Provide
    Arbitrary<Situation> situations() {
        return Situations.any();
    }

    @Property
    void choose_AnySituationAndDecision_ActionIsDefinedAndMatchesDecision(@ForAll("situations") Situation situation,
            @ForAll TacticalOption decision) {
        ActionTable table = new ActionTable(TacticsConfig.loadDefault().rules().actions(), new RuleCounter());

        ActionChoice choice = table.choose(decision, Assessments.facts(situation));

        assertEquals(decision, choice.action().option(),
                "Для любой ситуации действие определено и согласуется с решением: FIGHT не даёт RETREAT или CONTINUE, "
                        + "иначе адаптер получит противоречие «решили драться, а действие — бежать»");
    }
}
