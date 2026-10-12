package com.theminespirit.core.tactics;

import java.util.ArrayList;
import java.util.List;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;

final class Situations {
    private static final List<String> TYPES = List.of("minecraft:zombie", "minecraft:skeleton", "minecraft:creeper",
            "minecraft:spider", "minecraft:drowned", "minecraft:phantom", "minecraft:ravager", "minecraft:blaze",
            "minecraft:unknown_mob");

    private Situations() {
    }

    static Arbitrary<Situation> any() {
        return anyWith(Arbitraries.of(CombatMode.values()), 20);
    }

    static Arbitrary<Situation> anyWith(Arbitrary<CombatMode> modes, int maxEnemies) {
        return Combinators.combine(self(), enemies(maxEnemies), env(), orders(modes),
                Arbitraries.integers().between(0, 400), Arbitraries.integers().between(0, 400)).as(Situation::new);
    }

    static Arbitrary<SelfFacts> self() {
        Arbitrary<Double> fraction = Arbitraries.integers().between(0, 20).map(step -> step / 20.0);
        return Combinators.combine(Arbitraries.of(20.0, 40.0), fraction,
                Arbitraries.integers().between(0, 20).map(Integer::doubleValue),
                Arbitraries.of(0.5, 2.0, 4.0, 6.0, 9.0, 14.0), Arbitraries.of(0.1, 0.2, 0.3, 0.5),
                Arbitraries.integers().between(0, 4).map(step -> step / 4.0),
                Arbitraries.integers().between(0, 64), Arbitraries.integers().between(0, 30))
                .as((maxHp, hpFraction, armor, dps, speed, food, blocks, ammo) -> new SelfFacts(new Point(0, 0, 0),
                        maxHp * hpFraction, maxHp, armor, dps, speed, food, blocks, ammo > 15 ? 0 : ammo,
                        ammo % 3 == 0, ammo % 4, ammo % 5));
    }

    static Arbitrary<List<EnemyFact>> enemies(int maxSize) {
        Arbitrary<EnemyFact> enemy = Combinators.combine(Arbitraries.of(TYPES.toArray(String[]::new)),
                Arbitraries.integers().between(0, 400), Arbitraries.integers().between(0, 60).map(d -> d / 2.0),
                Arbitraries.integers().between(1, 8).map(step -> step / 8.0),
                Arbitraries.integers().between(0, 10).map(Integer::doubleValue), Arbitraries.of(true, true, false),
                Arbitraries.of(true, false), Arbitraries.integers().between(1, 3))
                .as((type, id, distance, hp, armor, visible, targeting, count) -> new EnemyFact(type, "id" + id,
                        new Point(distance, 0, 0), hp, armor, visible, targeting, count));
        return enemy.list().ofMaxSize(maxSize);
    }

    static Arbitrary<EnvFacts> env() {
        Arbitrary<Double> near = Arbitraries.of(0.0, 1.0, 2.0, 3.0, 6.0, 20.0, EnvFacts.FAR_DISTANCE);
        Arbitrary<Double> far = Arbitraries.integers().between(0, 200).map(Integer::doubleValue);
        return Combinators.combine(near, Arbitraries.of(0.0, 2.0, 5.0, 12.0), Arbitraries.integers().between(0, 15),
                Arbitraries.of(TimeOfDay.values()), far, Arbitraries.of(true, false), near, near)
                .as((lava, drop, light, time, home, reachable, cover, water) -> new EnvFacts(lava, drop, light, time,
                        home, reachable, cover, water));
    }

    static Arbitrary<Orders> orders(Arbitrary<CombatMode> modes) {
        Arbitrary<Double> radius = Arbitraries.of(0.0, 8.0, 16.0);
        return Combinators.combine(modes, radius, Arbitraries.integers().between(-10, 10),
                Arbitraries.of(true, false), Arbitraries.of(true, false), Arbitraries.of(true, false))
                .as((mode, zoneRadius, offset, hasWard, threatened, help) -> new Orders(mode,
                        zoneRadius == 0 ? null : new Point(offset, 0, 0), zoneRadius,
                        hasWard ? new Point(offset, 0, 3) : null, hasWard && threatened, help));
    }

    static Situation withSelf(Situation base, SelfFacts self) {
        return new Situation(self, new ArrayList<>(base.enemies()), base.env(), base.orders(), base.ticksSinceHit(),
                base.ticksSinceStrike());
    }

    static Situation withEnemies(Situation base, List<EnemyFact> enemies) {
        return new Situation(base.self(), enemies, base.env(), base.orders(), base.ticksSinceHit(),
                base.ticksSinceStrike());
    }

    static SelfFacts withStats(SelfFacts self, double hp, double armor, double dps) {
        return new SelfFacts(self.pos(), hp, self.maxHp(), armor, dps, self.speed(), self.food(), self.blocks(),
                self.rangedAmmo(), self.hasWaterBucket(), self.allies(), self.healItems());
    }
}
