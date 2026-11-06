package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.StreamSupport;

public final class LiveBoardFixture {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private LiveBoardFixture() {}

    public static Board load(String resource) throws IOException {
        JsonNode root;
        try (InputStream stream = LiveBoardFixture.class.getResourceAsStream(resource)) {
            assertTrue(stream != null, "Фикстура живой доски " + resource + " не найдена: тест без неё слеп");
            root = MAPPER.readTree(stream);
        }
        List<BoardState.Entry> entries = new ArrayList<>();
        Set<String> ledger = new LinkedHashSet<>();
        for (JsonNode task : root.get("tasks")) {
            BoardCase boardCase = BoardCase.valueOf(task.get("case").asText());
            BoardStatus status = BoardStatus.valueOf(task.get("status").asText());
            String id = task.get("id").asText();
            if (id.startsWith("first:") && status == BoardStatus.DONE) {
                ledger.add(BoardLedger.key(boardCase, task.get("level").asInt(1)));
            }
            entries.add(new BoardState.Entry(
                    new BoardTicket(
                            id,
                            boardCase,
                            assignee(task),
                            task.get("priority").asInt(),
                            task.get("createdAt").asLong(),
                            task.get("level").asInt(1),
                            task.get("cause").asText(),
                            strings(task.get("dependsOn")),
                            items(task.get("requiredItems"))),
                    status,
                    null,
                    null,
                    0));
        }
        Board board = new Board(() -> 0);
        board.restore(new BoardState(entries, ledger));
        return board;
    }

    private static Optional<UUID> assignee(JsonNode task) {
        JsonNode value = task.get("assignee");
        return value == null || value.isNull() ? Optional.empty() : Optional.of(UUID.fromString(value.asText()));
    }

    private static List<String> strings(JsonNode node) {
        return node == null
                ? List.of()
                : StreamSupport.stream(node.spliterator(), false)
                        .map(JsonNode::asText)
                        .toList();
    }

    private static Map<String, Integer> items(JsonNode node) {
        Map<String, Integer> items = new LinkedHashMap<>();
        if (node != null) {
            node.fields()
                    .forEachRemaining(
                            entry -> items.put(entry.getKey(), entry.getValue().asInt()));
        }
        return Map.copyOf(items);
    }
}
