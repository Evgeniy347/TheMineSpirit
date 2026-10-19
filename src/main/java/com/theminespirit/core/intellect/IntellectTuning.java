package com.theminespirit.core.intellect;

import com.fasterxml.jackson.databind.JsonNode;
import com.theminespirit.core.inventory.JsonResource;
import com.theminespirit.core.reload.DataTables;
import java.util.Set;

/**
 * Коэффициенты тактического интеллекта поселения. Числа — данные, а не константы кода: они лежат в
 * {@code data/theminespirit/tactics/intellect.json} и перечитываются по {@code /reload} из датапака мира.
 * Потребители берут текущую версию через {@link #loadDefault()} и не кэшируют её между операциями.
 */
public record IntellectTuning(
        long demandIntervalTicks,
        long minSwitchTicks,
        long failBackoffTicks,
        double hysteresisMargin,
        double crowdPenalty,
        double workCrowdPenalty,
        double maxDemand,
        double fleeMargin,
        int maxFighters,
        double foodDays,
        double foodRation,
        double foodChildRation,
        double foodDemandMin,
        double foodDemandMax,
        int forageWithdrawCount,
        int forageWithdrawRadius,
        int forageHuntRadius,
        int forageHuntCount,
        int forageFishRadius,
        int forageFishCount,
        int forageFishDuration,
        int oreTarget,
        int ironTarget,
        int weaponTarget,
        int teamMin,
        int teamMax,
        long inviteTtlTicks,
        long inviteRepeatTicks,
        long inviteExtendTicks,
        int inviteMaxExtends,
        long strategicTtlTicks,
        double healFraction,
        double armedDps) {

    public static final String RESOURCE = "data/theminespirit/tactics/intellect.json";

    private static final Set<String> FIELDS = Set.of(
            "demandIntervalTicks",
            "minSwitchTicks",
            "failBackoffTicks",
            "hysteresisMargin",
            "crowdPenalty",
            "workCrowdPenalty",
            "maxDemand",
            "fleeMargin",
            "maxFighters",
            "foodDays",
            "foodRation",
            "foodChildRation",
            "foodDemandMin",
            "foodDemandMax",
            "forageWithdrawCount",
            "forageWithdrawRadius",
            "forageHuntRadius",
            "forageHuntCount",
            "forageFishRadius",
            "forageFishCount",
            "forageFishDuration",
            "oreTarget",
            "ironTarget",
            "weaponTarget",
            "teamMin",
            "teamMax",
            "inviteTtlTicks",
            "inviteRepeatTicks",
            "inviteExtendTicks",
            "inviteMaxExtends",
            "strategicTtlTicks",
            "healFraction",
            "armedDps");

    private static final IntellectTuning DEFAULTS = new IntellectTuning(
            100, 200, 200, 0.15, 0.2, 8.0, 10.0, 1.0, 3, 1, 1.5, 0.75, 4.0, 9.0, 1, 8, 16, 1, 16, 1, 1200, 64, 32, 2, 2,
            3, 3600, 600, 1200, 3, 6000, 0.35, 6.0);

    public IntellectTuning {
        requireTicks("demandIntervalTicks", demandIntervalTicks);
        requireTicks("minSwitchTicks", minSwitchTicks);
        requireTicks("failBackoffTicks", failBackoffTicks);
        requireTicks("inviteTtlTicks", inviteTtlTicks);
        requireTicks("inviteRepeatTicks", inviteRepeatTicks);
        requireTicks("inviteExtendTicks", inviteExtendTicks);
        requireTicks("strategicTtlTicks", strategicTtlTicks);
        requireNonNegative("hysteresisMargin", hysteresisMargin);
        requireNonNegative("crowdPenalty", crowdPenalty);
        requireNonNegative("workCrowdPenalty", workCrowdPenalty);
        requirePositive("maxDemand", maxDemand);
        requireNonNegative("fleeMargin", fleeMargin);
        requirePositive("foodDays", foodDays);
        requirePositive("foodRation", foodRation);
        requirePositive("foodChildRation", foodChildRation);
        requireNonNegative("foodDemandMin", foodDemandMin);
        requireNonNegative("foodDemandMax", foodDemandMax);
        requirePositive("armedDps", armedDps);
        if (foodDemandMin > foodDemandMax) {
            throw new IllegalArgumentException("Минимум спроса еды " + foodDemandMin + " больше максимума "
                    + foodDemandMax + ": шкала перевёрнута, дефицит уменьшал бы добычу еды");
        }
        if (foodDemandMax > maxDemand) {
            throw new IllegalArgumentException("Максимум спроса еды " + foodDemandMax + " выше шкалы MAX_DEMAND "
                    + maxDemand + ": распределение сравнивало бы значения вне шкалы");
        }
        if (!Double.isFinite(healFraction) || healFraction <= 0 || healFraction >= 1) {
            throw new IllegalArgumentException("Порог лечения " + healFraction + " вне (0, 1): это доля здоровья, "
                    + "при которой дух идёт лечиться, а не дерётся");
        }
        requirePositive("maxFighters", maxFighters);
        requirePositive("forageWithdrawCount", forageWithdrawCount);
        requirePositive("forageWithdrawRadius", forageWithdrawRadius);
        requirePositive("forageHuntRadius", forageHuntRadius);
        requirePositive("forageHuntCount", forageHuntCount);
        requirePositive("forageFishRadius", forageFishRadius);
        requirePositive("forageFishCount", forageFishCount);
        requirePositive("forageFishDuration", forageFishDuration);
        requirePositive("oreTarget", oreTarget);
        requirePositive("ironTarget", ironTarget);
        requirePositive("weaponTarget", weaponTarget);
        requirePositive("teamMin", teamMin);
        requirePositive("teamMax", teamMax);
        if (teamMax < teamMin) {
            throw new IllegalArgumentException("Предел команды " + teamMin + ".." + teamMax + ": максимум меньше "
                    + "минимума, экспедицию нельзя собрать ни при каком составе");
        }
        requirePositive("inviteMaxExtends", inviteMaxExtends);
    }

    public static IntellectTuning defaults() {
        return DEFAULTS;
    }

    public static IntellectTuning loadDefault() {
        DataTables tables = DataTables.INSTANCE;
        if (tables == null) {
            // Bootstrap: DataTables ещё строится в этом же потоке, и один из его seed'ов уже спрашивает
            // коэффициенты (BoardConfig → ProcessingRules). До первого /reload действуют значения по умолчанию.
            return DEFAULTS;
        }
        return tables.intellect().current();
    }

    public static IntellectTuning load() {
        return parse(JsonResource.readClasspath(RESOURCE), RESOURCE);
    }

    public static IntellectTuning parse(String json, String origin) {
        JsonNode root = JsonResource.parse(json, origin);
        JsonResource.checkFields(root, FIELDS, origin, "intellect.json");
        try {
            return new IntellectTuning(
                    integer(root, origin, "demandIntervalTicks"),
                    integer(root, origin, "minSwitchTicks"),
                    integer(root, origin, "failBackoffTicks"),
                    decimal(root, origin, "hysteresisMargin"),
                    decimal(root, origin, "crowdPenalty"),
                    decimal(root, origin, "workCrowdPenalty"),
                    decimal(root, origin, "maxDemand"),
                    decimal(root, origin, "fleeMargin"),
                    JsonResource.integer(root.get("maxFighters"), origin, "maxFighters"),
                    decimal(root, origin, "foodDays"),
                    decimal(root, origin, "foodRation"),
                    decimal(root, origin, "foodChildRation"),
                    decimal(root, origin, "foodDemandMin"),
                    decimal(root, origin, "foodDemandMax"),
                    JsonResource.integer(root.get("forageWithdrawCount"), origin, "forageWithdrawCount"),
                    JsonResource.integer(root.get("forageWithdrawRadius"), origin, "forageWithdrawRadius"),
                    JsonResource.integer(root.get("forageHuntRadius"), origin, "forageHuntRadius"),
                    JsonResource.integer(root.get("forageHuntCount"), origin, "forageHuntCount"),
                    JsonResource.integer(root.get("forageFishRadius"), origin, "forageFishRadius"),
                    JsonResource.integer(root.get("forageFishCount"), origin, "forageFishCount"),
                    JsonResource.integer(root.get("forageFishDuration"), origin, "forageFishDuration"),
                    JsonResource.integer(root.get("oreTarget"), origin, "oreTarget"),
                    JsonResource.integer(root.get("ironTarget"), origin, "ironTarget"),
                    JsonResource.integer(root.get("weaponTarget"), origin, "weaponTarget"),
                    JsonResource.integer(root.get("teamMin"), origin, "teamMin"),
                    JsonResource.integer(root.get("teamMax"), origin, "teamMax"),
                    integer(root, origin, "inviteTtlTicks"),
                    integer(root, origin, "inviteRepeatTicks"),
                    integer(root, origin, "inviteExtendTicks"),
                    JsonResource.integer(root.get("inviteMaxExtends"), origin, "inviteMaxExtends"),
                    integer(root, origin, "strategicTtlTicks"),
                    decimal(root, origin, "healFraction"),
                    decimal(root, origin, "armedDps"));
        } catch (IllegalArgumentException error) {
            throw new IllegalStateException("Файл '" + origin + "': " + error.getMessage(), error);
        }
    }

    private static long integer(JsonNode root, String origin, String field) {
        return JsonResource.integer(root.get(field), origin, field);
    }

    private static double decimal(JsonNode root, String origin, String field) {
        return JsonResource.decimal(root.get(field), origin, field);
    }

    private static void requireTicks(String name, long ticks) {
        if (ticks < 1) {
            throw new IllegalArgumentException("Поле " + name + " = " + ticks
                    + ": интервал считается в тиках, он обязан быть не меньше 1, иначе действие повторяется "
                    + "каждый тик или срок уже истёк при рождении");
        }
    }

    private static void requirePositive(String name, int value) {
        if (value < 1) {
            throw new IllegalArgumentException("Поле " + name + " = " + value
                    + ": счётчик или цель обязаны быть не меньше 1, иначе задача недостижима");
        }
    }

    private static void requirePositive(String name, double value) {
        if (!Double.isFinite(value) || value <= 0) {
            throw new IllegalArgumentException("Поле " + name + " = " + value
                    + ": значение обязано быть конечным и больше 0, иначе расчёт вырождается");
        }
    }

    private static void requireNonNegative(String name, double value) {
        if (!Double.isFinite(value) || value < 0) {
            throw new IllegalArgumentException("Поле " + name + " = " + value
                    + ": значение обязано быть конечным и не меньше 0, иначе шкала спроса ломается");
        }
    }
}
