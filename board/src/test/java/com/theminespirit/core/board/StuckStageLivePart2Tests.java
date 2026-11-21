package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.theminespirit.core.board.api.BoardCase;
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

class StuckStageLivePart2Tests {
    private static final Path DIR = Path.of("src/test/resources/live/observe13-stuck");
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final EatNeed FED = new EatNeed(20, 20, 20, 20);

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
        throw new IllegalStateException("В фикстуре observe13 нет духа " + name);
    }

    private static JsonNode task(JsonNode board, String id) {
        for (JsonNode task : board.get("tasks")) {
            if (task.get("id").asText().equals(id)) {
                return task;
            }
        }
        throw new IllegalStateException("В фикстуре observe13 нет задания " + id);
    }

    private static BlockCoord point(JsonNode position) {
        return new BlockCoord(
                position.get(0).asInt(),
                position.get(1).asInt(),
                position.get(2).asInt());
    }

    private static StockView stock(JsonNode board, String... ids) {
        Map<String, Integer> items = new LinkedHashMap<>();
        for (String id : ids) {
            task(board, id).get("requiredItems").fields().forEachRemaining(entry -> items.put(entry.getKey(), 128));
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

    @Test
    void stuckSpirit_IdleWithoutTask_ReturnsToBoardByTimeout() throws IOException {
        JsonNode boardJson = read("board.json");
        JsonNode bots = read("bots.json");
        JsonNode third = bot(bots, "spirit-3");
        UUID spirit = UUID.fromString(third.get("id").asText());
        BlockCoord at = point(third.get("position"));
        long[] clock = {third.get("tick").asLong()};
        Board board = new Board(() -> clock[0]);
        BoardTicket house = ticket(task(boardJson, "first:build_house"));
        BoardTicket mine = ticket(task(boardJson, "first:build_mine"));
        StockView stock = stock(boardJson, "first:build_house", "first:build_mine");
        assertEquals(
                BoardOutcome.ACCEPTED,
                board.submit(house),
                "Живая хижина принимается доской Иначе тест stuckSpirit_IdleWithoutTask_ReturnsToBoardByTimeout перестанет ловить эту поломку. При смене stuckSpirit_IdleWithoutTask_ReturnsToBoardByTimeout проверь это утверждение.");
        assertEquals(
                BoardOutcome.ACCEPTED,
                board.submit(mine),
                "Живая шахта принимается доской и ждёт освобождения spirit-3 Иначе тест stuckSpirit_IdleWithoutTask_ReturnsToBoardByTimeout перестанет ловить эту поломку. При смене stuckSpirit_IdleWithoutTask_ReturnsToBoardByTimeout проверь это утверждение.");
        board.stuck().see(spirit, at);
        assertEquals(
                BoardOutcome.CLAIMED,
                board.claim(house.id(), spirit, clock[0]),
                "spirit-3 получает first:build_house, как в observe13 Иначе тест stuckSpirit_IdleWithoutTask_ReturnsToBoardByTimeout перестанет ловить эту поломку. При смене stuckSpirit_IdleWithoutTask_ReturnsToBoardByTimeout проверь это утверждение.");
        board.fail(
                house.id(),
                stock,
                "застрял в (-177.5, 69.0, -517.5): петля переносов: цель -178,69,-518, клетки у неё уже пробовали (3)",
                new GraphBlock.Path(""));
        List<DispatchRound.Candidate> candidates = List.of(candidate(spirit, "spirit-3"));
        Optional<DispatchRound.Candidate> immediate = new DispatchRound(board, clock[0]).choose(mine, candidates);
        assertTrue(
                immediate.isEmpty(),
                "Сразу после провала дух ещё застрял: новое задание ему не выдаётся, иначе он снова упадёт При смене stuckSpirit_IdleWithoutTask_ReturnsToBoardByTimeout проверь это утверждение.");
        clock[0] += BoardTuning.caseFailPauseTicks();
        board.stuck().see(spirit, at);
        Optional<DispatchRound.Candidate> afterIdle = new DispatchRound(board, clock[0]).choose(mine, candidates);
        assertTrue(
                afterIdle.isEmpty(),
                "Стоящий дух не получает заданий и после паузы: пока он не сдвинулся и не спасён, доска "
                        + "держит запрет (observe15 B1/A11) Иначе тест stuckSpirit_IdleWithoutTask_ReturnsToBoardByTimeout перестанет ловить эту поломку. При смене stuckSpirit_IdleWithoutTask_ReturnsToBoardByTimeout проверь это утверждение.");
        board.stuck().see(spirit, new BlockCoord(at.x() + 7, at.y(), at.z()), clock[0], true);
        assertTrue(
                new DispatchRound(board, clock[0]).choose(mine, candidates).isPresent(),
                "Дух вышел на поверхность — запрет снимается, и задание снова выдаётся Иначе тест stuckSpirit_IdleWithoutTask_ReturnsToBoardByTimeout перестанет ловить эту поломку. При смене stuckSpirit_IdleWithoutTask_ReturnsToBoardByTimeout проверь это утверждение.");
        assertEquals(
                BoardOutcome.CLAIMED,
                board.claim(mine.id(), spirit, clock[0]),
                "Тот же путь, что в игре: раздача -> claim выдаёт задание освободившемуся духу Иначе тест stuckSpirit_IdleWithoutTask_ReturnsToBoardByTimeout перестанет ловить эту поломку. При смене stuckSpirit_IdleWithoutTask_ReturnsToBoardByTimeout проверь это утверждение.");
    }
}
