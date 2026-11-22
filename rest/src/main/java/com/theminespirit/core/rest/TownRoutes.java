package com.theminespirit.core.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.theminespirit.core.inventory.api.JsonResource;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class TownRoutes {
    public static final String PATH = "/v1/town";
    public static final String EDITS = "/v1/town/edits";
    public static final String VALIDATE = "/v1/town/validate";

    private final TownGateway gateway;
    private final Idempotency idempotency;

    public TownRoutes(TownGateway gateway, Idempotency idempotency) {
        this.gateway = Objects.requireNonNull(gateway, "gateway не может быть null: карту читает платформа");
        this.idempotency = Objects.requireNonNull(
                idempotency, "idempotency не может быть null: повтор request_id вернёт прежний ответ");
    }

    public List<Route> routes() {
        return List.of(
                new Route("GET", PATH, (request, params) -> RestResponse.ok(gateway.viewJson())),
                new Route("POST", EDITS, this::edit),
                new Route("POST", VALIDATE, this::validate));
    }

    private RestResponse edit(RestRequest request, PathParams ignored) {
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
            response = gateway.editJson(body, false);
        } catch (RuntimeException failure) {
            idempotency.release(requestId);
            throw failure;
        }
        idempotency.remember(requestId, fingerprint, response);
        return RestResponse.ok(response);
    }

    private RestResponse validate(RestRequest request, PathParams ignored) {
        return RestResponse.ok(gateway.editJson(new String(request.body(), StandardCharsets.UTF_8), true));
    }

    private static String requestId(String body) {
        JsonNode root = JsonResource.parse(body, "town");
        JsonNode id = root.get("request_id");
        if (id == null || !id.isTextual() || id.asText().isBlank()) {
            throw new ApiException(
                    ErrorCode.INVALID_REQUEST,
                    "нет request_id: без него повтор правки не отличить от новой",
                    Map.of("field", "request_id"));
        }
        return id.asText();
    }
}
