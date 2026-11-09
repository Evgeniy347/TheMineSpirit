package com.theminespirit.core.rest;

import com.theminespirit.core.board.route.RouteResource;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public final class QuarryRoutes {
    public static final String SEARCH = "/v1/quarry/search";
    public static final String SITE = "/v1/quarry/site";
    public static final String STATUS = "/v1/quarry/status";

    private final QuarryGateway gateway;

    public QuarryRoutes(QuarryGateway gateway) {
        this.gateway = Objects.requireNonNull(
                gateway, "Мост мест добычи не может быть null: мир читает и меняет только платформа");
    }

    public List<Route> routes() {
        return List.of(
                new Route("GET", SEARCH, this::search),
                new Route("GET", STATUS, this::statuses),
                new Route("POST", SITE, this::install),
                new Route("PUT", SITE, this::install),
                new Route("DELETE", SITE, this::remove));
    }

    private RestResponse search(RestRequest request, PathParams ignoredParams) {
        QuarryQuery query = QuarryQuery.parse(request.query());
        return RestResponse.ok(QuarryJson.search(query, gateway.search(query)));
    }

    private RestResponse statuses(RestRequest ignoredRequest, PathParams ignoredParams) {
        return RestResponse.ok(QuarryJson.statuses(gateway.statuses()));
    }

    private RestResponse install(RestRequest request, PathParams ignoredParams) {
        QuarryInstall install = QuarryInstall.parse(request.body());
        return RestResponse.ok(QuarryJson.status(gateway.install(install)));
    }

    private RestResponse remove(RestRequest request, PathParams ignoredParams) {
        String raw = request.query().get("resource");
        if (raw == null || raw.isBlank()) {
            throw new ApiException(
                    ErrorCode.INVALID_REQUEST,
                    "Параметр 'resource' обязателен: снимают место у конкретного ресурса (dirt или sand)",
                    Map.of("field", "resource"));
        }
        RouteResource resource;
        try {
            resource = RouteResource.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            throw new ApiException(
                    ErrorCode.INVALID_REQUEST,
                    "Ресурс '" + raw + "' неизвестен: допустимо dirt, sand или snow",
                    Map.of("resource", raw));
        }
        gateway.remove(resource);
        return RestResponse.ok(QuarryJson.removed(resource.name().toLowerCase(Locale.ROOT)));
    }
}
