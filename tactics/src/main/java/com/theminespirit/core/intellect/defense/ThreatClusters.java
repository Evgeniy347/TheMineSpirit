package com.theminespirit.core.intellect.defense;

import com.theminespirit.core.tactics.EnemyFact;
import com.theminespirit.core.tactics.EnemyProfile;
import com.theminespirit.core.tactics.EnemyProfiles;
import com.theminespirit.core.tactics.Point;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

final class ThreatClusters {
    private ThreatClusters() {}

    static List<ThreatCluster> group(
            List<EnemyFact> enemies,
            Point center,
            EnemyProfiles profiles,
            DefenseProfile profile,
            DefenseWeights weights) {
        if (enemies == null) {
            throw new IllegalArgumentException("enemies не может быть null: нет врагов — пустой список");
        }
        if (center == null) {
            throw new IllegalArgumentException("center не может быть null: сектора считаются от защищаемой точки");
        }
        if (enemies.stream().anyMatch(enemy -> enemy == null)) {
            throw new IllegalArgumentException("enemies содержит null: враг без факта не входит в угрозу");
        }
        int sectors = DefenseTuning.SECTORS;
        double[] count = new double[sectors];
        double[] weight = new double[sectors];
        double[] x = new double[sectors];
        double[] z = new double[sectors];
        List<List<EnemyFact>> groups = new ArrayList<>(sectors);
        for (int index = 0; index < sectors; index++) {
            groups.add(new ArrayList<>());
        }
        for (EnemyFact enemy : enemies) {
            int sector = sectorOf(enemy.pos(), center, sectors);
            int amount = enemy.count();
            count[sector] += amount;
            EnemyProfile enemyProfile = profiles.profile(enemy.type());
            TargetClass cls = profile.classify(enemy.type(), enemyProfile);
            weight[sector] += amount * weights.threatWeight().getOrDefault(cls, 1.0);
            x[sector] += enemy.pos().x() * amount;
            z[sector] += enemy.pos().z() * amount;
            groups.get(sector).add(enemy);
        }
        List<ThreatCluster> clusters = new ArrayList<>();
        for (int sector = 0; sector < sectors; sector++) {
            if (count[sector] <= 0) {
                continue;
            }
            double bearing = (sector + 0.5) * 2 * Math.PI / sectors;
            clusters.add(new ThreatCluster(
                    bearing,
                    (int) Math.round(count[sector]),
                    weight[sector],
                    new Point(x[sector] / count[sector], center.y(), z[sector] / count[sector]),
                    groups.get(sector)));
        }
        clusters.sort(Comparator.comparingDouble(ThreatCluster::bearing));
        return List.copyOf(clusters);
    }

    static int sectorOf(Point pos, Point center, int sectors) {
        double angle = Math.atan2(pos.z() - center.z(), pos.x() - center.x());
        double normalized = angle < 0 ? angle + 2 * Math.PI : angle;
        return Math.min(sectors - 1, (int) (normalized / (2 * Math.PI / sectors)));
    }
}
