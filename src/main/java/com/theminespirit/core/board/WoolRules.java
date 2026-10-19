package com.theminespirit.core.board;

import com.theminespirit.core.reload.DataTables;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Шерсть и кровати: числа и гейты по снимку запасов. Порог отары, радиус и суточный
 * лимит диких овец, порог и запас нитей, цена ножниц и кровати — данные таблицы board
 * ({@code wool}); здесь только значения по умолчанию и проверки их осмысленности.
 * Овца загона не цель забоя: это правило живёт в {@link BreedingRules} и CullPolicy.
 */
public final class WoolRules {
    public static final Set<String> WOOL_KEYS = Set.of(
            "min_pen_sheep", "wild_sheep_radius", "wild_sheep_per_day", "string_threshold",
            "string_reserve", "shears_iron", "bed_wool", "bed_planks", "shear_radius");

    public static final int DEFAULT_MIN_PEN_SHEEP = 4;
    public static final int DEFAULT_WILD_SHEEP_RADIUS = 48;
    public static final int DEFAULT_WILD_SHEEP_PER_DAY = 4;
    public static final int DEFAULT_STRING_THRESHOLD = 8;
    public static final int DEFAULT_STRING_RESERVE = 4;
    public static final int DEFAULT_SHEARS_IRON = 2;
    public static final int DEFAULT_BED_WOOL = 3;
    public static final int DEFAULT_BED_PLANKS = 3;
    public static final int DEFAULT_SHEAR_RADIUS = 8;

    public static final int STRINGS_PER_WOOL = 4;
    public static final String SHEARS = "shears";
    public static final String IRON_INGOT = "iron_ingot";
    public static final String STRING = "string";
    public static final String CRAFTED_SHEARS = "minecraft:shears";
    public static final String CRAFTED_WOOL = "minecraft:white_wool";
    public static final String CRAFTED_BED = "minecraft:white_bed";
    public static final String PLANK_SUFFIX = "_planks";

    public static final List<String> COLORS = List.of(
            "white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
            "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black");

    private WoolRules() {
    }

    public static int minPenSheep() {
        return woolValue("min_pen_sheep", DEFAULT_MIN_PEN_SHEEP);
    }

    public static int wildSheepRadius() {
        return woolValue("wild_sheep_radius", DEFAULT_WILD_SHEEP_RADIUS);
    }

    public static int wildSheepPerDay() {
        return woolValue("wild_sheep_per_day", DEFAULT_WILD_SHEEP_PER_DAY);
    }

    public static int stringThreshold() {
        return woolValue("string_threshold", DEFAULT_STRING_THRESHOLD);
    }

    public static int stringReserve() {
        return woolValue("string_reserve", DEFAULT_STRING_RESERVE);
    }

    public static int shearsIron() {
        return woolValue("shears_iron", DEFAULT_SHEARS_IRON);
    }

    public static int bedWool() {
        return woolValue("bed_wool", DEFAULT_BED_WOOL);
    }

    public static int bedPlanks() {
        return woolValue("bed_planks", DEFAULT_BED_PLANKS);
    }

    public static int shearRadius() {
        return woolValue("shear_radius", DEFAULT_SHEAR_RADIUS);
    }

    public static boolean penReady(int sheepInPen) {
        if (sheepInPen < 0) {
            throw new IllegalArgumentException("Овец в загоне " + sheepInPen + ": поголовье не бывает "
                    + "отрицательным, отрицательное число прятало бы пустой загон");
        }
        return sheepInPen >= minPenSheep();
    }

    public static boolean shearsCraftable(int iron, boolean hasShears) {
        if (iron < 0) {
            throw new IllegalArgumentException("Железа " + iron + ": запас не бывает отрицательным");
        }
        return !hasShears && iron >= shearsIron();
    }

    /** Сколько шерсти выйдет из нитей, не трогая запас: партия — 4 нити за 1 шерсть. */
    public static int woolFromStrings(int strings) {
        if (strings < 0) {
            throw new IllegalArgumentException("Нитей " + strings + ": запас не бывает отрицательным");
        }
        if (strings < stringThreshold()) {
            return 0;
        }
        int batches = (strings - stringReserve()) / STRINGS_PER_WOOL;
        return Math.max(0, batches);
    }

    public static int bedsNeeded(int beds, int spirits) {
        if (beds < 0 || spirits < 0) {
            throw new IllegalArgumentException("Кроватей " + beds + " и духов " + spirits
                    + ": количество не бывает отрицательным");
        }
        return Math.max(0, spirits - beds);
    }

    public static boolean wildTarget(boolean inHomePen, long distanceSquared, int radius) {
        if (distanceSquared < 0) {
            throw new IllegalArgumentException("Квадрат расстояния " + distanceSquared
                    + " не бывает отрицательным");
        }
        if (radius < 0) {
            throw new IllegalArgumentException("Радиус охоты " + radius + " не бывает отрицательным");
        }
        return !inHomePen && distanceSquared <= (long) radius * radius;
    }

    public static String woolId(String color) {
        return requireColor(color) + "_wool";
    }

    public static String bedId(String color) {
        return requireColor(color) + "_bed";
    }

    /** Цвет кровати: сначала белая, потом первый цвет, которого набралось на кровать. */
    public static Optional<String> bedColor(StockView stock) {
        Objects.requireNonNull(stock, "Склад не может быть null: цвет кровати считают по запасам");
        if (wool(stock, "white") >= bedWool()) {
            return Optional.of("white");
        }
        for (String color : COLORS) {
            if (wool(stock, color) >= bedWool()) {
                return Optional.of(color);
            }
        }
        return Optional.empty();
    }

    public static Optional<String> planksType(StockView stock) {
        Objects.requireNonNull(stock, "Склад не может быть null: доски для кровати считают по запасам");
        List<String> types = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : stock.available().entrySet()) {
            if (entry.getKey().endsWith(PLANK_SUFFIX) && entry.getValue() >= bedPlanks()) {
                types.add(entry.getKey());
            }
        }
        types.sort(String::compareTo);
        return types.stream().findFirst();
    }

    public static int bedCount(StockView stock) {
        Objects.requireNonNull(stock, "Склад не может быть null: число кроватей считают по запасам");
        int total = 0;
        for (String color : COLORS) {
            total += stock.available().getOrDefault(bedId(color), 0);
        }
        return total;
    }

    public static int wool(StockView stock, String color) {
        Objects.requireNonNull(stock, "Склад не может быть null: шерсть считают по запасам");
        return stock.available().getOrDefault(woolId(color), 0);
    }

    static String woolKey(String key) {
        if (!WOOL_KEYS.contains(key)) {
            throw new IllegalArgumentException("Неизвестный ключ шерсти '" + key + "': допустимы "
                    + WOOL_KEYS + ", сверьтесь с секцией wool в board.json");
        }
        return key;
    }

    private static String requireColor(String color) {
        if (color == null || !COLORS.contains(color)) {
            throw new IllegalArgumentException("Неизвестный цвет шерсти '" + color + "': допустимы "
                    + COLORS);
        }
        return color;
    }

    private static int woolValue(String key, int fallback) {
        Integer value = DataTables.INSTANCE.board().current().wool().get(key);
        return value == null || value < 1 ? fallback : value;
    }
}
