package com.theminespirit.core.board.graph;

import com.fasterxml.jackson.databind.JsonNode;
import com.theminespirit.core.inventory.JsonResource;
import com.theminespirit.core.reload.TableSlot;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

public final class VitalNeeds {
    public static final String RESOURCE = "data/theminespirit/board/vital.json";

    public static final int BAND = 9;

    private static final TableSlot<List<Need>> SOURCE = new TableSlot<>(VitalNeeds::load);

    public record Need(
            String id,
            String event,
            boolean gate,
            String reason,
            String blocked,
            double healthShare,
            List<String> allow) {
        public Need {
            requireText(id, "id нужды");
            requireText(event, "событие нужды");
            requireText(reason, "причина нужды");
            requireText(blocked, "диагноз нужды");
            if (!Double.isFinite(healthShare) || healthShare < 0 || healthShare > 1) {
                throw new IllegalArgumentException("Порог здоровья нужды " + id + " = " + healthShare
                        + ": это доля здоровья в [0, 1], иначе ранение не отличить от здоровья");
            }
            allow = List.copyOf(Objects.requireNonNull(
                    allow, "Пути удовлетворения нужды " + id + " не могут быть null: пустой список — нет пути"));
            if (allow.isEmpty()) {
                throw new IllegalArgumentException("У нужды " + id + " нет пути удовлетворения: обычную работу "
                        + "тогда выдают при живой нужде — добавьте allow-префикс билета");
            }
            for (String prefix : allow) {
                requireText(prefix, "путь нужды " + id);
            }
        }

        public boolean allows(String ticketId) {
            Objects.requireNonNull(ticketId, "id билета не может быть null: у пути есть префикс");
            return allow.stream().anyMatch(ticketId::startsWith);
        }

        private static void requireText(String value, String what) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException(what + " пустое: по пустой строке нужду не найти");
            }
        }
    }

    private VitalNeeds() {}

    public static List<Need> needs() {
        return SOURCE.current();
    }

    public static void useSource(Supplier<List<Need>> supplier) {
        SOURCE.use(supplier);
    }

    private static List<Need> load() {
        return parse(JsonResource.parse(JsonResource.readClasspath(RESOURCE), RESOURCE), RESOURCE);
    }

    public static Optional<Need> byId(String id) {
        return needs().stream().filter(need -> need.id().equals(id)).findFirst();
    }

    public static Optional<Need> byEvent(String event) {
        return needs().stream().filter(need -> need.event().equals(event)).findFirst();
    }

    public static int priority(int base, List<Integer> bases, int workMax) {
        if (workMax <= 0) {
            return 0;
        }
        long above = bases.stream().filter(value -> value > base).distinct().count();
        return Math.max(0, workMax - (int) Math.min(BAND, above));
    }

    public static List<Need> parse(String json, String origin) {
        return parse(JsonResource.parse(json, origin), origin);
    }

    public static List<Need> parse(JsonNode root, String origin) {
        Objects.requireNonNull(root, "Таблица нужд не может быть null: без неё жизненный гейт не построить");
        List<Need> needs = new ArrayList<>();
        Map<String, String> seen = new LinkedHashMap<>();
        for (JsonNode node : root.path("needs")) {
            List<String> allow = new ArrayList<>();
            node.path("allow").forEach(prefix -> allow.add(prefix.asText()));
            Need need = new Need(
                    node.path("id").asText(),
                    node.path("event").asText(),
                    node.path("gate").asBoolean(false),
                    node.path("reason").asText(),
                    node.path("blocked").asText(),
                    node.path("healthShare").asDouble(0),
                    allow);
            String before = seen.put(need.id(), origin);
            if (before != null) {
                throw new IllegalStateException("Файл '" + origin + "': нужда " + need.id()
                        + " описана дважды — таблица нужд не терпит двух путей для одного состояния");
            }
            needs.add(need);
        }
        if (needs.isEmpty()) {
            throw new IllegalStateException(
                    "Файл '" + origin + "': нет ни одной жизненной нужды — " + "гейт VitalGate нечего охранять");
        }
        return List.copyOf(needs);
    }
}
