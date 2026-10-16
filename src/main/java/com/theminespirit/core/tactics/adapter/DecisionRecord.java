package com.theminespirit.core.tactics.adapter;

import com.theminespirit.core.board.ConfigStamp;
import com.theminespirit.core.tactics.Decision;
import com.theminespirit.core.tactics.HoldStatus;
import java.util.List;

public record DecisionRecord(long tick, String spiritId, String spiritName, String changed, String before,
        String after, String rule, HoldStatus hold, List<ReasonFactor> factors, DecisionFacts facts,
        String configHash) {
    public static final String BY_SCORE = Decision.BY_SCORE;
    public static final String HELD = Decision.HELD;

    public DecisionRecord {
        factors = List.copyOf(factors);
        if (configHash == null || configHash.isBlank()) {
            throw new IllegalArgumentException("configHash обязателен: по нему отличают версию "
                    + "конфигурации, по которой принято решение; без неё запись несравнима со временем");
        }
    }

    public DecisionRecord(long tick, String spiritId, String spiritName, String changed, String before,
            String after, String rule, HoldStatus hold, List<ReasonFactor> factors, DecisionFacts facts) {
        this(tick, spiritId, spiritName, changed, before, after, rule, hold, factors, facts,
                ConfigStamp.current());
    }

    public boolean byScore() {
        return BY_SCORE.equals(rule) || HELD.equals(rule);
    }
}
