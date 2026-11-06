package com.theminespirit.core.tactics;

import java.util.ArrayList;
import java.util.List;

final class SituationBuilder {
    private Point pos = new Point(0, 0, 0);
    private double hp = ScenarioDefaults.HP;
    private double maxHp = ScenarioDefaults.MAX_HP;
    private double armor = ScenarioDefaults.ARMOR;
    private double dps = ScenarioDefaults.DPS;
    private double speed = ScenarioDefaults.SPEED;
    private double food = ScenarioDefaults.FOOD;
    private int blocks = ScenarioDefaults.BLOCKS;
    private int ammo = ScenarioDefaults.AMMO;
    private boolean bucket;
    private int allies = ScenarioDefaults.ALLIES;
    private int healItems = ScenarioDefaults.HEAL_ITEMS;
    private final List<EnemyFact> enemies = new ArrayList<>();
    private double lava = ScenarioDefaults.FAR;
    private double drop;
    private int light = ScenarioDefaults.LIGHT;
    private TimeOfDay time = TimeOfDay.DAY;
    private double homeDistance = ScenarioDefaults.FAR;
    private boolean homeReachable;
    private double coverDistance = ScenarioDefaults.FAR;
    private double waterDistance = ScenarioDefaults.FAR;
    private CombatMode mode = ScenarioDefaults.MODE;
    private Point zoneCenter;
    private double zoneRadius;
    private Point ward;
    private boolean wardThreatened;
    private boolean help;
    private int sinceHit = Situation.MAX_TICKS;
    private int sinceStrike = Situation.MAX_TICKS;
    private int nextId;

    static SituationBuilder create() {
        return new SituationBuilder();
    }

    SituationBuilder pos(double x, double z) {
        pos = new Point(x, 0, z);
        return this;
    }

    SituationBuilder hpFraction(double fraction) {
        hp = maxHp * fraction;
        return this;
    }

    SituationBuilder hp(double value) {
        hp = value;
        return this;
    }

    SituationBuilder armor(double value) {
        armor = value;
        return this;
    }

    SituationBuilder dps(double value) {
        dps = value;
        return this;
    }

    SituationBuilder speed(double value) {
        speed = value;
        return this;
    }

    SituationBuilder food(double value) {
        food = value;
        return this;
    }

    SituationBuilder blocks(int value) {
        blocks = value;
        return this;
    }

    SituationBuilder ammo(int value) {
        ammo = value;
        return this;
    }

    SituationBuilder bucket(boolean value) {
        bucket = value;
        return this;
    }

    SituationBuilder allies(int value) {
        allies = value;
        return this;
    }

    SituationBuilder healItems(int value) {
        healItems = value;
        return this;
    }

    SituationBuilder enemy(String type, double distance) {
        return enemies(type, distance, 1);
    }

    SituationBuilder enemies(String type, double distance, int count) {
        for (int i = 0; i < count; i++) {
            enemies.add(new EnemyFact(
                    qualified(type),
                    "e" + nextId++,
                    new Point(distance, 0, 0),
                    ScenarioDefaults.ENEMY_HP_FRACTION,
                    ScenarioDefaults.ENEMY_ARMOR,
                    true,
                    true));
        }
        return this;
    }

    SituationBuilder enemy(EnemyFact fact) {
        enemies.add(fact);
        return this;
    }

    SituationBuilder lava(double distance) {
        lava = distance;
        return this;
    }

    SituationBuilder drop(double height) {
        drop = height;
        return this;
    }

    SituationBuilder light(int value) {
        light = value;
        return this;
    }

    SituationBuilder time(TimeOfDay value) {
        time = value;
        return this;
    }

    SituationBuilder home(double distance) {
        homeDistance = distance;
        homeReachable = true;
        return this;
    }

    SituationBuilder cover(double distance) {
        coverDistance = distance;
        return this;
    }

    SituationBuilder water(double distance) {
        waterDistance = distance;
        return this;
    }

    SituationBuilder mode(CombatMode value) {
        mode = value;
        return this;
    }

    SituationBuilder zone(double x, double z, double radius) {
        zoneCenter = new Point(x, 0, z);
        zoneRadius = radius;
        return this;
    }

    SituationBuilder ward(double x, double z, boolean threatened) {
        ward = new Point(x, 0, z);
        wardThreatened = threatened;
        return this;
    }

    SituationBuilder help(boolean value) {
        help = value;
        return this;
    }

    SituationBuilder sinceHit(int ticks) {
        sinceHit = ticks;
        return this;
    }

    SituationBuilder sinceStrike(int ticks) {
        sinceStrike = ticks;
        return this;
    }

    Situation build() {
        SelfFacts self =
                new SelfFacts(pos, hp, maxHp, armor, dps, speed, food, blocks, ammo, bucket, allies, healItems);
        EnvFacts env = new EnvFacts(lava, drop, light, time, homeDistance, homeReachable, coverDistance, waterDistance);
        Orders orders = new Orders(mode, zoneCenter, zoneRadius, ward, wardThreatened, help);
        return new Situation(self, enemies, env, orders, sinceHit, sinceStrike);
    }

    private static String qualified(String type) {
        return type.contains(":") ? type : "minecraft:" + type;
    }
}
