package com.theminespirit.core.rest;

import com.theminespirit.core.ErrorText;
import com.theminespirit.core.policy.UnprotectArea;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * REST-контроллер разрешений на снятие защиты построек игрока: список (GET), выдача (POST) и снятие (DELETE).
 * Выдача идемпотентна по {@code request_id}: повтор возвращает прежнее разрешение, а не создаёт второе.
 */
public final class PolicyRoutes {
    private final PolicyGateway gateway;
    private final IdempotencyCache idempotency;

    public PolicyRoutes(PolicyGateway gateway, IdempotencyCache idempotency) {
        this.gateway = Objects.requireNonNull(
                gateway, "gateway не может быть null: разрешения читает и меняет только платформа");
        this.idempotency = Objects.requireNonNull(
                idempotency, "idempotency не может быть null: повтор request_id обязан вернуть прежнее разрешение");
    }

    public List<Route> routes() {
        return List.of(
                new Route("GET", "/v1/policy/unprotect", this::list),
                new Route("POST", "/v1/policy/unprotect", this::grant),
                new Route("DELETE", "/v1/policy/unprotect/{id}", this::revoke));
    }

    private RestResponse list(RestRequest request, PathParams params) {
        return RestResponse.ok(PolicyJson.listJson(gateway.areas()));
    }

    private RestResponse grant(RestRequest request, PathParams params) {
        UnprotectRequest parsed = UnprotectRequest.parse(request.body());
        String requestId = parsed.requestId().toString();
        String fingerprint = fingerprint(parsed);
        IdempotencyCache.Result verdict = idempotency.check(requestId, fingerprint);
        if (verdict instanceof IdempotencyCache.Result.Replay replay) {
            return RestResponse.json(201, replay.storedResponse());
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
            response = grantedJson(grant(parsed), requestId);
        } catch (IllegalArgumentException refused) {
            idempotency.release(requestId);
            throw new ApiException(ErrorCode.INVALID_REQUEST, ErrorText.of(refused), Map.of("field", "area"), refused);
        } catch (RuntimeException failure) {
            idempotency.release(requestId);
            throw failure;
        }
        idempotency.remember(requestId, fingerprint, response);
        return RestResponse.json(201, response);
    }

    private UnprotectArea grant(UnprotectRequest request) {
        return gateway.grant(request);
    }

    private RestResponse revoke(RestRequest request, PathParams params) {
        String id = params.get("id");
        UnprotectArea removed = gateway.revoke(id);
        if (removed == null) {
            throw new ApiException(
                    ErrorCode.NOT_FOUND,
                    "Разрешение '" + id + "' не найдено: список разрешений — GET /v1/policy/unprotect",
                    Map.of("id", id));
        }
        return RestResponse.ok(PolicyJson.removedJson(removed.id()));
    }

    private static String grantedJson(UnprotectArea area, String requestId) {
        Map<String, Object> body = new LinkedHashMap<>(PolicyJson.toMap(area));
        body.put("request_id", requestId);
        return RestJson.write(body);
    }

    private static String fingerprint(UnprotectRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("dimension", request.dimension());
        body.put(
                "min",
                List.of(request.min().x(), request.min().y(), request.min().z()));
        body.put(
                "max",
                List.of(request.max().x(), request.max().y(), request.max().z()));
        body.put("granted_by", request.grantedBy());
        body.put("reason", request.reason());
        body.put("ttl_seconds", request.ttlSeconds());
        return IdempotencyCache.fingerprintOf(body);
    }
}
