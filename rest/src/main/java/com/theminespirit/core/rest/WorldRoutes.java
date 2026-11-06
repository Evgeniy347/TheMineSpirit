package com.theminespirit.core.rest;

import java.util.List;
import java.util.Objects;

public final class WorldRoutes {
    public static final String PATH = "/v1/world";

    private final WorldGateway gateway;

    public WorldRoutes(WorldGateway gateway) {
        this.gateway = Objects.requireNonNull(gateway, "gateway не может быть null: сводку мира отдаёт платформа");
    }

    public List<Route> routes() {
        return List.of(
                new Route("GET", PATH, (request, params) -> RestResponse.ok(WorldJson.viewJson(gateway.view()))));
    }
}
