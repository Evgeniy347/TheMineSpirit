package com.theminespirit.core.rest;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class HealthRoutes {
    public static final String PATH = "/v1/health";

    private HealthRoutes() {}

    public static List<Route> of(HealthGateway gateway) {
        return List.of(new Route(
                "GET", PATH, (request, params) -> RestResponse.ok(json(gateway.snapshot(System.currentTimeMillis())))));
    }

    static String json(HealthSnapshot snapshot) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("verdict", snapshot.verdict().json());
        body.put("age_ms", snapshot.ageMillis());
        body.put("tick", snapshot.tick());
        body.put("last_tick_ms", snapshot.lastTickMillis());
        body.put("threshold_ms", snapshot.thresholdMillis());
        body.put("debug_paused", snapshot.debugPaused());
        return RestJson.write(body);
    }
}
