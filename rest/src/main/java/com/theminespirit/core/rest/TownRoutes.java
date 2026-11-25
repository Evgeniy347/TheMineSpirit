package com.theminespirit.core.rest;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class TownRoutes {
    public static final String PLAN = "/v1/town/plan";
    public static final String TERRAIN = "/v1/town/terrain.png";
    public static final String DRAWIO = "/v1/town/plan.drawio";
    public static final String PREVIEW = "/v1/town/preview";
    public static final String PREVIEW_DRAWIO = "/v1/town/preview.drawio";
    public static final String APPLY = "/v1/town/apply";
    public static final String APPLY_DRAWIO = "/v1/town/apply.drawio";
    public static final String PLOT = "/v1/town/plots/{id}";
    private static final int DEFAULT_SCALE = 2;

    private final TownGateway gateway;
    private final Idempotency idempotency;

    public TownRoutes(TownGateway gateway, Idempotency idempotency) {
        this.gateway = Objects.requireNonNull(gateway, "gateway не может быть null: карту читает платформа");
        this.idempotency = Objects.requireNonNull(
                idempotency, "idempotency не может быть null: повтор request_id вернёт прежний ответ");
    }

    public List<Route> routes() {
        return List.of(
                new Route("GET", PLAN, (request, params) -> xml(gateway.planDrawio())),
                new Route("GET", TERRAIN, this::terrain),
                new Route("GET", DRAWIO, this::drawio),
                new Route("POST", PREVIEW, this::preview),
                new Route("POST", PREVIEW_DRAWIO, this::preview),
                new Route("POST", APPLY, this::apply),
                new Route("POST", APPLY_DRAWIO, this::apply),
                new Route("DELETE", PLOT, this::removePlot));
    }

    private RestResponse terrain(RestRequest request, PathParams ignored) {
        int x1 = coordinate(request, "x1");
        int z1 = coordinate(request, "z1");
        int x2 = coordinate(request, "x2");
        int z2 = coordinate(request, "z2");
        int scale = request.query().containsKey("scale") ? coordinate(request, "scale") : DEFAULT_SCALE;
        return RestResponse.png(gateway.terrainPng(x1, z1, x2, z2, scale));
    }

    private RestResponse drawio(RestRequest request, PathParams ignored) {
        String text = request.query().keySet().containsAll(List.of("x1", "z1", "x2", "z2"))
                ? gateway.planDrawio(
                        coordinate(request, "x1"),
                        coordinate(request, "z1"),
                        coordinate(request, "x2"),
                        coordinate(request, "z2"),
                        request.query().containsKey("scale") ? coordinate(request, "scale") : DEFAULT_SCALE)
                : gateway.planDrawio();
        return new RestResponse(
                200,
                Map.of(
                        "Content-Type", "application/xml; charset=utf-8",
                        "Content-Disposition", "attachment; filename=\"town-plan.drawio\""),
                text);
    }

    private RestResponse preview(RestRequest request, PathParams ignored) {
        return RestResponse.png(gateway.previewDrawio(new String(request.body(), StandardCharsets.UTF_8)));
    }

    private RestResponse apply(RestRequest request, PathParams ignored) {
        String body = new String(request.body(), StandardCharsets.UTF_8);
        String fingerprint = IdempotencyCache.fingerprintOf(Map.of("body", body));
        String requestId = requestId(request, fingerprint);
        IdempotencyCache.Result verdict = idempotency.check(requestId, fingerprint);
        if (verdict instanceof IdempotencyCache.Result.Replay replay) {
            return RestResponse.ok(replay.storedResponse());
        }
        if (verdict instanceof IdempotencyCache.Result.Conflict) {
            throw new ApiException(
                    ErrorCode.CONFLICT,
                    "request_id " + requestId + " уже использован с другим телом запроса: повторите с новым "
                            + "request_id или пришлите исходное тело",
                    Map.of("request_id", requestId));
        }
        String response;
        try {
            response = gateway.applyDrawio(body);
        } catch (RuntimeException failure) {
            idempotency.release(requestId);
            throw failure;
        }
        idempotency.remember(requestId, fingerprint, response);
        return RestResponse.ok(response);
    }

    private RestResponse removePlot(RestRequest request, PathParams params) {
        return RestResponse.ok(gateway.removePlot(params.get("id")));
    }

    private static String requestId(RestRequest request, String fingerprint) {
        String explicit = request.query().get("request_id");
        return explicit == null || explicit.isBlank() ? fingerprint : explicit.trim();
    }

    private static RestResponse xml(String text) {
        return new RestResponse(200, Map.of("Content-Type", "application/xml; charset=utf-8"), text);
    }

    private static int coordinate(RestRequest request, String name) {
        String raw = request.query().get(name);
        if (raw == null || raw.isBlank()) {
            throw new ApiException(
                    ErrorCode.INVALID_REQUEST,
                    "нет координаты " + name + ": карту местности заказывают рамкой x1,z1,x2,z2",
                    Map.of("field", name));
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException error) {
            throw new ApiException(
                    ErrorCode.INVALID_REQUEST,
                    "координата " + name + " не число: '" + raw + "'",
                    Map.of("field", name));
        }
    }
}
