package com.theminespirit.core.intellect.defense;

import com.theminespirit.core.coordinator.DefenseAssignment;
import com.theminespirit.core.coordinator.SectorAssigner;
import com.theminespirit.core.tactics.EnemyFact;
import com.theminespirit.core.tactics.EnemyProfiles;
import com.theminespirit.core.tactics.Point;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class DefenseAllocator {
    private DefenseAllocator() {}

    public static List<DefenseAssignment> allocate(
            Point center,
            double radius,
            List<DefenderCapability> defenders,
            List<EnemyFact> enemies,
            EnemyProfiles profiles) {
        if (center == null) {
            throw new IllegalArgumentException("center не может быть null: круг без центра не построить");
        }
        if (radius <= 0) {
            throw new IllegalArgumentException(
                    "radius = " + radius + ": радиус обороны обязан быть положительным, иначе сектора вырождены");
        }
        if (defenders == null) {
            throw new IllegalArgumentException("defenders не может быть null: нет защитников — пустой список");
        }
        if (enemies == null) {
            throw new IllegalArgumentException("enemies не может быть null: нет врагов — пустой список");
        }
        if (profiles == null) {
            throw new IllegalArgumentException("profiles не может быть null: сила врага берётся из enemies.json");
        }
        if (defenders.stream().anyMatch(defender -> defender == null)) {
            throw new IllegalArgumentException("defenders содержит null: защитник без снимка не получает пост");
        }
        Set<UUID> ids = new HashSet<>();
        for (DefenderCapability defender : defenders) {
            if (!ids.add(defender.id())) {
                throw new IllegalArgumentException(
                        "defender " + defender.id() + " встречается дважды: один дух — один пост");
            }
        }
        if (defenders.isEmpty()) {
            return List.of();
        }
        List<DirectionThreat> threats = ThreatBearing.sum(enemies, center, profiles);
        if (threats.isEmpty()) {
            return new SectorAssigner()
                    .assign(
                            center,
                            radius,
                            defenders.stream().map(DefenderCapability::id).toList());
        }
        List<DefenderCapability> ordered = defenders.stream()
                .sorted(Comparator.comparingDouble(DefenderCapability::frontRank)
                        .reversed()
                        .thenComparing(DefenderCapability::id))
                .toList();
        List<DirectionThreat> byStrength = threats.stream()
                .sorted(Comparator.comparingDouble(DirectionThreat::strength)
                        .reversed()
                        .thenComparingDouble(DirectionThreat::bearing))
                .toList();
        int[] quotas = quotas(byStrength, ordered.size());
        double postDistance = Math.min(radius, DefenseTuning.FRONT_RADIUS);
        List<DefenseAssignment> posts = new ArrayList<>(ordered.size());
        int cursor = 0;
        for (int index = 0; index < byStrength.size(); index++) {
            DirectionThreat threat = byStrength.get(index);
            for (int slot = 0; slot < quotas[index]; slot++) {
                DefenderCapability defender = ordered.get(cursor++);
                Point post = new Point(
                        center.x() + Math.cos(threat.bearing()) * postDistance,
                        center.y(),
                        center.z() + Math.sin(threat.bearing()) * postDistance);
                posts.add(new DefenseAssignment(defender.id(), post, threat.bearing()));
            }
        }
        return List.copyOf(posts);
    }

    private static int[] quotas(List<DirectionThreat> threats, int defenders) {
        int sectors = threats.size();
        int[] quota = new int[sectors];
        if (defenders <= sectors) {
            Arrays.fill(quota, 0, defenders, 1);
            return quota;
        }
        Arrays.fill(quota, 1);
        for (int seat = defenders - sectors; seat > 0; seat--) {
            int best = 0;
            for (int index = 1; index < sectors; index++) {
                double rank = threats.get(index).strength() / (quota[index] + 1);
                double bestRank = threats.get(best).strength() / (quota[best] + 1);
                if (rank > bestRank) {
                    best = index;
                }
            }
            quota[best]++;
        }
        return quota;
    }
}
