package com.theminespirit.core.rest;

import com.theminespirit.core.tactics.HoldStatus;
import com.theminespirit.core.tactics.adapter.DecisionFacts;
import com.theminespirit.core.tactics.adapter.DecisionRecord;
import com.theminespirit.core.tactics.adapter.ReasonFactor;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class DecisionJson {
    private DecisionJson() {}

    public static String toJson(String bot, DecisionsQuery query, List<DecisionRecord> records) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("bot", bot);
        root.put("limit", query.limit());
        List<Map<String, Object>> entries = new ArrayList<>(records.size());
        for (DecisionRecord record : records) {
            entries.add(toMap(record));
        }
        root.put("decisions", entries);
        return RestJson.write(root);
    }

    private static Map<String, Object> toMap(DecisionRecord record) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("tick", record.tick());
        map.put("spirit", record.spiritId());
        map.put("changed", record.changed());
        map.put("from", record.before());
        map.put("to", record.after());
        map.put("rule", record.rule());
        map.put("hold", hold(record.hold()));
        map.put("factors", factors(record.factors()));
        map.put("facts", facts(record.facts()));
        return map;
    }

    private static String hold(HoldStatus status) {
        return status.name().toLowerCase(Locale.ROOT);
    }

    private static List<Map<String, Object>> factors(List<ReasonFactor> factors) {
        List<Map<String, Object>> entries = new ArrayList<>(factors.size());
        for (ReasonFactor factor : factors) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("factor", factor.factor());
            entry.put("value", number(factor.value()));
            entry.put("weight", number(factor.weight()));
            entries.add(entry);
        }
        return entries;
    }

    private static Map<String, Object> facts(DecisionFacts facts) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("enemies", facts.enemies());
        map.put("nearest", facts.nearest());
        map.put("hp", number(facts.hp()));
        map.put("max_hp", number(facts.maxHp()));
        map.put("armor", number(facts.armor()));
        map.put("weapon", facts.weapon());
        return map;
    }

    private static double number(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
