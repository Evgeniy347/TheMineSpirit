package com.theminespirit.core.rest;

import com.fasterxml.jackson.core.JsonLocation;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.theminespirit.core.board.BoardActivities;
import com.theminespirit.core.board.RelocatePlan;
import com.theminespirit.core.board.api.BoardCase;
import com.theminespirit.core.observation.BlockCoord;
import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record BoardTaskRequest(
        UUID requestId,
        BoardCase boardCase,
        Optional<UUID> assignee,
        Map<String, Integer> requiredItems,
        List<String> dependsOn,
        int level,
        String cause,
        Optional<BoardCase> building,
        Optional<BlockCoord> from,
        Optional<BlockCoord> to) {

    static final String FIELD = "field";
    static final String ALLOWED = "allowed";
    static final String REQUEST_ID = "request_id";
    static final String CASE = "case";
    static final String ASSIGNEE = "assignee";
    static final String REQUIRED_ITEMS = "requiredItems";
    static final String DEPENDS_ON = "dependsOn";
    static final String LEVEL = "level";
    static final String CAUSE = "cause";
    static final String BUILDING = "building";
    static final String FROM = "from";
    static final String TO = "to";
    private static final List<String> FIELDS =
            List.of(REQUEST_ID, CASE, ASSIGNEE, REQUIRED_ITEMS, DEPENDS_ON, LEVEL, CAUSE, BUILDING, FROM, TO);
    private static final JsonMapper MAPPER = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .build();

    public BoardTaskRequest(
            UUID requestId,
            BoardCase boardCase,
            Optional<UUID> assignee,
            Map<String, Integer> requiredItems,
            List<String> dependsOn,
            int level,
            String cause) {
        this(
                requestId,
                boardCase,
                assignee,
                requiredItems,
                dependsOn,
                level,
                cause,
                Optional.empty(),
                Optional.empty(),
                Optional.empty());
    }

    public BoardTaskRequest {
        if (requestId == null) {
            throw new ApiException(
                    ErrorCode.INVALID_REQUEST,
                    "request_id обязателен: идемпотентность постановки держится на UUID клиента",
                    Map.of(FIELD, REQUEST_ID));
        }
        if (boardCase == null) {
            throw new ApiException(
                    ErrorCode.INVALID_REQUEST,
                    "Поле 'case' обязательно: без кейса доска не знает приоритет, гейт и план задания",
                    Map.of(FIELD, CASE, ALLOWED, caseNames()));
        }
        if (level < 1) {
            throw invalid(
                    "Поле 'level' = " + level + " меньше 1: первая постройка ставится "
                            + "уровнем 1, отсутствующий уровень задают единицей",
                    Map.of(FIELD, LEVEL));
        }
        if (cause == null || cause.isBlank()) {
            throw invalid(
                    "Поле 'cause' пусто: по причине пересборка проверяет актуальность, "
                            + "постоянное задание задают причиной always",
                    Map.of(FIELD, CAUSE));
        }
        Objects.requireNonNull(assignee, "assignee не может быть null: без владельца задают Optional.empty()");
        requiredItems = Map.copyOf(requiredItems == null ? Map.of() : requiredItems);
        dependsOn = List.copyOf(dependsOn == null ? List.of() : dependsOn);
        Objects.requireNonNull(building, "building не может быть null: без постройки задают Optional.empty()");
        Objects.requireNonNull(from, "from не может быть null: без точки задают Optional.empty()");
        Objects.requireNonNull(to, "to не может быть null: без точки задают Optional.empty()");
        if (boardCase == BoardCase.TRANSFER_ITEM) {
            throw invalid(
                    "Кейс TRANSFER_ITEM ставит доска по нехватке ингредиентов у крафтера: "
                            + "вручную он не принимается, иначе план не знает ни предмета, ни получателя",
                    Map.of(FIELD, CASE, "case", boardCase.name()));
        }
        if (boardCase == BoardCase.RELOCATE_BUILDING) {
            requireRelocation(building, from, to);
        } else if (building.isPresent() || from.isPresent() || to.isPresent()) {
            throw invalid(
                    "Поля 'building', 'from' и 'to' относятся только к RELOCATE_BUILDING: " + "у кейса " + boardCase
                            + " их быть не должно",
                    Map.of(FIELD, building.isPresent() ? BUILDING : from.isPresent() ? FROM : TO));
        }
    }

    private static void requireRelocation(
            Optional<BoardCase> building, Optional<BlockCoord> from, Optional<BlockCoord> to) {
        if (building.isEmpty() || from.isEmpty() || to.isEmpty()) {
            throw invalid(
                    "Перенос требует полей 'building' (что переносим), 'from' и 'to' (точки): "
                            + "без них неизвестно, какую постройку сносить и где ставить",
                    Map.of(ALLOWED, List.of(BUILDING, FROM, TO)));
        }
        if (from.orElseThrow().equals(to.orElseThrow())) {
            throw invalid(
                    "Перенос из точки " + from.orElseThrow() + " в ту же точку: "
                            + "сносить и строить на одном месте нечего",
                    Map.of(FIELD, TO));
        }
        try {
            RelocatePlan.assertMovable(building.orElseThrow());
        } catch (IllegalArgumentException rejected) {
            throw new ApiException(
                    ErrorCode.INVALID_REQUEST,
                    422,
                    Objects.requireNonNullElse(
                            rejected.getMessage(), "Постройка " + building.orElseThrow() + " не переносится"),
                    Map.of("building", building.orElseThrow().name(), "immovable", RelocatePlan.immovableNames()));
        }
    }

    public static List<String> caseNames() {
        return Arrays.stream(BoardCase.values())
                .filter(boardCase -> !BoardActivities.isActivity(boardCase))
                .map(BoardCase::name)
                .toList();
    }

    public static BoardTaskRequest parse(byte[] body) {
        JsonNode root = read(body);
        if (root == null || root.isNull() || !root.isObject()) {
            throw invalid(
                    "Тело POST board tasks должно быть JSON-объектом вида "
                            + "{\"request_id\":\"<uuid>\",\"case\":\"BUILD_FENCE\",\"assignee\":\"<uuid>\","
                            + "\"requiredItems\":{\"oak_log\":16},\"dependsOn\":[\"…\"],\"level\":1,"
                            + "\"cause\":\"always\"}, получено: "
                            + (root == null ? "пустое тело" : root.getNodeType()),
                    Map.of(ALLOWED, FIELDS));
        }
        rejectUnknownFields(root);
        UUID requestId = BoardTaskFields.requestId(root.get(REQUEST_ID));
        BoardCase boardCase = BoardTaskFields.boardCase(root.get(CASE));
        Optional<UUID> assignee = BoardTaskFields.assignee(root.get(ASSIGNEE));
        Map<String, Integer> requiredItems = BoardTaskFields.items(root.get(REQUIRED_ITEMS));
        List<String> dependsOn = BoardTaskFields.dependsOn(root.get(DEPENDS_ON));
        int level = BoardTaskFields.level(root.get(LEVEL));
        String cause = BoardTaskFields.cause(root.get(CAUSE));
        Optional<BoardCase> building = BoardTaskFields.building(root.get(BUILDING));
        Optional<BlockCoord> from = BoardTaskFields.point(root.get(FROM), FROM);
        Optional<BlockCoord> to = BoardTaskFields.point(root.get(TO), TO);
        return new BoardTaskRequest(
                requestId, boardCase, assignee, requiredItems, dependsOn, level, cause, building, from, to);
    }

    private static JsonNode read(byte[] body) {
        try {
            return MAPPER.readTree(body);
        } catch (JsonProcessingException e) {
            JsonLocation location = e.getLocation();
            Map<String, Object> details = new LinkedHashMap<>();
            String where = "";
            if (location != null && location.getLineNr() > 0) {
                details.put("line", location.getLineNr());
                details.put("column", location.getColumnNr());
                where = " (строка " + location.getLineNr() + ", столбец " + location.getColumnNr() + ")";
            }
            throw new ApiException(
                    ErrorCode.INVALID_REQUEST,
                    "Тело POST board tasks не разобрано как JSON" + where + ": " + e.getOriginalMessage()
                            + ". Проверьте скобки, кавычки и кодировку UTF-8",
                    details,
                    e);
        } catch (IOException e) {
            throw new ApiException(
                    ErrorCode.INVALID_REQUEST,
                    "Тело POST board tasks не прочитано как JSON: " + e.getMessage(),
                    Map.of(),
                    e);
        }
    }

    private static void rejectUnknownFields(JsonNode root) {
        Iterator<String> names = root.fieldNames();
        while (names.hasNext()) {
            String name = names.next();
            if (!FIELDS.contains(name)) {
                throw invalid(
                        "Неизвестное поле '" + name + "' в POST board tasks: допустимы " + FIELDS,
                        Map.of(FIELD, name, ALLOWED, FIELDS));
            }
        }
    }

    static ApiException invalid(String message, Map<String, Object> details) {
        return new ApiException(ErrorCode.INVALID_REQUEST, message, details);
    }
}
