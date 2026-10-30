package com.theminespirit.core.rest;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class BoardRoutes {
    private final BoardGateway gateway;
    private final IdempotencyCache idempotency;

    public BoardRoutes(BoardGateway gateway, IdempotencyCache idempotency) {
        this.gateway = Objects.requireNonNull(
                gateway, "gateway не может быть null: очередь доски читает и меняет только платформа");
        this.idempotency = Objects.requireNonNull(
                idempotency, "idempotency не может быть null: повтор request_id обязан вернуть прежнее задание");
    }

    public List<Route> routes() {
        return List.of(
                new Route("GET", "/v1/board", this::list),
                new Route("GET", "/v1/board/log", this::log),
                new Route("POST", "/v1/board/tasks", this::create),
                new Route("DELETE", "/v1/board/tasks/{id}", this::cancel));
    }

    private RestResponse list(RestRequest ignoredRequest, PathParams ignoredParams) {
        return RestResponse.ok(BoardJson.listJson(gateway.tasks(), gateway.counts()));
    }

    private RestResponse log(RestRequest request, PathParams ignoredParams) {
        BoardLogQuery query = BoardLogQuery.parse(request.query());
        return RestResponse.ok(BoardJson.logJson(gateway.log(query.limit())));
    }

    private RestResponse create(RestRequest request, PathParams ignoredParams) {
        BoardTaskRequest parsed = BoardTaskRequest.parse(request.body());
        String requestId = parsed.requestId().toString();
        String fingerprint = fingerprint(parsed);
        IdempotencyCache.Result verdict = idempotency.check(requestId, fingerprint);
        if (verdict instanceof IdempotencyCache.Result.Replay replay) {
            return RestResponse.json(202, replay.storedResponse());
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
            BoardTask task = gateway.submit(parsed);
            response = acceptedJson(task, requestId);
        } catch (RuntimeException failure) {
            idempotency.release(requestId);
            throw failure;
        }
        idempotency.remember(requestId, fingerprint, response);
        return RestResponse.json(202, response);
    }

    private RestResponse cancel(RestRequest ignoredRequest, PathParams params) {
        return RestResponse.ok(BoardJson.toJson(gateway.cancel(params.get("id"))));
    }

    private static String acceptedJson(BoardTask task, String requestId) {
        Map<String, Object> body = new LinkedHashMap<>(BoardJson.toMap(task));
        body.put("request_id", requestId);
        return RestJson.write(body);
    }

    private static String fingerprint(BoardTaskRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("case", request.boardCase().name());
        body.put("assignee", request.assignee().map(Object::toString).orElse(null));
        body.put("requiredItems", request.requiredItems());
        body.put("dependsOn", request.dependsOn());
        body.put("level", request.level());
        body.put("cause", request.cause());
        body.put("building", request.building().map(Enum::name).orElse(null));
        body.put("from", request.from().map(Object::toString).orElse(null));
        body.put("to", request.to().map(Object::toString).orElse(null));
        return IdempotencyCache.fingerprintOf(body);
    }
}
