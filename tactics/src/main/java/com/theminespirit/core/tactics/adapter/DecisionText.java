package com.theminespirit.core.tactics.adapter;

import com.theminespirit.core.tactics.HoldStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.StringJoiner;

public final class DecisionText {
    public static final String LOG_PREFIX = "[tactics]";

    private DecisionText() {}

    public static String format(DecisionRecord record) {
        StringJoiner text = new StringJoiner("; ");
        text.add(LOG_PREFIX + " тик " + record.tick());
        text.add("дух " + record.spiritName() + " (" + record.spiritId() + ")");
        text.add(fieldName(record.changed()) + " " + record.before() + " → " + record.after());
        text.add(reason(record));
        text.add(facts(record.facts()));
        return text.toString();
    }

    public static String fieldName(String field) {
        return switch (field) {
            case DecisionSnapshot.DECISION -> "решение";
            case DecisionSnapshot.MODE -> "режим";
            case DecisionSnapshot.TARGET -> "цель";
            case DecisionSnapshot.ORDER -> "приказ";
            default ->
                throw new IllegalArgumentException("Поле записи '" + field + "' неизвестно: подписи есть у "
                        + DecisionSnapshot.DECISION + ", " + DecisionSnapshot.MODE + ", " + DecisionSnapshot.TARGET
                        + " и " + DecisionSnapshot.ORDER);
        };
    }

    private static String reason(DecisionRecord record) {
        if (record.byScore()) {
            return "по оценке" + (record.hold() == HoldStatus.HELD ? " (удержание)" : "") + factors(record.factors());
        }
        return "по правилу " + record.rule() + factors(record.factors());
    }

    private static String factors(List<ReasonFactor> factors) {
        if (factors.isEmpty()) {
            return "";
        }
        List<String> weights = new ArrayList<>();
        for (ReasonFactor factor : factors) {
            weights.add(factor.factor() + " " + weight(factor.weight()));
        }
        return ", факторы " + String.join(", ", weights);
    }

    private static String weight(double value) {
        return String.format(Locale.ROOT, "%+.2f", value);
    }

    private static String facts(DecisionFacts facts) {
        return "врагов рядом " + facts.enemies() + " (ближайший " + facts.nearest() + "), здоровье " + health(facts)
                + ", броня " + armor(facts.armor()) + ", бьёт: " + facts.weapon();
    }

    private static String health(DecisionFacts facts) {
        return String.format(Locale.ROOT, "%.0f/%.0f", facts.hp(), facts.maxHp());
    }

    private static String armor(double armor) {
        return String.format(Locale.ROOT, "%.0f", armor);
    }
}
