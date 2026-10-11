package com.theminespirit.core.tactics;

public record EnemyMetrics(int count, int visibleCount, double nearest, double nearestExplosive,
        double nearestRanged, int rangedCount, double totalHp, double effectiveHp, double totalDps, double fastest,
        boolean anyRanged, boolean anyExplosive, boolean anySwims, boolean anyFlies, boolean targetingSelf) {
    public static EnemyMetrics of(Situation situation, EnemyProfiles profiles, ArmorModel armor) {
        Accumulator sum = new Accumulator();
        for (EnemyFact enemy : situation.enemies()) {
            sum.add(enemy, profiles.profile(enemy.type()), situation.distance(enemy), armor);
        }
        return sum.result();
    }

    private static final class Accumulator {
        private int count;
        private int visible;
        private double nearest = EnvFacts.FAR_DISTANCE;
        private double nearestExplosive = EnvFacts.FAR_DISTANCE;
        private double nearestRanged = EnvFacts.FAR_DISTANCE;
        private int ranged;
        private double hp;
        private double effective;
        private double dps;
        private double fastest;
        private boolean anyExplosive;
        private boolean anySwims;
        private boolean anyFlies;
        private boolean targeting;

        void add(EnemyFact enemy, EnemyProfile profile, double distance, ArmorModel armor) {
            int n = enemy.count();
            count += n;
            visible += enemy.visible() ? n : 0;
            nearest = Math.min(nearest, distance);
            double health = profile.hp() * enemy.hpFraction() * n;
            hp += health;
            effective += armor.effectiveHp(health, enemy.armor());
            dps += profile.dps() * n;
            fastest = Math.max(fastest, profile.speed());
            targeting |= enemy.targetingSelf();
            if (profile.explosive()) {
                anyExplosive = true;
                nearestExplosive = Math.min(nearestExplosive, distance);
            }
            if (profile.ranged()) {
                ranged += n;
                nearestRanged = Math.min(nearestRanged, distance);
            }
            anySwims |= profile.swims();
            anyFlies |= profile.flies();
        }

        EnemyMetrics result() {
            return new EnemyMetrics(count, visible, nearest, nearestExplosive, nearestRanged, ranged, hp, effective,
                    dps, fastest, ranged > 0, anyExplosive, anySwims, anyFlies, targeting);
        }
    }
}
