package com.theminespirit.core.tactics;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public enum FactName {
    SELF_HP_FRACTION("self.hpFraction", FactType.NUM),
    SELF_ARMOR("self.armor", FactType.NUM),
    SELF_DPS("self.dps", FactType.NUM),
    SELF_SPEED("self.speed", FactType.NUM),
    SELF_FOOD("self.food", FactType.NUM),
    SELF_BLOCKS("self.blocks", FactType.NUM),
    SELF_HAS_RANGED("self.hasRanged", FactType.BOOL),
    SELF_HAS_WATER_BUCKET("self.hasWaterBucket", FactType.BOOL),
    SELF_ALLIES("self.allies", FactType.NUM),
    SELF_HEAL_ITEMS("self.healItems", FactType.NUM),
    ENEMY_COUNT("enemy.count", FactType.NUM),
    ENEMY_VISIBLE_COUNT("enemy.visibleCount", FactType.NUM),
    ENEMY_NEAREST("enemy.nearest", FactType.NUM),
    ENEMY_NEAREST_EXPLOSIVE("enemy.nearestExplosive", FactType.NUM),
    ENEMY_TOTAL_HP("enemy.totalHp", FactType.NUM),
    ENEMY_TOTAL_DPS("enemy.totalDps", FactType.NUM),
    ENEMY_FASTEST("enemy.fastest", FactType.NUM),
    ENEMY_ANY_RANGED("enemy.anyRanged", FactType.BOOL),
    ENEMY_ANY_EXPLOSIVE("enemy.anyExplosive", FactType.BOOL),
    ENEMY_ANY_SWIMS("enemy.anySwims", FactType.BOOL),
    ENEMY_ANY_FLIES("enemy.anyFlies", FactType.BOOL),
    ENEMY_TARGETING_SELF("enemy.targetingSelf", FactType.BOOL),
    ENV_LAVA_NEAR("env.lavaNear", FactType.NUM),
    ENV_DROP_HEIGHT("env.dropHeight", FactType.NUM),
    ENV_LIGHT("env.light", FactType.NUM),
    ENV_NIGHT("env.night", FactType.BOOL),
    ENV_HOME_DISTANCE("env.homeDistance", FactType.NUM),
    ENV_HOME_REACHABLE("env.homeReachable", FactType.BOOL),
    ENV_COVER_DISTANCE("env.coverDistance", FactType.NUM),
    ENV_WATER_DISTANCE("env.waterDistance", FactType.NUM),
    ENV_ESCAPE_AVAILABILITY("env.escapeAvailability", FactType.NUM),
    ORDER_MODE("order.mode", FactType.ENUM),
    ORDER_ORDERED("order.ordered", FactType.ENUM),
    ORDER_INITIATES("order.initiates", FactType.BOOL),
    ORDER_ZONE_SET("order.zoneSet", FactType.BOOL),
    ORDER_ZONE_DISTANCE("order.zoneDistance", FactType.NUM),
    ORDER_LEASH_LEFT("order.leashLeft", FactType.NUM),
    ORDER_ENGAGE_LEFT("order.engageLeft", FactType.NUM),
    ORDER_WARD_PRESENT("order.wardPresent", FactType.BOOL),
    ORDER_PROTECTED_THREATENED("order.protectedThreatened", FactType.BOOL),
    ORDER_HELP_AVAILABLE("order.helpAvailable", FactType.BOOL),
    TIME_SINCE_HIT("time.sinceHit", FactType.NUM),
    TIME_SINCE_ENEMY_SEEN("time.sinceEnemySeen", FactType.NUM),
    TIME_IN_STATE("time.inState", FactType.NUM),
    STATE_TACTICAL("state.tactical", FactType.ENUM),
    DERIVED_WIN_MARGIN("derived.winMargin", FactType.NUM),
    DERIVED_SPEED_RATIO("derived.speedRatio", FactType.NUM);

    private static final Map<String, FactName> BY_KEY = new HashMap<>();

    static {
        for (FactName name : values()) {
            BY_KEY.put(name.key, name);
        }
    }

    private final String key;
    private final FactType type;

    FactName(String key, FactType type) {
        this.key = key;
        this.type = type;
    }

    public String key() {
        return key;
    }

    public FactType type() {
        return type;
    }

    public boolean computedAfterRetreatChoice() {
        return this == ENV_ESCAPE_AVAILABILITY;
    }

    public List<String> enumValues() {
        return switch (this) {
            case ORDER_MODE, ORDER_ORDERED -> Arrays.stream(CombatMode.values()).map(CombatMode::id).toList();
            case STATE_TACTICAL -> Arrays.stream(TacticalState.values()).map(TacticalState::id).toList();
            default -> List.of();
        };
    }

    public static Optional<FactName> byKey(String key) {
        return Optional.ofNullable(BY_KEY.get(key));
    }
}
