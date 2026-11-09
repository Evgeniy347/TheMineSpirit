package com.theminespirit.core.rest;

import com.theminespirit.core.board.route.QuarryCandidate;
import com.theminespirit.core.board.route.QuarrySite;
import com.theminespirit.core.board.route.QuarryStatus;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class QuarryJson {
    private QuarryJson() {}

    static String statuses(List<QuarryStatus> statuses) {
        Map<String, Object> body = new LinkedHashMap<>();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (QuarryStatus status : statuses) {
            rows.add(statusMap(status));
        }
        body.put("sites", rows);
        return RestJson.write(body);
    }

    static String status(QuarryStatus status) {
        return RestJson.write(statusMap(status));
    }

    static String search(QuarryQuery query, List<QuarryCandidate> candidates) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("resource", query.resource().name().toLowerCase(Locale.ROOT));
        body.put("min", query.min());
        body.put("max", query.max());
        query.base().ifPresent(base -> body.put("base", point(base.x(), base.y(), base.z())));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (QuarryCandidate candidate : candidates) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put(
                    "point",
                    point(
                            candidate.point().x(),
                            candidate.point().y(),
                            candidate.point().z()));
            row.put("radius", candidate.radius());
            row.put("reserve", candidate.reserve());
            row.put("path", candidate.pathLength());
            row.put("value", candidate.valuePermille());
            row.put("biome", candidate.biome());
            rows.add(row);
        }
        body.put("candidates", rows);
        return RestJson.write(body);
    }

    static String removed(String resource) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("resource", resource);
        body.put("state", "unset");
        return RestJson.write(body);
    }

    private static Map<String, Object> statusMap(QuarryStatus status) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("resource", status.resource().name().toLowerCase(Locale.ROOT));
        row.put("state", status.stateName());
        row.put("reserve", status.reserve());
        row.put("dug", status.dug());
        row.put("remaining_percent", status.remainingPercent());
        row.put("low", status.low());
        row.put("path", status.resource().pathBuilding());
        row.put("route", status.resource().routeBuilding());
        row.put("path_built", status.pathBuilt());
        row.put("route_built", status.routeBuilt());
        if (status.site() == null) {
            row.put("point", null);
        } else {
            QuarrySite site = status.site();
            row.put(
                    "point",
                    point(site.point().x(), site.point().y(), site.point().z()));
            row.put("radius", site.radius());
            row.put("biome", site.biome());
            row.put("initial_reserve", site.initialReserve());
        }
        return row;
    }

    private static Map<String, Object> point(int x, int y, int z) {
        Map<String, Object> point = new LinkedHashMap<>();
        point.put("x", x);
        point.put("y", y);
        point.put("z", z);
        return point;
    }
}
