package com.theminespirit.core.intellect;

import com.fasterxml.jackson.databind.JsonNode;
import com.theminespirit.core.inventory.JsonResource;
import com.theminespirit.core.reload.TableSlot;
import java.util.Set;
import java.util.function.Supplier;

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

    private static final String MAX_FIGHTERS = "maxFighters";
    private static final String FORAGE_WITHDRAW_COUNT = "forageWithdrawCount";
    private static final String FORAGE_WITHDRAW_RADIUS = "forageWithdrawRadius";
    private static final String FORAGE_HUNT_RADIUS = "forageHuntRadius";
    private static final String FORAGE_HUNT_COUNT = "forageHuntCount";
    private static final String FORAGE_FISH_RADIUS = "forageFishRadius";
    private static final String FORAGE_FISH_COUNT = "forageFishCount";
    private static final String FORAGE_FISH_DURATION = "forageFishDuration";
    private static final String ORE_TARGET = "oreTarget";
    private static final String IRON_TARGET = "ironTarget";
    private static final String WEAPON_TARGET = "weaponTarget";
    private static final String TEAM_MIN = "teamMin";
    private static final String TEAM_MAX = "teamMax";
    private static final String INVITE_MAX_EXTENDS = "inviteMaxExtends";
    private static final Set<String> FIELDS = Set.of(
            "demandIntervalTicks",
            "minSwitchTicks",
            "failBackoffTicks",
            "hysteresisMargin",
            "crowdPenalty",
            "workCrowdPenalty",
            "maxDemand",
            "fleeMargin",
            MAX_FIGHTERS,
            "foodDays",
            "foodRation",
            "foodChildRation",
            "foodDemandMin",
            "foodDemandMax",
            FORAGE_WITHDRAW_COUNT,
            FORAGE_WITHDRAW_RADIUS,
            FORAGE_HUNT_RADIUS,
            FORAGE_HUNT_COUNT,
            FORAGE_FISH_RADIUS,
            FORAGE_FISH_COUNT,
            FORAGE_FISH_DURATION,
            ORE_TARGET,
            IRON_TARGET,
            WEAPON_TARGET,
            TEAM_MIN,
            TEAM_MAX,
            "inviteTtlTicks",
            "inviteRepeatTicks",
            "inviteExtendTicks",
            INVITE_MAX_EXTENDS,
            "strategicTtlTicks",
            "healFraction",
            "armedDps");

    private static final IntellectTuning DEFAULTS = new IntellectTuning(
            100, 200, 200, 0.15, 0.2, 8.0, 10.0, 1.0, 3, 1, 1.5, 0.75, 4.0, 9.0, 1, 8, 16, 1, 16, 1, 1200, 64, 32, 2, 2,
            3, 3600, 600, 1200, 3, 6000, 0.35, 6.0);

    private static final TableSlot<IntellectTuning> SOURCE = new TableSlot<>(IntellectTuning::defaults);

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
        requirePositive(MAX_FIGHTERS, maxFighters);
        requirePositive(FORAGE_WITHDRAW_COUNT, forageWithdrawCount);
        requirePositive(FORAGE_WITHDRAW_RADIUS, forageWithdrawRadius);
        requirePositive(FORAGE_HUNT_RADIUS, forageHuntRadius);
        requirePositive(FORAGE_HUNT_COUNT, forageHuntCount);
        requirePositive(FORAGE_FISH_RADIUS, forageFishRadius);
        requirePositive(FORAGE_FISH_COUNT, forageFishCount);
        requirePositive(FORAGE_FISH_DURATION, forageFishDuration);
        requirePositive(ORE_TARGET, oreTarget);
        requirePositive(IRON_TARGET, ironTarget);
        requirePositive(WEAPON_TARGET, weaponTarget);
        requirePositive(TEAM_MIN, teamMin);
        requirePositive(TEAM_MAX, teamMax);
        if (teamMax < teamMin) {
            throw new IllegalArgumentException("Предел команды " + teamMin + ".." + teamMax + ": максимум меньше "
                    + "минимума, экспедицию нельзя собрать ни при каком составе");
        }
        requirePositive(INVITE_MAX_EXTENDS, inviteMaxExtends);
    }

    public static IntellectTuning defaults() {
        return DEFAULTS;
    }

    public static IntellectTuning loadDefault() {
        return SOURCE.current();
    }

    public static void useSource(Supplier<IntellectTuning> supplier) {
        SOURCE.use(supplier);
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
                    JsonResource.integer(root.get(MAX_FIGHTERS), origin, MAX_FIGHTERS),
                    decimal(root, origin, "foodDays"),
                    decimal(root, origin, "foodRation"),
                    decimal(root, origin, "foodChildRation"),
                    decimal(root, origin, "foodDemandMin"),
                    decimal(root, origin, "foodDemandMax"),
                    JsonResource.integer(root.get(FORAGE_WITHDRAW_COUNT), origin, FORAGE_WITHDRAW_COUNT),
                    JsonResource.integer(root.get(FORAGE_WITHDRAW_RADIUS), origin, FORAGE_WITHDRAW_RADIUS),
                    JsonResource.integer(root.get(FORAGE_HUNT_RADIUS), origin, FORAGE_HUNT_RADIUS),
                    JsonResource.integer(root.get(FORAGE_HUNT_COUNT), origin, FORAGE_HUNT_COUNT),
                    JsonResource.integer(root.get(FORAGE_FISH_RADIUS), origin, FORAGE_FISH_RADIUS),
                    JsonResource.integer(root.get(FORAGE_FISH_COUNT), origin, FORAGE_FISH_COUNT),
                    JsonResource.integer(root.get(FORAGE_FISH_DURATION), origin, FORAGE_FISH_DURATION),
                    JsonResource.integer(root.get(ORE_TARGET), origin, ORE_TARGET),
                    JsonResource.integer(root.get(IRON_TARGET), origin, IRON_TARGET),
                    JsonResource.integer(root.get(WEAPON_TARGET), origin, WEAPON_TARGET),
                    JsonResource.integer(root.get(TEAM_MIN), origin, TEAM_MIN),
                    JsonResource.integer(root.get(TEAM_MAX), origin, TEAM_MAX),
                    integer(root, origin, "inviteTtlTicks"),
                    integer(root, origin, "inviteRepeatTicks"),
                    integer(root, origin, "inviteExtendTicks"),
                    JsonResource.integer(root.get(INVITE_MAX_EXTENDS), origin, INVITE_MAX_EXTENDS),
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
            throw invalid(
                    name,
                    ticks,
                    ": интервал считается в тиках, он обязан быть не меньше 1, иначе действие повторяется "
                            + "каждый тик или срок уже истёк при рождении");
        }
    }

    private static void requirePositive(String name, int value) {
        if (value < 1) {
            throw invalid(name, value, ": счётчик или цель обязаны быть не меньше 1, иначе задача недостижима");
        }
    }

    private static void requirePositive(String name, double value) {
        if (!Double.isFinite(value) || value <= 0) {
            throw invalid(name, value, ": значение обязано быть конечным и больше 0, иначе расчёт вырождается");
        }
    }

    private static void requireNonNegative(String name, double value) {
        if (!Double.isFinite(value) || value < 0) {
            throw invalid(name, value, ": значение обязано быть конечным и не меньше 0, иначе шкала спроса ломается");
        }
    }

    private static IllegalArgumentException invalid(String name, Object value, String why) {
        return new IllegalArgumentException("Поле " + name + " = " + value + why);
    }
}
