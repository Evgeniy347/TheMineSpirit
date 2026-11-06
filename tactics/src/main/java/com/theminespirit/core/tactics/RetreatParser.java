package com.theminespirit.core.tactics;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

final class RetreatParser {
    private static final String WHEN = "when";
    private static final String FACT = "fact";
    private static final String PER_UNIT = "perUnit";

    private RetreatParser() {}

    static RetreatTable parse(ConfigNode node, Function<ConfigNode, String> claimId) {
        node.fields(new String[] {"methods", "callHelp", "rally"});
        List<RetreatEntry> entries = new ArrayList<>();
        for (ConfigNode item : node.get("methods").nonEmptyItems()) {
            entries.add(entry(item, claimId));
        }
        ConfigNode help = node.get("callHelp").fields(new String[] {WHEN});
        ConfigNode rally = node.get("rally")
                .fields(new String[] {"rallyRadius", "callRadius", "callMinEnemies", "callIntervalTicks", "helpRadius"
                });
        RallyTuning tuning = rally.build(() -> new RallyTuning(
                rally.get("rallyRadius").integer(),
                rally.get("callRadius").integer(),
                rally.get("callMinEnemies").integer(),
                rally.get("callIntervalTicks").integer(),
                rally.get("helpRadius").integer()));
        return new RetreatTable(entries, early(help.get(WHEN)), tuning);
    }

    private static RetreatEntry entry(ConfigNode item, Function<ConfigNode, String> claimId) {
        item.fields(new String[] {"id", "method", "requires", "utility", "bonus"});
        RetreatMethod method = item.get("method").enumValue(RetreatMethod.class);
        if (method == RetreatMethod.NONE) {
            throw item.get("method")
                    .fail("способ NONE нельзя описывать", "NONE означает: ничего не подошло, и выбирается сам");
        }
        ConfigNode utility = item.get("utility").fields(new String[] {"base"}, FACT, PER_UNIT);
        if (utility.has(FACT) != utility.has(PER_UNIT)) {
            throw utility.fail("поля fact и perUnit задаются вместе", "полезность = base + perUnit × значение факта");
        }
        List<RetreatEntry.Bonus> bonuses = new ArrayList<>();
        for (ConfigNode bonus : item.get("bonus").items()) {
            bonus.fields(new String[] {WHEN, "add"});
            bonuses.add(new RetreatEntry.Bonus(
                    early(bonus.get(WHEN)), bonus.get("add").number(-1, 1)));
        }
        return new RetreatEntry(
                claimId.apply(item.get("id")),
                method,
                early(item.get("requires")),
                utility.get("base").number(0, 1),
                utilityFact(utility),
                utility.has(PER_UNIT) ? utility.get(PER_UNIT).number(-1, 1) : 0,
                bonuses);
    }

    private static Optional<FactName> utilityFact(ConfigNode utility) {
        if (!utility.has(FACT)) {
            return Optional.empty();
        }
        ConfigNode node = utility.get(FACT);
        FactName fact = FactName.byKey(node.text())
                .orElseThrow(
                        () -> node.fail("неизвестный факт '" + node.text() + "'", "имя берётся из каталога фактов"));
        if (fact.type() != FactType.NUM || fact.computedAfterRetreatChoice()) {
            throw node.fail(
                    "факт " + fact.key() + " не годится для полезности",
                    "нужен числовой факт, известный до выбора отхода");
        }
        return Optional.of(fact);
    }

    private static Condition early(ConfigNode array) {
        Condition condition = ConditionParser.parse(array);
        for (FactName fact : condition.facts()) {
            if (fact.computedAfterRetreatChoice()) {
                throw array.fail(
                        "условие отхода использует " + fact.key() + ", который считается после выбора отхода",
                        "доступность отхода сама зависит от этих условий: используйте факты о себе, врагах и среде");
            }
        }
        return condition;
    }
}
