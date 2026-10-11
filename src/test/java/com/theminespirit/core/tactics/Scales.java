package com.theminespirit.core.tactics;

final class Scales {
    private static final String JSON = "{\"armorPerPoint\": 0.04, \"armorCap\": 0.8, \"minDps\": 0.1,"
            + " \"winMarginDivisor\": 3, \"lowHpFloor\": 0.6, \"proximityRange\": 12,"
            + " \"rangedRange\": 24, \"rangedFull\": 3, \"explosiveRange\": 7, \"allyFull\": 3, \"routesFull\": 3, \"lavaRange\": 6,"
            + " \"dropSafe\": 3, \"dropRange\": 9, \"nightFloor\": 0.4, \"hungerThreshold\": 0.5, \"healFull\": 3,"
            + " \"leashMargin\": 2, \"speedRatioCap\": 10}";
    private static final FactorScales DEFAULTS = FactorScalesParser.parse(ConfigNode.root("weights.json", JSON));

    private Scales() {
    }

    static FactorScales defaults() {
        return DEFAULTS;
    }

    static String json() {
        return JSON;
    }
}
