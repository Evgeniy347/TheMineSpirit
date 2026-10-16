package com.theminespirit.core.intellect;

import com.theminespirit.core.coordinator.SpiritRole;
import com.theminespirit.core.coordinator.SpiritSnapshot;
import com.theminespirit.core.tactics.ArmorModel;
import com.theminespirit.core.tactics.CombatMode;
import com.theminespirit.core.tactics.EnemyFact;
import com.theminespirit.core.tactics.EnemyMetrics;
import com.theminespirit.core.tactics.EnemyProfiles;
import com.theminespirit.core.tactics.EnvFacts;
import com.theminespirit.core.tactics.FactorScales;
import com.theminespirit.core.tactics.Orders;
import com.theminespirit.core.tactics.SelfFacts;
import com.theminespirit.core.tactics.Situation;
import com.theminespirit.core.tactics.TacticsConfig;
import com.theminespirit.core.tactics.TimeOfDay;
import com.theminespirit.core.tactics.WinMargin;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class StrengthAssessor {
    private StrengthAssessor() {
    }

    public static List<StrengthVerdict> assess(List<SpiritSnapshot> spirits, List<EnemyFact> enemies,
            EnemyProfiles profiles, ArmorModel armor, boolean weaponAvailable) {
        if (spirits == null) {
            throw new IllegalArgumentException("Оценка сил без духов не считается: бой ведут конкретные духи");
        }
        if (enemies == null) {
            throw new IllegalArgumentException("Оценка сил без врагов не считается: пустой список — нет угроз");
        }
        if (profiles == null) {
            throw new IllegalArgumentException("Оценка сил без профилей врагов не считается: урон берут из enemies.json");
        }
        if (armor == null) {
            throw new IllegalArgumentException("Оценка сил без модели брони не считается: входит в запас победы");
        }
        if (enemies.isEmpty()) {
            return List.of();
        }
        FactorScales scales = scalesWith(armor);
        List<SpiritSnapshot> ordered = spirits.stream().sorted(Comparator.comparing(SpiritSnapshot::id)).toList();
        List<StrengthVerdict> verdicts = new ArrayList<>();
        List<Fighter> fighters = new ArrayList<>();
        for (SpiritSnapshot spirit : ordered) {
            EnemyFact nearest = nearest(spirit, enemies);
            SelfFacts self = selfFacts(spirit);
            double margin = marginOf(self, nearest, profiles, armor, scales);
            Optional<UUID> enemy = enemyId(nearest);
            if (spirit.hpFraction() < IntellectTuning.HEAL_FRACTION) {
                verdicts.add(new StrengthVerdict(spirit.id(), StrengthAction.HEAL, margin, enemy));
            } else if (self.dps() <= 1 && weaponAvailable) {
                verdicts.add(new StrengthVerdict(spirit.id(), StrengthAction.ARM, margin, enemy));
            } else if (spirit.threatsNear() > 0 || spirit.attacked()) {
                fighters.add(new Fighter(spirit.id(), margin, enemy));
            }
        }
        fighters.sort(Comparator.comparingDouble(Fighter::margin).reversed().thenComparing(Fighter::spirit));
        int target = fighterTarget(enemies, profiles);
        for (int index = 0; index < fighters.size(); index++) {
            Fighter fighter = fighters.get(index);
            if (index < target && fighter.margin() >= 1 - IntellectTuning.FLEE_MARGIN) {
                verdicts.add(new StrengthVerdict(fighter.spirit(), StrengthAction.FIGHT, fighter.margin(),
                        fighter.enemy()));
            } else {
                verdicts.add(new StrengthVerdict(fighter.spirit(), StrengthAction.FLEE, fighter.margin(),
                        fighter.enemy()));
            }
        }
        verdicts.sort(Comparator.comparing(StrengthVerdict::spirit));
        return List.copyOf(verdicts);
    }

    public static int fighterTarget(List<EnemyFact> enemies, EnemyProfiles profiles) {
        if (enemies == null) {
            throw new IllegalArgumentException("Число бойцов без врагов не считается: пустой список — нет угроз");
        }
        if (profiles == null) {
            throw new IllegalArgumentException("Число бойцов без профилей не считается: сила врага — из enemies.json");
        }
        double totalHp = 0;
        for (EnemyFact enemy : enemies) {
            totalHp += profiles.profile(enemy.type()).hp() * enemy.count();
        }
        return Math.min(IntellectTuning.MAX_FIGHTERS, Math.max(1, (int) Math.ceil(totalHp / 30)));
    }

    private static SelfFacts selfFacts(SpiritSnapshot spirit) {
        return new SelfFacts(spirit.pos(), spirit.hpFraction() * 20, 20, 0,
                spirit.role() == SpiritRole.DEFENDER ? IntellectTuning.ARMED_DPS : 1, 0.25, spirit.food(), 0, 0,
                false, 0, 0);
    }

    private static double marginOf(SelfFacts self, EnemyFact enemy, EnemyProfiles profiles, ArmorModel armor,
            FactorScales scales) {
        Situation duel = new Situation(self, List.of(enemy), neutralEnv(), Orders.mode(CombatMode.DEFENSIVE), 0, 0);
        EnemyMetrics metrics = EnemyMetrics.of(duel, profiles, armor);
        return WinMargin.of(self, metrics, scales);
    }

    private static EnvFacts neutralEnv() {
        return new EnvFacts(EnvFacts.FAR_DISTANCE, 0, EnvFacts.MAX_LIGHT, TimeOfDay.DAY, EnvFacts.FAR_DISTANCE,
                false, EnvFacts.FAR_DISTANCE, EnvFacts.FAR_DISTANCE);
    }

    private static FactorScales scalesWith(ArmorModel armor) {
        FactorScales defaults = TacticsConfig.loadDefault().weights().scales();
        return new FactorScales(armor, defaults.minDps(), defaults.winMarginDivisor(), defaults.lowHpFloor(),
                defaults.proximityRange(), defaults.rangedRange(), defaults.rangedFull(), defaults.explosiveRange(),
                defaults.allyFull(), defaults.routesFull(), defaults.lavaRange(), defaults.dropSafe(),
                defaults.dropRange(), defaults.nightFloor(), defaults.hungerThreshold(), defaults.healFull(),
                defaults.leashMargin(), defaults.speedRatioCap(), defaults.urgencyHorizon());
    }

    private static EnemyFact nearest(SpiritSnapshot spirit, List<EnemyFact> enemies) {
        EnemyFact nearest = null;
        double nearestDistance = Double.POSITIVE_INFINITY;
        for (EnemyFact enemy : enemies) {
            double distance = enemy.pos().distanceTo(spirit.pos());
            if (nearest == null || distance < nearestDistance
                    || (distance == nearestDistance && enemy.id().compareTo(nearest.id()) < 0)) {
                nearest = enemy;
                nearestDistance = distance;
            }
        }
        return nearest;
    }

    private static Optional<UUID> enemyId(EnemyFact enemy) {
        try {
            return Optional.of(UUID.fromString(enemy.id()));
        } catch (IllegalArgumentException notUuid) {
            return Optional.empty();
        }
    }

    private record Fighter(UUID spirit, double margin, Optional<UUID> enemy) {
    }
}
