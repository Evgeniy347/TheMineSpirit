package com.theminespirit.core.rest;

import com.theminespirit.core.tactics.adapter.DecisionRecord;
import java.util.List;
import java.util.Map;
import java.util.Objects;

final class DecisionRoutes {
    private final BotGateway gateway;

    DecisionRoutes(BotGateway gateway) {
        this.gateway = Objects.requireNonNull(gateway, "gateway не может быть null: духов знает только он");
    }

    List<Route> routes() {
        return List.of(new Route("GET", "/v1/spirits/{id}/decisions", this::decisions));
    }

    private RestResponse decisions(RestRequest request, PathParams params) {
        String id = params.get("id");
        DecisionsQuery query = DecisionsQuery.parse(request.query());
        List<DecisionRecord> records = gateway.decisions(id, query.limit());
        return RestResponse.ok(DecisionJson.toJson(id, query, records));
    }
}
