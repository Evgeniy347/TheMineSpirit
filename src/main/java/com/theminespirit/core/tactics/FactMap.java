package com.theminespirit.core.tactics;

import java.util.Arrays;

public final class FactMap {
    private final Object[] values = new Object[FactName.values().length];

    private FactMap() {}

    public static FactMap from(Situation situation, Assessment assessment) {
        FactMap map = new FactMap();
        map.putSelf(situation.self());
        map.putEnemies(assessment.enemies());
        map.putEnv(situation.env(), assessment.escapeRoutes());
        map.putOrders(situation.orders(), assessment);
        map.put(FactName.TIME_SINCE_HIT, situation.ticksSinceHit());
        map.put(FactName.TIME_SINCE_STRIKE, situation.ticksSinceStrike());
        map.put(FactName.TIME_SINCE_ENEMY_SEEN, (double) assessment.ticksSinceEnemySeen());
        map.put(FactName.TIME_IN_STATE, (double) assessment.ticksInState());
        map.put(FactName.STATE_TACTICAL, assessment.state().id());
        map.put(FactName.DERIVED_WIN_MARGIN, assessment.winMargin());
        map.put(FactName.DERIVED_SPEED_RATIO, assessment.speedRatio());
        return map;
    }

    public double number(FactName name) {
        return (Double) get(name, FactType.NUM);
    }

    public boolean bool(FactName name) {
        return (Boolean) get(name, FactType.BOOL);
    }

    public String text(FactName name) {
        return (String) get(name, FactType.ENUM);
    }

    private Object get(FactName name, FactType expected) {
        if (name.type() != expected) {
            throw new IllegalArgumentException("Факт " + name.key() + " имеет тип " + name.type() + ", а запрошен "
                    + expected + ": проверьте тип значения в условии");
        }
        return values[name.ordinal()];
    }

    private void putSelf(SelfFacts self) {
        put(FactName.SELF_HP_FRACTION, self.hpFraction());
        put(FactName.SELF_ARMOR, self.armor());
        put(FactName.SELF_DPS, self.dps());
        put(FactName.SELF_SPEED, self.speed());
        put(FactName.SELF_FOOD, self.food());
        put(FactName.SELF_BLOCKS, self.blocks());
        put(FactName.SELF_HAS_RANGED, self.hasRanged());
        put(FactName.SELF_HAS_WATER_BUCKET, self.hasWaterBucket());
        put(FactName.SELF_ALLIES, self.allies());
        put(FactName.SELF_HEAL_ITEMS, self.healItems());
    }

    private void putEnemies(EnemyMetrics enemies) {
        put(FactName.ENEMY_COUNT, enemies.count());
        put(FactName.ENEMY_VISIBLE_COUNT, enemies.visibleCount());
        put(FactName.ENEMY_NEAREST, enemies.nearest());
        put(FactName.ENEMY_NEAREST_EXPLOSIVE, enemies.nearestExplosive());
        put(FactName.ENEMY_TOTAL_HP, enemies.totalHp());
        put(FactName.ENEMY_TOTAL_DPS, enemies.totalDps());
        put(FactName.ENEMY_FASTEST, enemies.fastest());
        put(FactName.ENEMY_ANY_RANGED, enemies.anyRanged());
        put(FactName.ENEMY_ANY_EXPLOSIVE, enemies.anyExplosive());
        put(FactName.ENEMY_ANY_SWIMS, enemies.anySwims());
        put(FactName.ENEMY_ANY_FLIES, enemies.anyFlies());
        put(FactName.ENEMY_TARGETING_SELF, enemies.targetingSelf());
    }

    private void putEnv(EnvFacts env, int escapeRoutes) {
        put(FactName.ENV_LAVA_NEAR, env.lavaNear());
        put(FactName.ENV_DROP_HEIGHT, env.dropHeight());
        put(FactName.ENV_LIGHT, env.light());
        put(FactName.ENV_NIGHT, env.night());
        put(FactName.ENV_HOME_DISTANCE, env.homeDistance());
        put(FactName.ENV_HOME_REACHABLE, env.homeReachable());
        put(FactName.ENV_COVER_DISTANCE, env.coverDistance());
        put(FactName.ENV_WATER_DISTANCE, env.waterDistance());
        put(FactName.ENV_ESCAPE_AVAILABILITY, escapeRoutes);
    }

    private void putOrders(Orders orders, Assessment assessment) {
        put(FactName.ORDER_MODE, assessment.mode().id());
        put(FactName.ORDER_ORDERED, orders.mode().id());
        put(FactName.ORDER_INITIATES, assessment.initiates());
        put(FactName.ORDER_ZONE_SET, orders.zoneSet());
        put(FactName.ORDER_ZONE_DISTANCE, assessment.zoneDistance());
        put(FactName.ORDER_LEASH_LEFT, assessment.leashLeft());
        put(FactName.ORDER_ENGAGE_LEFT, assessment.engageLeft());
        put(FactName.ORDER_WARD_PRESENT, orders.wardPresent());
        put(FactName.ORDER_PROTECTED_THREATENED, orders.wardThreatened());
        put(FactName.ORDER_HELP_AVAILABLE, orders.helpAvailable());
    }

    private void put(FactName name, double value) {
        values[name.ordinal()] = value;
    }

    private void put(FactName name, boolean value) {
        values[name.ordinal()] = value;
    }

    private void put(FactName name, String value) {
        values[name.ordinal()] = value;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof FactMap map && Arrays.equals(values, map.values);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(values);
    }

    @Override
    public String toString() {
        StringBuilder text = new StringBuilder("FactMap{");
        for (FactName name : FactName.values()) {
            text.append(name.key()).append('=').append(values[name.ordinal()]).append(' ');
        }
        return text.append('}').toString();
    }
}
