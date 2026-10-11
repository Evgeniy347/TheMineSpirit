package com.theminespirit.core.tactics;

final class SpecWeights {
    private static final String BODY = "\"weights\": {"
            + "\"BIAS\": {\"FIGHT\": 0, \"RETREAT\": 0.2, \"IGNORE\": 0.5},"
            + "\"WIN_MARGIN\": {\"FIGHT\": 2.0, \"RETREAT\": -1.6, \"IGNORE\": 0},"
            + "\"LOW_HP\": {\"FIGHT\": -1.5, \"RETREAT\": 2.0, \"IGNORE\": 0},"
            + "\"ENEMY_PRESSURE\": {\"FIGHT\": -0.5, \"RETREAT\": 1.2, \"IGNORE\": -0.8},"
            + "\"PROXIMITY\": {\"FIGHT\": 1.0, \"RETREAT\": 0.6, \"IGNORE\": -2.0},"
            + "\"RANGED_EXPOSURE\": {\"FIGHT\": -0.4, \"RETREAT\": 0.8, \"IGNORE\": -0.3},"
            + "\"EXPLOSIVE_NEAR\": {\"FIGHT\": -1.0, \"RETREAT\": 2.0, \"IGNORE\": -0.5},"
            + "\"ALLY_SUPPORT\": {\"FIGHT\": 0.8, \"RETREAT\": -0.5, \"IGNORE\": 0},"
            + "\"ESCAPE_ROUTE\": {\"FIGHT\": 0, \"RETREAT\": 0.6, \"IGNORE\": 0},"
            + "\"TERRAIN_RISK\": {\"FIGHT\": -0.8, \"RETREAT\": 0.4, \"IGNORE\": 0},"
            + "\"DARKNESS\": {\"FIGHT\": -0.2, \"RETREAT\": 0.3, \"IGNORE\": 0},"
            + "\"HUNGER\": {\"FIGHT\": -0.3, \"RETREAT\": 0.3, \"IGNORE\": 0},"
            + "\"PROTECT_URGENCY\": {\"FIGHT\": 1.5, \"RETREAT\": -0.6, \"IGNORE\": -1.0},"
            + "\"LEASH_PRESSURE\": {\"FIGHT\": -1.5, \"RETREAT\": 0, \"IGNORE\": 1.2},"
            + "\"HEALING_AVAILABLE\": {\"FIGHT\": 0.3, \"RETREAT\": -0.3, \"IGNORE\": 0}}";

    private SpecWeights() {
    }

    static ScoreWeights spec() {
        return parse(BODY, "[\"RETREAT\", \"FIGHT\", \"IGNORE\"]");
    }

    static ScoreWeights equalBias(String tieBreakJson) {
        StringBuilder body = new StringBuilder("\"weights\": {");
        for (Factor factor : Factor.values()) {
            String value = factor == Factor.BIAS ? "1" : "0";
            body.append(factor == Factor.BIAS ? "" : ",").append("\"").append(factor.name())
                    .append("\": {\"FIGHT\": ").append(value).append(", \"RETREAT\": ").append(value)
                    .append(", \"IGNORE\": ").append(value).append("}");
        }
        return parse(body.append("}").toString(), tieBreakJson);
    }

    static ScoreWeights withBiasZero() {
        return parse(BODY.replace("\"BIAS\": {\"FIGHT\": 0, \"RETREAT\": 0.2, \"IGNORE\": 0.5}",
                "\"BIAS\": {\"FIGHT\": 0, \"RETREAT\": 0, \"IGNORE\": 0}"), "[\"RETREAT\", \"FIGHT\", \"IGNORE\"]");
    }

    static ScoreWeights parse(String weightsBody, String tieBreakJson) {
        String json = "{\"scales\": " + Scales.json() + ", " + weightsBody + ", \"tieBreak\": " + tieBreakJson
                + ", \"stickiness\": 0.25, \"holdTicks\": 40, \"hpDropRelease\": 0.3}";
        return WeightsParser.parse(ConfigNode.root("weights.json", json));
    }
}
