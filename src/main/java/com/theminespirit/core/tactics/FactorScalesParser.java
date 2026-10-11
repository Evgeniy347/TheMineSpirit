package com.theminespirit.core.tactics;

final class FactorScalesParser {
    private static final String[] FIELDS = {"armorPerPoint", "armorCap", "minDps", "winMarginDivisor", "lowHpFloor",
            "pressureHorizon", "proximityRange", "rangedRange", "explosiveRange", "allyFull", "routesFull",
            "lavaRange", "dropSafe", "dropRange", "nightFloor", "hungerThreshold", "healFull", "leashMargin",
            "speedRatioCap"};

    private FactorScalesParser() {
    }

    static FactorScales parse(ConfigNode node) {
        node.fields(FIELDS);
        return new FactorScales(
                new ArmorModel(node.get("armorPerPoint").number(0, 1), node.get("armorCap").number(0, 0.95)),
                node.get("minDps").number(0.001, 100),
                node.get("winMarginDivisor").number(0.1, 100),
                node.get("lowHpFloor").number(0.01, 1),
                node.get("pressureHorizon").number(0.1, 1000),
                node.get("proximityRange").number(0.1, 1000),
                node.get("rangedRange").number(0.1, 1000),
                node.get("explosiveRange").number(0.1, 1000),
                node.get("allyFull").number(1, 64),
                node.get("routesFull").number(1, 10),
                node.get("lavaRange").number(0.1, 100),
                node.get("dropSafe").number(0, 100),
                node.get("dropRange").number(0.1, 400),
                node.get("nightFloor").number(0, 1),
                node.get("hungerThreshold").number(0.01, 1),
                node.get("healFull").number(1, 64),
                node.get("leashMargin").number(0.1, 1000),
                node.get("speedRatioCap").number(1, 1000));
    }
}
