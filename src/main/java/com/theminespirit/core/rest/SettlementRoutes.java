package com.theminespirit.core.rest;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class SettlementRoutes {
    public static final String PATH = "/v1/settlement/stage";

    private final SettlementGateway gateway;
    private final IdempotencyCache idempotency;

    public SettlementRoutes(SettlementGateway gateway, IdempotencyCache idempotency) {
        this.gateway = Objects.requireNonNull(
                gateway, "gateway не может быть null: этап поселения читает и меняет только платформа");
        this.idempotency = Objects.requireNonNull(
                idempotency, "idempotency не может быть null: повтор request_id обязан вернуть прежний откат");
    }

    public List<Route> routes() {
        return List.of(
                new Route("GET", PATH, this::read),
                new Route("POST", PATH, this::change),
                new Route("PUT", PATH, this::change));
    }

    private RestResponse read(RestRequest ignoredRequest, PathParams ignoredParams) {
        return RestResponse.ok(SettlementJson.viewJson(gateway.view()));
    }

    private RestResponse change(RestRequest request, PathParams ignoredParams) {
        SettlementChange parsed = SettlementChange.parse(request.body());
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
            response = SettlementJson.changeJson(gateway.rollback(parsed), requestId);
        } catch (RuntimeException failure) {
            idempotency.release(requestId);
            throw failure;
        }
        idempotency.remember(requestId, fingerprint, response);
        return RestResponse.ok(response);
    }

    private static String fingerprint(SettlementChange change) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("stage", change.stage());
        body.put("by", change.by());
        body.put("reason", change.reason());
        return IdempotencyCache.fingerprintOf(body);
    }
}
