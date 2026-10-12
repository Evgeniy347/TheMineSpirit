package com.theminespirit.core.rest;

import com.fasterxml.jackson.core.JsonLocation;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.theminespirit.core.observation.WorldObservation;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.core.task.TaskJson;
import com.theminespirit.core.task.TaskRecord;
import com.theminespirit.core.task.TaskRegistry;
import com.theminespirit.core.task.TaskState;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Маршруты задач: постановка, чтение, отмена, поток событий и сухой прогон плана.
 */
public final class TaskRoutes {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final TaskRegistry registry;
    private final TaskGateway gateway;
    private final PlanningService planning;
    private final IdempotencyCache idempotency;
    private final SseSlots slots;

    public TaskRoutes(TaskRegistry registry, TaskGateway gateway, PlanningService planning, IdempotencyCache idempotency,
            SseSlots slots) {
        this.registry = Objects.requireNonNull(registry, "registry не может быть null: задачи живут в реестре");
        this.gateway = Objects.requireNonNull(gateway, "gateway не может быть null: мир двигает только платформа");
        this.planning = Objects.requireNonNull(planning, "planning не может быть null: цель превращает планировщик");
        this.idempotency = Objects.requireNonNull(idempotency, "idempotency не может быть null: повтор снимает память");
        this.slots = Objects.requireNonNull(slots, "slots не может быть null: лимит потоков считает счётчик");
    }

    public List<Route> routes() {
        return List.of(
                new Route("POST", "/v1/bots/{id}/tasks", this::create),
                new Route("GET", "/v1/tasks/{id}", this::get),
                new Route("DELETE", "/v1/tasks/{id}", this::delete),
                new Route("GET", "/v1/tasks/{id}/events", this::events),
                new Route("POST", "/v1/plan", this::plan));
    }

    private RestResponse create(RestRequest request, PathParams params) {
        String bot = params.get("id");
        TaskRequest parsed = TaskRequest.parse(request.body());
        String fingerprint = fingerprint(bot, parsed);
        IdempotencyCache.Result verdict = idempotency.check(parsed.requestId().toString(), fingerprint);
        if (verdict instanceof IdempotencyCache.Result.Replay replay) {
            return RestResponse.json(202, replay.storedResponse());
        }
        if (verdict instanceof IdempotencyCache.Result.Conflict) {
            throw new ApiException(ErrorCode.CONFLICT,
                    "request_id " + parsed.requestId() + " уже использован с другим телом запроса: повторите с новым "
                            + "request_id или пришлите исходное тело",
                    Map.of("request_id", parsed.requestId().toString()));
        }
        TaskNode plan = planFor(bot, parsed);
        String taskId;
        try {
            taskId = registry.reserve(bot, parsed.goal());
        } catch (TaskRegistry.BusyException busy) {
            throw new ApiException(ErrorCode.CONFLICT, busy.getMessage(),
                    Map.of("bot", busy.bot(), "task_id", busy.activeTaskId()));
        }
        try {
            gateway.start(bot, plan);
        } catch (RuntimeException failure) {
            registry.discard(taskId);
            throw failure;
        }
        registry.markStarted(taskId);
        String response = acceptedJson(taskId, bot, parsed.requestId().toString());
        idempotency.remember(parsed.requestId().toString(), fingerprint, response);
        return RestResponse.json(202, response);
    }

    private TaskNode planFor(String bot, TaskRequest parsed) {
        if (parsed.plan() != null) {
            return planning.validatePlan(parsed.plan());
        }
        WorldObservation observation = gateway.observation(bot);
        return planning.planGoal(parsed, observation);
    }

    private RestResponse get(RestRequest request, PathParams params) {
        return RestResponse.ok(TaskJson.toJson(knownTask(params.get("id"))));
    }

    private RestResponse delete(RestRequest request, PathParams params) {
        String taskId = params.get("id");
        TaskRecord record = knownTask(taskId);
        if (record.active()) {
            gateway.cancel(record.bot(), taskId);
            registry.cancelIfActive(taskId);
        }
        return RestResponse.ok(TaskJson.toJson(knownTask(taskId)));
    }

    private RestResponse events(RestRequest request, PathParams params) {
        String taskId = params.get("id");
        TaskRecord record = knownTask(taskId);
        long lastEventId = lastEventId(request);
        if (!slots.tryAcquire()) {
            throw new ApiException(ErrorCode.RATE_LIMITED,
                    "Одновременно открыто уже " + SseSlots.MAX_STREAMS + " потоков событий: закройте один из них "
                            + "и подключитесь снова",
                    Map.of("limit", SseSlots.MAX_STREAMS));
        }
        boolean handedOff = false;
        try {
            SseStream stream = new TaskEventStream(record, registry.log(taskId), lastEventId,
                    TaskEventStream.KEEPALIVE, slots::release);
            handedOff = true;
            return RestResponse.stream(stream);
        } finally {
            if (!handedOff) {
                slots.release();
            }
        }
    }

    private RestResponse plan(RestRequest request, PathParams params) {
        JsonNode root;
        try {
            root = MAPPER.readTree(request.body());
        } catch (JsonProcessingException e) {
            JsonLocation location = e.getLocation();
            String where = "";
            if (location != null && location.getLineNr() > 0) {
                where = " (строка " + location.getLineNr() + ", столбец " + location.getColumnNr() + ")";
            }
            throw new ApiException(ErrorCode.INVALID_REQUEST,
                    "Тело POST plan не разобрано как JSON" + where + ": " + e.getOriginalMessage()
                            + ". Проверьте скобки, кавычки и кодировку UTF-8",
                    Map.of());
        } catch (IOException e) {
            throw new ApiException(ErrorCode.INVALID_REQUEST,
                    "Тело POST plan не прочитано: " + e.getMessage(), Map.of());
        }
        if (root == null || !root.isObject()) {
            throw new ApiException(ErrorCode.INVALID_REQUEST,
                    "Тело POST plan должно быть JSON-объектом вида {\"plan\":{…}}, получено: "
                            + (root == null ? "пустое тело" : root.getNodeType()), Map.of());
        }
        JsonNode planNode = root.get("plan");
        if (planNode == null || planNode.isNull()) {
            throw new ApiException(ErrorCode.INVALID_REQUEST,
                    "В теле POST plan нет поля 'plan' с деревом плана, доступны: plan", Map.of("field", "plan"));
        }
        if (!planNode.isObject()) {
            throw new ApiException(ErrorCode.INVALID_REQUEST,
                    "'plan' должно быть JSON-объектом с узлом плана, получено: " + planNode.getNodeType(),
                    Map.of("field", "plan"));
        }
        planning.validatePlan(planNode);
        return RestResponse.ok("{\"valid\":true}");
    }

    private TaskRecord knownTask(String taskId) {
        return registry.get(taskId).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND,
                "Задача '" + taskId + "' не найдена: реестр живёт в памяти сервера и очищается при перезапуске",
                Map.of("task_id", taskId)));
    }

    private static long lastEventId(RestRequest request) {
        String header = null;
        for (Map.Entry<String, String> entry : request.headers().entrySet()) {
            if (entry.getKey().equalsIgnoreCase("last-event-id")) {
                header = entry.getValue();
                break;
            }
        }
        if (header == null || header.isBlank()) {
            return 0;
        }
        try {
            return Long.parseLong(header.trim());
        } catch (NumberFormatException e) {
            throw new ApiException(ErrorCode.INVALID_REQUEST,
                    "Заголовок Last-Event-ID должен быть числом, получено: '" + header + "'",
                    Map.of("header", "Last-Event-ID"));
        }
    }

    private static String fingerprint(String bot, TaskRequest parsed) {
        String planJson = parsed.plan() == null ? null : parsed.plan().toString();
        return IdempotencyCache.fingerprint(bot, parsed.goal(), planJson, parsed.params());
    }

    private static String acceptedJson(String taskId, String bot, String requestId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("task_id", taskId);
        body.put("bot", bot);
        body.put("state", TaskState.ACCEPTED.wire());
        body.put("request_id", requestId);
        try {
            return MAPPER.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Ответ 202 Accepted не сериализуется в JSON: проверьте типы полей", e);
        }
    }
}
