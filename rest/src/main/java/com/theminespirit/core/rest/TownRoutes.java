package com.theminespirit.core.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.theminespirit.core.inventory.api.JsonResource;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class TownRoutes {
    public static final String PLAN = "/v1/town/plan";
    public static final String TERRAIN = "/v1/town/terrain.png";
    public static final String PREVIEW = "/v1/town/preview";
    public static final String APPLY = "/v1/town/apply";
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
                new Route("GET", PLAN, (request, params) -> RestResponse.ok(gateway.planJson())),
                new Route("GET", TERRAIN, this::terrain),
                new Route("POST", PREVIEW, this::preview),
                new Route("POST", APPLY, this::apply));
    }

    private RestResponse terrain(RestRequest request, PathParams ignored) {
        int x1 = coordinate(request, "x1");
        int z1 = coordinate(request, "z1");
        int x2 = coordinate(request, "x2");
        int z2 = coordinate(request, "z2");
        int scale = request.query().containsKey("scale") ? coordinate(request, "scale") : DEFAULT_SCALE;
        return RestResponse.png(gateway.terrainPng(x1, z1, x2, z2, scale));
    }

    private RestResponse preview(RestRequest request, PathParams ignored) {
        return RestResponse.png(gateway.previewPng(new String(request.body(), StandardCharsets.UTF_8)));
    }

    private RestResponse apply(RestRequest request, PathParams ignored) {
        String body = new String(request.body(), StandardCharsets.UTF_8);
        String requestId = requestId(body);
        String fingerprint = IdempotencyCache.fingerprintOf(Map.of("body", body));
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
            response = gateway.applyJson(body);
        } catch (RuntimeException failure) {
            idempotency.release(requestId);
            throw failure;
        }
        idempotency.remember(requestId, fingerprint, response);
        return RestResponse.ok(response);
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

    private static String requestId(String body) {
        JsonNode root = JsonResource.parse(body, "town");
        JsonNode id = root.get("request_id");
        if (id == null || !id.isTextual() || id.asText().isBlank()) {
            throw new ApiException(
                    ErrorCode.INVALID_REQUEST,
                    "нет request_id: без него повтор загрузки карты не отличить от новой",
                    Map.of("field", "request_id"));
        }
        return id.asText();
    }
}
