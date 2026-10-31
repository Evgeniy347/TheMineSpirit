package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.theminespirit.core.observation.BlockCoord;
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

class StuckDispatchLiveTests {
    private static final Path DIR = Path.of("src/test/resources/live/observe12");
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final EatNeed FED = new EatNeed(20, 20, 20, 20);

    @Test
    void stuckSpirit_AfterStallFailures_NoNewTicketWhileNearest() throws IOException {
        JsonNode boardJson = read("board.json");
        JsonNode bots = read("bots.json");
        JsonNode first = bot(bots, "spirit-1");
        JsonNode second = bot(bots, "spirit-2");
        UUID stuck = UUID.fromString(first.get("id").asText());
        UUID free = UUID.fromString(second.get("id").asText());
        BlockCoord at = point(first.get("position"));
        long[] clock = {first.get("tick").asLong()};
        Board board = new Board(() -> clock[0]);
        JsonNode fence = task(boardJson, "first:build_fence");
        JsonNode mine = task(boardJson, "first:build_mine");
        BoardTicket fenceTicket = ticket(fence);
        BoardTicket mineTicket = ticket(mine);
        StockView stock = stock(fence, mine);
        assertEquals(BoardOutcome.ACCEPTED, board.submit(fenceTicket), "Живое задание ограды принимается доской");
        assertEquals(BoardOutcome.ACCEPTED, board.submit(mineTicket), "Живое задание шахты принимается доской");

        String stall = "задание стоит " + TaskStall.STALL_TICKS + " тиков: подход не двигается в " + at;
        board.stuck().see(stuck, at);
        board.stuck().see(free, point(second.get("position")));
        assertTrue(
                board.claimable(stock, clock[0]).stream()
                        .anyMatch(ticket -> ticket.id().equals(fenceTicket.id())),
                "Перед провалом ограда обязана быть в очереди выдачи: " + fenceTicket.id());
        assertEquals(
                BoardOutcome.CLAIMED,
                board.claim(fenceTicket.id(), stuck, clock[0]),
                "Живой ticket " + fenceTicket.id() + " выдаётся застрявшему spirit-1, как в observe12");
        board.fail(fenceTicket.id(), stock, stall);

        List<DispatchRound.Candidate> candidates = new ArrayList<>();
        candidates.add(candidate(stuck, "spirit-1"));
        candidates.add(candidate(free, "spirit-2"));
        DispatchRound round = new DispatchRound(board, clock[0]);
        BoardTicket next = board.find(mineTicket.id()).orElseThrow();

        Optional<DispatchRound.Candidate> chosen = round.choose(next, candidates);

        assertTrue(chosen.isPresent(), "Задание шахты обязано достаться свободному духу, а не остаться без выбора");
        assertEquals(
                free,
                chosen.get().id(),
                "spirit-1 не двигается с " + at + " и провалил подход: доска не имеет права отдавать ему "
                        + "новое задание (другой кейс), пока он не освободится; выбран ближайший достижимый дух");
        String why = round.why(next, candidates);
        assertTrue(
                why.contains("spirit-1") && why.contains("не двигается"),
                "Причина ожидания называет застрявшего духа и что он не двигается: " + why);
        board.noteWait(next.id(), why);
        String card = BoardWhy.waitReason(board, next, stock, clock[0]).orElse(null);
        assertTrue(
                card != null && card.contains("spirit-1") && card.contains("не двигается"),
                "Карточка задания на доске несёт причину «дух не двигается», а не молчит: " + card);
        assertEquals(
                BoardOutcome.CLAIMED,
                board.claim(next.id(), chosen.get().id(), clock[0]),
                "Тот же путь, что в игре: claimable → отказ выдачи → claim выдаёт задание достижимому духу");
        board.stuck().see(stuck, new BlockCoord(at.x() + 1, at.y(), at.z()));
        assertTrue(
                board.stuck().isStuck(stuck, clock[0]),
                "Сдвиг на блок внутри области " + StuckSpirits.AREA_RADIUS + " — колебание, а не освобождение: "
                        + "метка обязана держаться (observe13 N23)");
        board.stuck().see(stuck, new BlockCoord(at.x() + StuckSpirits.AREA_RADIUS + 1, at.y(), at.z()));
        assertFalse(
                board.stuck().isStuck(stuck, clock[0]),
                "Дух вышел из области: смена позиции снимает запрет на новые задания");
    }

    private static DispatchRound.Candidate candidate(UUID id, String name) {
        return new DispatchRound.Candidate(id, name, null, List.of("minecraft:stone_pickaxe"), FED, SpiritView.free());
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
        throw new IllegalStateException("В фикстуре observe12 нет духа " + name);
    }

    private static JsonNode task(JsonNode board, String id) {
        for (JsonNode task : board.get("tasks")) {
            if (task.get("id").asText().equals(id)) {
                return task;
            }
        }
        throw new IllegalStateException("В фикстуре observe12 нет задания " + id);
    }

    private static BlockCoord point(JsonNode position) {
        return new BlockCoord(
                position.get(0).asInt(),
                position.get(1).asInt(),
                position.get(2).asInt());
    }

    private static StockView stock(JsonNode... tasks) {
        Map<String, Integer> items = new LinkedHashMap<>();
        for (JsonNode task : tasks) {
            task.get("requiredItems").fields().forEachRemaining(entry -> items.put(entry.getKey(), 128));
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
}
