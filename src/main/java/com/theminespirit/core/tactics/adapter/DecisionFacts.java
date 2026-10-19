package com.theminespirit.core.tactics.adapter;

import com.theminespirit.core.tactics.EnemyFact;
import com.theminespirit.core.tactics.FactChecks;
import com.theminespirit.core.tactics.SelfFacts;
import com.theminespirit.core.tactics.Situation;
import java.util.List;
import java.util.Locale;

public record DecisionFacts(int enemies, String nearest, double hp, double maxHp, double armor, String weapon) {
    private static final double BARE_HAND_DPS = 1.0;

    public DecisionFacts {
        if (enemies < 0) {
            throw new IllegalArgumentException(
                    "DecisionFacts.enemies = " + enemies + ": врагов рядом не бывает " + "отрицательного числа");
        }
        if (maxHp < 1 || hp < 0 || hp > maxHp) {
            throw new IllegalArgumentException("DecisionFacts hp = " + hp + " из maxHp = " + maxHp
                    + ": здоровье духа лежит в пределах от 0 до максимума");
        }
        FactChecks.range("DecisionFacts.armor", armor, 0, SelfFacts.ARMOR_CAP);
    }

    public static DecisionFacts of(Situation situation) {
        SelfFacts self = situation.self();
        List<EnemyFact> enemies = situation.enemies();
        return new DecisionFacts(
                enemies.size(), nearest(situation), self.hp(), self.maxHp(), self.armor(), weapon(self));
    }

    private static String nearest(Situation situation) {
        List<EnemyFact> enemies = situation.enemies();
        if (enemies.isEmpty()) {
            return "нет";
        }
        EnemyFact enemy = enemies.get(0);
        String near = enemy.type() + " в " + distance(situation.distance(enemy));
        return enemy.count() > 1 ? near + " (" + enemy.count() + ")" : near;
    }

    private static String weapon(SelfFacts self) {
        String melee = self.dps() > BARE_HAND_DPS ? "оружие ближнего боя" : "руки";
        return self.hasRanged() ? melee + " и лук" : melee;
    }

    private static String distance(double blocks) {
        return String.format(Locale.ROOT, "%.1f", blocks);
    }
}
