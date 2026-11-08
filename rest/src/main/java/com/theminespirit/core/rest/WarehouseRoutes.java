package com.theminespirit.core.rest;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class WarehouseRoutes {
    public static final String PATH = "/v1/settlement/warehouse";

    private final WarehouseGateway gateway;
    private final IdempotencyCache idempotency;

    public WarehouseRoutes(WarehouseGateway gateway, IdempotencyCache idempotency) {
        this.gateway = Objects.requireNonNull(
                gateway, "gateway не может быть null: решение о складе читает и меняет только платформа");
        this.idempotency = Objects.requireNonNull(
                idempotency, "idempotency не может быть null: повтор request_id обязан вернуть прежнее решение");
    }

    public List<Route> routes() {
        return List.of(
                new Route("GET", PATH, this::read),
                new Route("POST", PATH, this::decide),
                new Route("PUT", PATH, this::decide));
    }

    private RestResponse read(RestRequest ignoredRequest, PathParams ignoredParams) {
        return RestResponse.ok(WarehouseJson.viewJson(gateway.view()));
    }

    private RestResponse decide(RestRequest request, PathParams ignoredParams) {
        WarehouseDecision parsed = WarehouseDecision.parse(request.body());
        String requestId = parsed.requestId().toString();
        String fingerprint = fingerprint(parsed);
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
            response = WarehouseJson.changeJson(gateway.decide(parsed), requestId);
        } catch (RuntimeException failure) {
            idempotency.release(requestId);
            throw failure;
        }
        idempotency.remember(requestId, fingerprint, response);
        return RestResponse.ok(response);
    }

    private static String fingerprint(WarehouseDecision decision) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("level", decision.level());
        body.put(
                "site",
                decision.site().x() + "," + decision.site().y() + ","
                        + decision.site().z());
        body.put("by", decision.by());
        body.put("reason", decision.reason());
        return IdempotencyCache.fingerprintOf(body);
    }
}
