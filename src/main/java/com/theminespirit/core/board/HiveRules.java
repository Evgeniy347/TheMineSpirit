package com.theminespirit.core.board;

import com.theminespirit.core.reload.DataTables;
import java.util.Set;

/**
 * Пчёлы: числа и гейты по снимку запасов и миру. Радиус поиска ульев, глубина дыма,
 * радиус цветов и их минимум, цена улья — данные таблицы board ({@code hive}).
 * Пчёл не убивают: скрипт берёт соты или мёд и не трогает обитателей улья.
 */
public final class HiveRules {
    public static final Set<String> HIVE_KEYS = Set.of(
            "hive_radius",
            "smoke_depth",
            "flower_radius",
            "flowers_min",
            "hive_planks",
            "hive_honeycomb",
            "site_radius");

    public static final int DEFAULT_HIVE_RADIUS = 16;
    public static final int DEFAULT_SMOKE_DEPTH = 5;
    public static final int DEFAULT_FLOWER_RADIUS = 4;
    public static final int DEFAULT_FLOWERS_MIN = 3;
    public static final int DEFAULT_HIVE_PLANKS = 6;
    public static final int DEFAULT_HIVE_HONEYCOMB = 3;
    public static final int DEFAULT_SITE_RADIUS = 8;

    public static final int FULL_HONEY_LEVEL = 5;
    public static final int MAX_SMOKE_DEPTH = 5;

    public static final String SHEARS = "shears";
    public static final String GLASS_BOTTLE = "glass_bottle";
    public static final String HONEYCOMB = "honeycomb";
    public static final String CRAFTED_BEEHIVE = "minecraft:beehive";

    public enum Tool {
        SHEARS,
        BOTTLE,
        NONE
    }

    private HiveRules() {}

    public static int hiveRadius() {
        return hiveValue("hive_radius", DEFAULT_HIVE_RADIUS);
    }

    public static int smokeDepth() {
        return Math.min(MAX_SMOKE_DEPTH, hiveValue("smoke_depth", DEFAULT_SMOKE_DEPTH));
    }

    public static int flowerRadius() {
        return hiveValue("flower_radius", DEFAULT_FLOWER_RADIUS);
    }

    public static int flowersMin() {
        return hiveValue("flowers_min", DEFAULT_FLOWERS_MIN);
    }

    public static int hivePlanks() {
        return hiveValue("hive_planks", DEFAULT_HIVE_PLANKS);
    }

    public static int hiveHoneycomb() {
        return hiveValue("hive_honeycomb", DEFAULT_HIVE_HONEYCOMB);
    }

    public static int siteRadius() {
        return hiveValue("site_radius", DEFAULT_SITE_RADIUS);
    }

    public static boolean fullHive(int honeyLevel) {
        if (honeyLevel < 0 || honeyLevel > FULL_HONEY_LEVEL) {
            throw new IllegalArgumentException("Уровень мёда " + honeyLevel + " вне [0, " + FULL_HONEY_LEVEL
                    + "]: такого состояния улья не бывает");
        }
        return honeyLevel >= FULL_HONEY_LEVEL;
    }

    public static Tool chooseTool(boolean hasShears, int glassBottles) {
        if (glassBottles < 0) {
            throw new IllegalArgumentException(
                    "Стеклянных бутылок " + glassBottles + ": запас не бывает отрицательным");
        }
        if (hasShears) {
            return Tool.SHEARS;
        }
        return glassBottles > 0 ? Tool.BOTTLE : Tool.NONE;
    }

    public static boolean campfireNeeded(boolean smoked) {
        return !smoked;
    }

    public static boolean smokeOffsetWithinDepth(int offset) {
        if (offset < 0) {
            throw new IllegalArgumentException(
                    "Смещение под ульем " + offset + " не бывает отрицательным: отсчёт идёт от улья вниз");
        }
        return offset >= 1 && offset <= smokeDepth();
    }

    public static boolean flowersEnough(int flowers) {
        if (flowers < 0) {
            throw new IllegalArgumentException("Цветов " + flowers + ": счёт не бывает отрицательным");
        }
        return flowers >= flowersMin();
    }

    public static boolean planksEnough(int planks) {
        if (planks < 0) {
            throw new IllegalArgumentException("Досок " + planks + ": запас не бывает отрицательным");
        }
        return planks >= hivePlanks();
    }

    public static boolean honeycombEnough(int honeycomb) {
        if (honeycomb < 0) {
            throw new IllegalArgumentException("Сотов " + honeycomb + ": запас не бывает отрицательным");
        }
        return honeycomb >= hiveHoneycomb();
    }

    public static boolean placementReady(int honeycomb, int planks, int flowers) {
        return honeycombEnough(honeycomb) && planksEnough(planks) && flowersEnough(flowers);
    }

    static String hiveKey(String key) {
        if (!HIVE_KEYS.contains(key)) {
            throw new IllegalArgumentException("Неизвестный ключ пчёл '" + key + "': допустимы " + HIVE_KEYS
                    + ", сверьтесь с секцией hive в board.json");
        }
        return key;
    }

    private static int hiveValue(String key, int fallback) {
        Integer value = DataTables.INSTANCE.board().current().hive().get(key);
        return value == null || value < 1 ? fallback : value;
    }
}
