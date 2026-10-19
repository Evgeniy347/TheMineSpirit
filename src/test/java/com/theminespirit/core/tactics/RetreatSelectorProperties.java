package com.theminespirit.core.tactics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

class RetreatSelectorProperties {
    private static final TacticsConfig CONFIG = TacticsConfig.loadDefault();

    @Provide
    Arbitrary<Situation> situations() {
        return Situations.any();
    }

    @Property
    void choice_AnySituation_ChosenMethodSatisfiesItsOwnRequirements(@ForAll("situations") Situation situation) {
        FactMap facts = Assessments.facts(situation);
        RetreatOptions options = new RetreatSelector(CONFIG.rules().retreat(), new RuleCounter()).evaluate(facts);

        RetreatChoice choice = options.choice();

        choice.rule().ifPresent(id -> {
            RetreatEntry entry = CONFIG.rules().retreat().entries().stream()
                    .filter(candidate -> candidate.id().equals(id))
                    .findFirst()
                    .get();
            assertTrue(
                    entry.requires().matches(facts),
                    "Выбранный способ " + choice.method() + " (" + id + ") обязан удовлетворять собственным requires: "
                            + "иначе дух отходит туда, куда по условиям отходить нельзя");
        });
        assertEquals(
                options.routes() == 0,
                choice.method() == RetreatMethod.NONE,
                "NONE выбирается тогда и только тогда, когда ни один способ не подошёл");
    }
}
