package com.theminespirit.core.rest;

import java.util.List;
import java.util.Objects;

public final class AuditGroupRoutes {
    public static final String PATH = "/v1/audit/groups";

    private final AuditGroupsGateway gateway;

    public AuditGroupRoutes(AuditGroupsGateway gateway) {
        this.gateway = Objects.requireNonNull(gateway, "gateway не может быть null: группы аудита отдаёт платформа");
    }

    public List<Route> routes() {
        return List.of(new Route(
                "GET", PATH, (request, params) -> RestResponse.ok(AuditGroupsJson.viewJson(gateway.groups()))));
    }
}
