package com.theminespirit.core.tactics;

final class FactorScalesParser {
    private static final String[] FIELDS = {"armorPerPoint", "armorCap", "minDps", "winMarginDivisor", "lowHpFloor",
            "proximityRange", "rangedRange", "rangedFull", "explosiveRange", "allyFull", "routesFull",
            "lavaRange", "dropSafe", "dropRange", "nightFloor", "hungerThreshold", "healFull", "leashMargin",
            "speedRatioCap"};

    private FactorScalesParser() {
    }

    static FactorScales parse(ConfigNode node) {
        node.fields(FIELDS);
        ArmorModel armor = node.build("armor",
                () -> new ArmorModel(node.get("armorPerPoint").number(), node.get("armorCap").number()));
        return node.build(() -> new FactorScales(armor,
                node.get("minDps").number(),
                node.get("winMarginDivisor").number(),
                node.get("lowHpFloor").number(),
                node.get("proximityRange").number(),
                node.get("rangedRange").number(),
                node.get("rangedFull").number(),
                node.get("explosiveRange").number(),
                node.get("allyFull").number(),
                node.get("routesFull").number(),
                node.get("lavaRange").number(),
                node.get("dropSafe").number(),
                node.get("dropRange").number(),
                node.get("nightFloor").number(),
                node.get("hungerThreshold").number(),
                node.get("healFull").number(),
                node.get("leashMargin").number(),
                node.get("speedRatioCap").number()));
    }
}
