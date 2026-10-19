package com.theminespirit.core.rest;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.policy.UnprotectArea;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** JSON-представление разрешений /v1/policy/unprotect: имена полей — контракт v1. */
public final class PolicyJson {
    private PolicyJson() {}

    public static String listJson(List<UnprotectArea> areas) {
        List<Object> items = new ArrayList<>(areas.size());
        for (UnprotectArea area : areas) {
            items.add(toMap(area));
        }
        return RestJson.write(Map.of("areas", items));
    }

    public static String toJson(UnprotectArea area) {
        return RestJson.write(toMap(area));
    }

    public static String removedJson(String id) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", id);
        body.put("removed", true);
        return RestJson.write(body);
    }

    public static Map<String, Object> toMap(UnprotectArea area) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", area.id());
        map.put("dimension", area.dimension());
        map.put("min", point(area.box().min()));
        map.put("max", point(area.box().max()));
        map.put("granted_by", area.grantedBy());
        map.put("granted_at", area.grantedAtMillis());
        map.put("reason", area.reason());
        map.put("expires_at", area.expiresAtMillis() == 0 ? null : area.expiresAtMillis());
        return map;
    }

    private static List<Integer> point(BlockCoord coord) {
        return List.of(coord.x(), coord.y(), coord.z());
    }
}
