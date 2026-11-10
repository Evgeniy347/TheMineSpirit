package com.theminespirit.core.intellect.defense;

import com.theminespirit.core.tactics.Point;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class SquadClusters {
    private SquadClusters() {}

    public static List<DefenseSquad> group(List<DefenseMember> members, double radius) {
        if (members == null) {
            throw new IllegalArgumentException("members не может быть null: нет духов — пустой список");
        }
        if (!Double.isFinite(radius) || radius <= 0) {
            throw new IllegalArgumentException("radius = " + radius + ": радиус отряда обязан быть положительным");
        }
        if (members.stream().anyMatch(member -> member == null)) {
            throw new IllegalArgumentException("members содержит null: дух без снимка в отряд не попадает");
        }
        List<DefenseMember> ordered = new ArrayList<>(members);
        ordered.sort(Comparator.comparing(DefenseMember::id));
        boolean[] used = new boolean[ordered.size()];
        List<List<DefenseMember>> clusters = new ArrayList<>();
        for (int start = 0; start < ordered.size(); start++) {
            if (used[start]) {
                continue;
            }
            List<DefenseMember> cluster = new ArrayList<>();
            List<Integer> queue = new ArrayList<>();
            used[start] = true;
            queue.add(start);
            while (!queue.isEmpty()) {
                int index = queue.removeLast();
                DefenseMember member = ordered.get(index);
                cluster.add(member);
                for (int other = 0; other < ordered.size(); other++) {
                    if (used[other]) {
                        continue;
                    }
                    if (member.pos().distanceTo(ordered.get(other).pos()) <= radius) {
                        used[other] = true;
                        queue.add(other);
                    }
                }
            }
            clusters.add(cluster);
        }
        clusters.sort(Comparator.comparingDouble(
                        (List<DefenseMember> cluster) -> center(cluster).x())
                .thenComparingDouble(cluster -> center(cluster).z()));
        List<DefenseSquad> squads = new ArrayList<>(clusters.size());
        Set<UUID> seen = new HashSet<>();
        for (int index = 0; index < clusters.size(); index++) {
            List<DefenseMember> cluster = clusters.get(index);
            for (DefenseMember member : cluster) {
                if (!seen.add(member.id())) {
                    throw new IllegalArgumentException(
                            "дух " + member.id() + " встречается дважды: один дух — один отряд и один приказ");
                }
            }
            squads.add(new DefenseSquad("S" + (index + 1), cluster));
        }
        return List.copyOf(squads);
    }

    private static Point center(List<DefenseMember> members) {
        double x = 0;
        double y = 0;
        double z = 0;
        for (DefenseMember member : members) {
            x += member.pos().x();
            y += member.pos().y();
            z += member.pos().z();
        }
        int size = members.size();
        return new Point(x / size, y / size, z / size);
    }
}
