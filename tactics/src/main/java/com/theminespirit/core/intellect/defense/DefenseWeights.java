package com.theminespirit.core.intellect.defense;

import com.theminespirit.core.inventory.JsonResource;
import com.theminespirit.core.reload.TableSlot;
import com.theminespirit.core.tactics.ConfigNode;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Supplier;

public record DefenseWeights(
        double squadRadius,
        double localRadius,
        double baseCapacity,
        double healthFloor,
        double healthShare,
        double armorFull,
        double armorFloor,
        double weaponFull,
        double weaponFloor,
        double foodFull,
        double foodPenalty,
        double strengthBonus,
        int maxHelpersPerThreat,
        double woundedFraction,
        double allWoundedShare,
        double healPotionMinHealth,
        Map<TargetClass, Double> threatWeight) {
    public static final String RESOURCE = "data/theminespirit/tactics/defense.json";
    private static final TableSlot<DefenseWeights> SOURCE = new TableSlot<>(DefenseWeights::readFromClasspath);

    public DefenseWeights {
        for (double value : new double[] {
            squadRadius,
            localRadius,
            baseCapacity,
            healthFloor,
            healthShare,
            armorFull,
            armorFloor,
            weaponFull,
            weaponFloor,
            foodFull,
            foodPenalty,
            strengthBonus,
            woundedFraction,
            allWoundedShare,
            healPotionMinHealth
        }) {
            if (!Double.isFinite(value) || value < 0) {
                throw new IllegalArgumentException(
                        "Вес обороны " + value + ": он неотрицателен и конечен, иначе решение команды не посчитать");
            }
        }
        if (squadRadius <= 0 || localRadius <= 0 || baseCapacity <= 0 || armorFull <= 0 || weaponFull <= 0) {
            throw new IllegalArgumentException("Опорные веса обороны: радиус отряда " + squadRadius + ", радиус боя "
                    + localRadius + ", ёмкость " + baseCapacity + ", броня " + armorFull + ", оружие " + weaponFull
                    + " — все положительны");
        }
        if (foodFull > 1 || woundedFraction >= 1 || allWoundedShare > 1 || healPotionMinHealth > 1) {
            throw new IllegalArgumentException("Доли обороны вне [0, 1]: сытость " + foodFull + ", ранение "
                    + woundedFraction + ", доля отряда " + allWoundedShare + ", порог лечилки " + healPotionMinHealth);
        }
        if (maxHelpersPerThreat <= 0) {
            throw new IllegalArgumentException("maxHelpersPerThreat = " + maxHelpersPerThreat
                    + ": предел помощников положителен, иначе помощь не приходит");
        }
        if (threatWeight == null || threatWeight.size() != TargetClass.values().length) {
            throw new IllegalArgumentException("threatWeight обязан задать вес каждому классу цели: иначе новый "
                    + "класс молча получит чужую силу");
        }
        threatWeight = Map.copyOf(threatWeight);
    }

    public static DefenseWeights loadDefault() {
        return SOURCE.current();
    }

    public static void useSource(Supplier<DefenseWeights> supplier) {
        SOURCE.use(supplier);
    }

    public static DefenseWeights readFromClasspath() {
        return fromJson(JsonResource.readClasspath(RESOURCE));
    }

    public static DefenseWeights fromJson(String json) {
        return parse(ConfigNode.root("defense.json", json));
    }

    static DefenseWeights parse(ConfigNode root) {
        root.fields(new String[] {
            "squadRadius",
            "localRadius",
            "baseCapacity",
            "healthFloor",
            "healthShare",
            "armorFull",
            "armorFloor",
            "weaponFull",
            "weaponFloor",
            "foodFull",
            "foodPenalty",
            "strengthBonus",
            "maxHelpersPerThreat",
            "woundedFraction",
            "allWoundedShare",
            "healPotionMinHealth",
            "threatWeight"
        });
        return root.build(() -> new DefenseWeights(
                root.get("squadRadius").number(0.5, 64),
                root.get("localRadius").number(1, 256),
                root.get("baseCapacity").number(0.1, 10),
                root.get("healthFloor").number(0, 1),
                root.get("healthShare").number(0, 1),
                root.get("armorFull").number(1, DefenseMember.MAX_ARMOR),
                root.get("armorFloor").number(0, 1),
                root.get("weaponFull").number(0.1, 100),
                root.get("weaponFloor").number(0, 1),
                root.get("foodFull").number(0, 1),
                root.get("foodPenalty").number(0, 1),
                root.get("strengthBonus").number(0, 2),
                root.get("maxHelpersPerThreat").integer(1, 64),
                root.get("woundedFraction").number(0, 1),
                root.get("allWoundedShare").number(0, 1),
                root.get("healPotionMinHealth").number(0, 1),
                threatWeight(root.get("threatWeight"))));
    }

    private static Map<TargetClass, Double> threatWeight(ConfigNode node) {
        Map<TargetClass, Double> weights = new EnumMap<>(TargetClass.class);
        for (Map.Entry<String, ConfigNode> entry : node.entries().entrySet()) {
            TargetClass cls = node.enumValueOf(TargetClass.class, entry.getKey(), "класс цели");
            if (weights.containsKey(cls)) {
                throw entry.getValue()
                        .fail(
                                "класс '" + cls + "' описан дважды",
                                "вес класса цели задаётся один раз, иначе выбор веса случаен");
            }
            weights.put(cls, entry.getValue().number(0, 100));
        }
        return weights;
    }
}
