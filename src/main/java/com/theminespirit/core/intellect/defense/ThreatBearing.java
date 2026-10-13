package com.theminespirit.core.intellect.defense;

import com.theminespirit.core.tactics.EnemyFact;
import com.theminespirit.core.tactics.EnemyProfile;
import com.theminespirit.core.tactics.EnemyProfiles;
import com.theminespirit.core.tactics.Point;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class ThreatBearing {
    private ThreatBearing() {
    }

    public static List<DirectionThreat> sum(List<EnemyFact> enemies, Point center, EnemyProfiles profiles) {
        if (enemies == null) {
            throw new IllegalArgumentException("enemies не может быть null: нет врагов — пустой список");
        }
        if (center == null) {
            throw new IllegalArgumentException("center не может быть null: сектора считаются от защищаемой точки");
        }
        if (profiles == null) {
            throw new IllegalArgumentException("profiles не может быть null: сила врага берётся из enemies.json");
        }
        int sectors = DefenseTuning.SECTORS;
        double[] strength = new double[sectors];
        int[] count = new int[sectors];
        for (EnemyFact enemy : enemies) {
            int sector = sectorOf(enemy.pos(), center, sectors);
            EnemyProfile profile = profiles.profile(enemy.type());
            strength[sector] += profile.hp() * Math.max(DefenseTuning.MIN_DPS, profile.dps()) * enemy.count();
            count[sector] += enemy.count();
        }
        List<DirectionThreat> threats = new ArrayList<>();
        for (int sector = 0; sector < sectors; sector++) {
            if (count[sector] > 0) {
                threats.add(new DirectionThreat((sector + 0.5) * 2 * Math.PI / sectors, strength[sector],
                        count[sector]));
            }
        }
        threats.sort(Comparator.comparingDouble(DirectionThreat::bearing));
        return List.copyOf(threats);
    }

    private static int sectorOf(Point pos, Point center, int sectors) {
        double angle = Math.atan2(pos.z() - center.z(), pos.x() - center.x());
        double normalized = angle < 0 ? angle + 2 * Math.PI : angle;
        return Math.min(sectors - 1, (int) (normalized / (2 * Math.PI / sectors)));
    }
}
