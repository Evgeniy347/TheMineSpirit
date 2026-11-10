package com.theminespirit.core.intellect.defense;

import com.theminespirit.core.tactics.Point;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class SquadPlacement {
    private SquadPlacement() {}

    static Map<String, Point> posts(
            List<DefenseSquad> squads,
            List<ThreatCluster> clusters,
            Map<String, Integer> helperOf,
            List<List<DefenseSquad>> locals,
            Point center) {
        Map<String, Point> posts = new LinkedHashMap<>();
        for (DefenseSquad squad : squads) {
            if (!helperOf.containsKey(squad.id())) {
                posts.put(squad.id(), squad.center());
                continue;
            }
            int index = helperOf.get(squad.id());
            ThreatCluster cluster = clusters.get(index);
            Point anchor = locals.get(index).stream()
                    .map(DefenseSquad::center)
                    .findFirst()
                    .orElseGet(() -> before(center, cluster.centroid()));
            posts.put(squad.id(), anchor);
        }
        return posts;
    }

    private static Point before(Point center, Point target) {
        double distance = center.distanceTo(target);
        double step = Math.max(DefenseTuning.FRONT_RADIUS, distance - DefenseTuning.FRONT_RADIUS);
        return toward(center, target, step);
    }

    static Point toward(Point from, Point to, double distance) {
        double dx = to.x() - from.x();
        double dz = to.z() - from.z();
        double length = Math.hypot(dx, dz);
        if (length < 1e-9) {
            return from;
        }
        double step = Math.min(distance, length);
        return new Point(from.x() + dx / length * step, from.y(), from.z() + dz / length * step);
    }

    static double bearing(Point center, Point point) {
        double angle = Math.atan2(point.z() - center.z(), point.x() - center.x());
        return angle < 0 ? angle + 2 * Math.PI : angle;
    }
}
