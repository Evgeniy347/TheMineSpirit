package com.theminespirit.core.rest;

import java.util.List;
import java.util.Map;

final class BotRoutes {
    private final BotGateway gateway;

    BotRoutes(BotGateway gateway) {
        this.gateway = gateway;
    }

    List<Route> routes() {
        return List.of(
                new Route("GET", "/v1/bots", this::list),
                new Route("GET", "/v1/bots/{id}", this::get),
                new Route("POST", "/v1/bots/{id}/observe", this::observe));
    }

    private RestResponse list(RestRequest request, PathParams params) {
        return RestResponse.ok(BotJson.listToJson(gateway.bots()));
    }

    private RestResponse get(RestRequest request, PathParams params) {
        String id = params.get("id");
        BotInfo bot = gateway.bot(id).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND,
                "Дух '" + id + "' не найден: список живых духов — GET /v1/bots", Map.of("id", id)));
        return RestResponse.ok(BotJson.toJson(bot));
    }

    private RestResponse observe(RestRequest request, PathParams params) {
        ObserveRequest observeRequest = ObserveRequest.parse(request.body());
        return RestResponse.ok(gateway.observe(params.get("id"), observeRequest).toJson());
    }
}
