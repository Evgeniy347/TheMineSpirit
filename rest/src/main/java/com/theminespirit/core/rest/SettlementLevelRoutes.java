package com.theminespirit.core.rest;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class SettlementLevelRoutes {
    public static final String PATH = "/v1/settlement/level";
    public static final String PREVIEW = PATH + "/preview";

    private final SettlementLevelGateway gateway;
    private final Idempotency idempotency;

    public SettlementLevelRoutes(SettlementLevelGateway gateway, Idempotency idempotency) {
        this.gateway = Objects.requireNonNull(
                gateway, "gateway не может быть null: уровень поселения читает и меняет только платформа");
        this.idempotency = Objects.requireNonNull(
                idempotency, "idempotency не может быть null: повтор request_id обязан вернуть прежний уровень");
    }

    public List<Route> routes() {
        return List.of(
                new Route("GET", PATH, this::read),
                new Route("POST", PREVIEW, this::preview),
                new Route("PUT", PATH, this::change));
    }

    private RestResponse read(RestRequest ignoredRequest, PathParams ignoredParams) {
        SettlementLevelView view = gateway.view()
                .orElseThrow(() -> new ApiException(
                        ErrorCode.NOT_FOUND,
                        "уровень поселения ещё не задан: сначала выберите его через PUT " + PATH
                                + " с полями request_id, level, by, reason",
                        Map.of()));
        return RestResponse.ok(SettlementLevelJson.viewJson(view));
    }

    private RestResponse preview(RestRequest ignoredRequest, PathParams ignoredParams) {
        return RestResponse.ok(SettlementLevelJson.previewJson(gateway.preview()));
    }

    private RestResponse change(RestRequest request, PathParams ignoredParams) {
        SettlementLevelChange parsed = SettlementLevelChange.parse(request.body());
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
            response = SettlementLevelJson.changeJson(gateway.set(parsed), requestId);
        } catch (RuntimeException failure) {
            idempotency.release(requestId);
            throw failure;
        }
        idempotency.remember(requestId, fingerprint, response);
        return RestResponse.ok(response);
    }

    private static String fingerprint(SettlementLevelChange change) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("level", change.level());
        body.put("by", change.by());
        body.put("reason", change.reason());
        return IdempotencyCache.fingerprintOf(body);
    }
}
