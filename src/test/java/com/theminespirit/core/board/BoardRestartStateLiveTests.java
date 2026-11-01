package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BoardRestartStateLiveTests {
    private static final Path DIR = Path.of("src/test/resources/live/boardstate");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void restore_StuckMarkAndCasePause_SurviveServerRestart() throws IOException {
        JsonNode boardJson = read("board.json");
        JsonNode bots = read("bots.json");
        JsonNode second = bot(bots, "spirit-2");
        UUID spirit = UUID.fromString(second.get("id").asText());
        long[] clock = {second.get("tick").asLong()};
        Board board = new Board(() -> clock[0]);
        BoardTicket ticket = ticket(task(boardJson, "graph:strip_oak_log"));
        assertEquals(BoardOutcome.ACCEPTED, board.submit(ticket), "Живое задание обтёсывания принимается доской");
        assertEquals(
                BoardOutcome.CLAIMED,
                board.claim(ticket.id(), spirit, clock[0]),
                "Живой ticket выдаётся своему духу spirit-2");
        board.fail(ticket.id(), new StockView(Map.of()), "застрял в (-169, 70, -505): петля переносов");

        Board restored = new Board(() -> clock[0]);
        restored.restore(board.snapshot());

        assertTrue(
                restored.stuck().isStuck(spirit, clock[0]),
                "Метка «застрял» обязана пережить перезапуск сервера: иначе дух снова получает задания "
                        + "и падает (observe14 A7/N22)");
        assertFalse(
                restored.availableTo(spirit, BoardCase.GATHER_MATERIALS, clock[0]),
                "Пауза кейса после провала обязана пережить перезапуск: иначе сразу после старта задание снова "
                        + "выдаётся тому же духу (observe14 A7/N25)");
    }

    @Test
    void restore_BlockedTicket_KeepsPauseAndTimeout() throws IOException {
        JsonNode boardJson = read("board.json");
        JsonNode bot = bot(read("bots.json"), "spirit-2");
        long[] clock = {bot.get("tick").asLong()};
        Board board = new Board(() -> clock[0]);
        BoardTicket ticket = ticket(task(boardJson, "repair:level:23333410"));
        StockView stock = new StockView(Map.of());
        List<String> reasons = List.of(
                "не удалось добрать oak_log",
                "застрял в (-169, 70, -505): перенос не найден",
                "нет прогресса 3600 тиков (acquire, -)",
                "не хватило материала 'minecraft:cobblestone'",
                "задание стоит 1200 тиков: подход не двигается");
        for (int attempt = 0; attempt < BoardTuning.ticketFailLimit(); attempt++) {
            assertEquals(
                    BoardOutcome.ACCEPTED,
                    board.submit(ticket),
                    "Живой repair:level:23333410 ставится на попытку " + (attempt + 1));
            assertEquals(
                    BoardOutcome.CLAIMED,
                    board.claim(ticket.id(), UUID.randomUUID(), clock[0]),
                    "Попытка " + (attempt + 1) + " выдана духу");
            assertTrue(board.fail(ticket.id(), stock, reasons.get(attempt)), "Попытка " + (attempt + 1) + " провалена");
            clock[0] += BoardTuning.caseFailPauseCapTicks();
            board.prune(clock[0]);
        }
        assertEquals(
                BoardStatus.BLOCKED,
                board.statusOf(ticket.id()).orElse(null),
                "После " + BoardTuning.ticketFailLimit() + " провалов живое задание заблокировано");

        long blockedAt = clock[0];
        Board restored = new Board(() -> clock[0]);
        restored.restore(board.snapshot());
        assertEquals(
                BoardStatus.BLOCKED,
                restored.statusOf(ticket.id()).orElse(null),
                "BLOCKED обязан пережить перезапуск, а не стать снова PENDING/ASSIGNED (observe14 A7/N25)");
        assertEquals(
                BoardOutcome.BLOCKED,
                restored.claim(ticket.id(), UUID.randomUUID(), clock[0]),
                "До конца паузы заблокированное задание не выдаётся даже после загрузки состояния");
        String card = BoardWhy.waitReason(restored, ticket, stock, clock[0]).orElse(null);
        assertTrue(
                card != null && card.contains("заблокировано"),
                "Карточка после загрузки называет причину блокировки: " + card);

        clock[0] = blockedAt + BoardTuning.caseFailPauseCapTicks();
        restored.claimable(stock, clock[0]);
        assertEquals(
                BoardStatus.PENDING,
                restored.statusOf(ticket.id()).orElse(null),
                "Пауза блокировки продолжает считаться от тика блокировки после перезапуска, а не вечно");
    }

    @Test
    void waitReason_StaleBusyNote_NotShownAfterPause() throws IOException {
        JsonNode boardJson = read("board.json");
        JsonNode bot = bot(read("bots.json"), "spirit-2");
        long[] clock = {bot.get("tick").asLong()};
        Board board = new Board(() -> clock[0]);
        BoardTicket ticket = ticket(task(boardJson, "graph:strip_oak_log"));
        assertEquals(BoardOutcome.ACCEPTED, board.submit(ticket), "Живое задание принимается доской");
        board.noteWait(ticket.id(), "spirit-2 — занят: acquire/goto");

        clock[0] += BoardTuning.boardIntervalTicks();
        String card = BoardWhy.waitReason(board, ticket, new StockView(Map.of()), clock[0])
                .orElse(null);

        assertTrue(
                card == null || !card.contains("занят"),
                "Устаревшая причина «занят» не показывается: дух уже idle, а доска держит старый script/activity "
                        + "(observe14 A11); карточка: " + card);
    }

    @Test
    void blockedTicket_SameStockAtAssignment_DoesNotUnblockImmediately() throws IOException {
        JsonNode boardJson = read("board.json");
        JsonNode bot = bot(read("bots.json"), "spirit-3");
        long[] clock = {bot.get("tick").asLong()};
        Board board = new Board(() -> clock[0]);
        JsonNode task = task(boardJson, "first:build_orchard");
        BoardTicket ticket = ticket(task);
        StockView stock = new StockView(Map.of("stick", 128, "charcoal", 128, "coal", 128));
        List<String> reasons = List.of(
                "нет прогресса 3600 тиков (chop_tree, 2/chop_tree/0)",
                "застрял в (-172, 77, -516): перенос не найден",
                "не хватило материала 'minecraft:stick'",
                "не удалось добрать coal",
                "задание стоит 1200 тиков: подход не двигается");
        for (int attempt = 0; attempt < BoardTuning.ticketFailLimit(); attempt++) {
            if (attempt == 0) {
                assertEquals(
                        BoardOutcome.ACCEPTED, board.submit(ticket), "Живой сад ставится на попытку " + (attempt + 1));
            }
            assertEquals(
                    BoardOutcome.CLAIMED,
                    board.claim(ticket.id(), UUID.randomUUID(), clock[0]),
                    "Попытка " + (attempt + 1) + " выдана духу");
            assertTrue(board.fail(ticket.id(), stock, reasons.get(attempt)), "Попытка " + (attempt + 1) + " провалена");
            if (attempt + 1 < BoardTuning.ticketFailLimit()) {
                clock[0] += BoardTuning.caseFailPauseCapTicks();
                assertEquals(
                        BoardOutcome.ACCEPTED,
                        board.submit(ticket),
                        "Этапное задание переоткрывается перед следующей попыткой");
            }
        }
        assertEquals(
                BoardStatus.BLOCKED,
                board.statusOf(ticket.id()).orElse(null),
                "После " + BoardTuning.ticketFailLimit() + " провалов сад заблокирован");

        board.claimable(stock, clock[0]);

        assertEquals(
                BoardStatus.BLOCKED,
                board.statusOf(ticket.id()).orElse(null),
                "Тот же запас, что при блокировке, не снимает BLOCKED: раньше «изменилось: failures=0» "
                        + "возникало от сравнения запаса с потребностью (observe15 B3)");
    }

    private static JsonNode read(String name) throws IOException {
        return MAPPER.readTree(Files.readString(DIR.resolve(name), StandardCharsets.UTF_8));
    }

    private static JsonNode bot(JsonNode bots, String name) {
        for (JsonNode bot : bots.get("bots")) {
            if (bot.get("name").asText().equals(name)) {
                return bot;
            }
        }
        throw new IllegalStateException("В фикстуре boardstate нет духа " + name);
    }

    private static JsonNode task(JsonNode board, String id) {
        for (JsonNode task : board.get("tasks")) {
            if (task.get("id").asText().equals(id)) {
                return task;
            }
        }
        throw new IllegalStateException("В фикстуре boardstate нет задания " + id);
    }

    private static BoardTicket ticket(JsonNode task) {
        List<String> dependsOn = new ArrayList<>();
        task.get("dependsOn").forEach(node -> dependsOn.add(node.asText()));
        Map<String, Integer> required = new LinkedHashMap<>();
        task.get("requiredItems")
                .fields()
                .forEachRemaining(
                        entry -> required.put(entry.getKey(), entry.getValue().asInt()));
        JsonNode assignee = task.get("assignee");
        Optional<UUID> holder = assignee == null || assignee.isNull()
                ? Optional.empty()
                : Optional.of(UUID.fromString(assignee.asText()));
        return new BoardTicket(
                task.get("id").asText(),
                BoardCase.valueOf(task.get("case").asText()),
                holder,
                task.get("priority").asInt(),
                task.get("createdAt").asLong(),
                task.get("level").asInt(),
                task.get("cause").asText(),
                dependsOn,
                required);
    }
}
