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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class IdleReleaseLiveTests {
    private static final Path FIXTURE = Path.of("src/test/resources/live/observe10-idle-release.json");
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final EatNeed FED = new EatNeed(20, 20, 20, 20);

    @Test
    void idle_TwoSpiritsPausedAfterThreeFailures_ReleaseBeforeObservedIdle() throws IOException {
        JsonNode fixture = MAPPER.readTree(Files.readString(FIXTURE, StandardCharsets.UTF_8));
        long[] now = {0};
        Board board = new Board(() -> now[0]);
        UUID s2 = spirit(fixture, 0);
        UUID s3 = spirit(fixture, 1);
        UUID s1 = busy(fixture);
        BoardTicket mine = ticket(fixture.get("tasks").get(0));
        for (int attempt = 1; attempt <= 4; attempt++) {
            submit(board, survive(mine, "observe10:build_mine:" + attempt));
        }

        fail(board, "observe10:build_mine:1", s2, now);
        now[0] = BoardBackoff.pauseTicks(1);
        fail(board, "observe10:build_mine:2", s2, now);
        now[0] += BoardBackoff.pauseTicks(2);
        fail(board, "observe10:build_mine:3", s2, now);

        long idle = fixture.get("idle").get("ticks").asLong();
        assertTrue(
                idle < BoardTuning.caseFailPauseCapTicks(),
                "Наблюдение обязано быть короче старого потолка " + BoardTuning.caseFailPauseCapTicks()
                        + " тиков, иначе прошлая правка сняла бы паузу сама: " + idle);
        long idleStart = now[0];
        assertTrue(
                board.caseDetached(s2, BoardCase.BUILD_MINE),
                "Три провала подряд снимают духа с кейса BUILD_MINE — как у spirit-2 в observe10");
        assertFalse(
                board.availableTo(s2, BoardCase.BUILD_MINE, idleStart),
                "До простоя кейс закрыт духу: иначе тест не воспроизводит паузу");

        Set<UUID> present = new LinkedHashSet<>(List.of(s1, s2, s3));
        Set<UUID> idleSpirits = new LinkedHashSet<>(List.of(s2, s3));
        board.releaseIdle(present, idleSpirits, idleStart);
        now[0] = idleStart + idle;
        int released = board.releaseIdle(present, idleSpirits, now[0]);

        assertTrue(released > 0, "Простой " + idle + " тиков обязан освободить духа: иначе он стоит без задачи");
        assertTrue(
                board.availableTo(s2, BoardCase.BUILD_MINE, now[0]),
                "После простоя дух снова берёт BUILD_MINE, не дожидаясь старого потолка "
                        + BoardTuning.caseFailPauseCapTicks());

        DispatchRound round = new DispatchRound(board, now[0]);
        DispatchRound.Candidate candidate = new DispatchRound.Candidate(
                s2, "spirit-2", null, List.of("minecraft:stone_pickaxe"), FED, SpiritView.free());
        BoardTicket next = board.find("observe10:build_mine:4").orElseThrow();
        assertTrue(
                round.refusal(next, candidate).isEmpty(),
                "Выдача в игре обязана считать духа свободным, а не «на паузе кейса»: "
                        + round.refusal(next, candidate));
        assertTrue(
                board.claimable(stock(fixture), now[0]).stream().anyMatch(t -> t.id().equals(next.id())),
                "Освобождённое задание обязано попасть в очередь выдачи доски, а не остаться вне её");
        assertEquals(
                BoardOutcome.CLAIMED,
                board.claim(next.id(), s2, now[0]),
                "Тот же путь, что в игре: claimable → отказ выдачи → claim обязан выдать задание");
    }

    @Test
    void idle_BlockedTicket_ReleasedBeforeOldTimeout() throws IOException {
        JsonNode fixture = MAPPER.readTree(Files.readString(FIXTURE, StandardCharsets.UTF_8));
        long[] now = {0};
        Board board = new Board(() -> now[0]);
        UUID s2 = spirit(fixture, 0);
        UUID s3 = spirit(fixture, 1);
        UUID s1 = busy(fixture);
        BoardTicket mine = ticket(fixture.get("tasks").get(0));
        List<UUID> order = new ArrayList<>(List.of(s1, s2, s3, s1, s2));
        for (UUID spirit : order) {
            submit(board, mine, spirit, now[0]);
            assertEquals(
                    BoardOutcome.CLAIMED,
                    board.claim(mine.id(), spirit, now[0]),
                    "Перед провалом задание обязано быть выдано духу: фикстура " + mine.id());
            board.fail(mine.id(), stock(fixture), "клетку шахты не выломать на тике " + now[0] + " (observe10)");
            now[0] += BoardTuning.caseFailPauseTicks();
        }
        assertEquals(
                BoardStatus.BLOCKED,
                board.statusOf(mine.id()).orElse(BoardStatus.PENDING),
                "Пять провалов подряд обязаны заблокировать задание, как first:build_mine в observe10");

        long blockedAt = now[0] - BoardTuning.caseFailPauseTicks();
        Set<UUID> present = new LinkedHashSet<>(List.of(s1, s2, s3));
        Set<UUID> idleSpirits = new LinkedHashSet<>(List.of(s2, s3));
        board.releaseIdle(present, idleSpirits, blockedAt);
        now[0] = blockedAt + BoardTuning.idleReleaseTicks();
        int released = board.releaseIdle(present, idleSpirits, now[0]);

        assertTrue(
                released > 0,
                "Простой духов обязан снять BLOCKED раньше старого потолка " + BoardTuning.caseFailPauseCapTicks());
        assertEquals(
                BoardStatus.PENDING,
                board.statusOf(mine.id()).orElse(BoardStatus.PENDING),
                "BLOCKED снимается простоем духа: доска снова пробует задание, а не держит его до потолка");
        assertEquals(
                BoardOutcome.CLAIMED,
                board.claim(mine.id(), s2, now[0]),
                "Разблокированное задание выдаётся тому же духу через claim, как в раунде выдачи");
    }

    private static void fail(Board board, String id, UUID spirit, long[] now) {
        assertEquals(
                BoardOutcome.CLAIMED,
                board.claim(id, spirit, now[0]),
                "Задание " + id + " обязано выдаваться перед провалом");
        board.fail(id, new StockView(Map.of()), "не удалось добыть oak_log (observe10)");
    }

    private static BoardTicket survive(BoardTicket ticket, String id) {
        return new BoardTicket(
                id,
                ticket.boardCase(),
                Optional.empty(),
                ticket.priority(),
                ticket.createdAt(),
                ticket.level(),
                ticket.cause(),
                ticket.dependsOn(),
                ticket.requiredItems());
    }

    private static UUID spirit(JsonNode fixture, int index) {
        return UUID.fromString(fixture.get("idle").get("spirits").get(index).asText());
    }

    private static UUID busy(JsonNode fixture) {
        return UUID.fromString(fixture.get("idle").get("busy").get(0).asText());
    }

    private static StockView stock(JsonNode fixture) {
        Map<String, Integer> items = new LinkedHashMap<>();
        for (JsonNode task : fixture.get("tasks")) {
            task.get("requiredItems")
                    .fields()
                    .forEachRemaining(
                            entry -> items.put(entry.getKey(), entry.getValue().asInt()));
        }
        return new StockView(items);
    }

    private static BoardTicket ticket(JsonNode task) {
        List<String> dependsOn = new ArrayList<>();
        task.get("dependsOn").forEach(node -> dependsOn.add(node.asText()));
        Map<String, Integer> required = new LinkedHashMap<>();
        task.get("requiredItems")
                .fields()
                .forEachRemaining(
                        entry -> required.put(entry.getKey(), entry.getValue().asInt()));
        return new BoardTicket(
                task.get("id").asText(),
                BoardCase.valueOf(task.get("case").asText()),
                Optional.empty(),
                task.get("priority").asInt(),
                task.get("createdAt").asLong(),
                task.get("level").asInt(),
                task.get("cause").asText(),
                dependsOn,
                required);
    }

    private static void submit(Board board, BoardTicket ticket) {
        assertEquals(
                BoardOutcome.ACCEPTED,
                board.submit(ticket),
                "Доска обязана принять живое задание " + ticket.id() + " (createdAt=" + ticket.createdAt() + ")");
    }

    private static void submit(Board board, BoardTicket ticket, UUID spirit, long now) {
        BoardOutcome outcome = board.submit(ticket);
        assertEquals(
                BoardOutcome.ACCEPTED,
                outcome,
                "Доска обязана принять живое задание " + ticket.id() + " духу " + spirit + " на тике " + now
                        + " (status=" + board.statusOf(ticket.id()) + ", failures="
                        + board.ticketFailures(ticket.id()) + ")");
    }
}
