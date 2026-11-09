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

class StuckStageLiveTests {
    private static final Path DIR = Path.of("src/test/resources/live/observe13-stuck");
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final EatNeed FED = new EatNeed(20, 20, 20, 20);

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
        assertEquals(BoardOutcome.ACCEPTED, board.submit(house), "Живая хижина принимается доской");
        assertEquals(
                BoardOutcome.ACCEPTED,
                board.submit(mine),
                "Живая шахта принимается доской и ждёт освобождения spirit-3");

        board.stuck().see(spirit, at);
        assertEquals(
                BoardOutcome.CLAIMED,
                board.claim(house.id(), spirit, clock[0]),
                "spirit-3 получает first:build_house, как в observe13");
        board.fail(
                house.id(),
                stock,
                "застрял в (-177.5, 69.0, -517.5): петля переносов: цель -178,69,-518, клетки у неё уже пробовали (3)",
                new GraphBlock.Path(""));

        List<DispatchRound.Candidate> candidates = List.of(candidate(spirit, "spirit-3"));
        Optional<DispatchRound.Candidate> immediate = new DispatchRound(board, clock[0]).choose(mine, candidates);
        assertTrue(
                immediate.isEmpty(),
                "Сразу после провала дух ещё застрял: новое задание ему не выдаётся, иначе он снова упадёт");

        clock[0] += BoardTuning.caseFailPauseTicks();
        board.stuck().see(spirit, at);
        Optional<DispatchRound.Candidate> afterIdle = new DispatchRound(board, clock[0]).choose(mine, candidates);

        assertTrue(
                afterIdle.isEmpty(),
                "Стоящий дух не получает заданий и после паузы: пока он не сдвинулся и не спасён, доска "
                        + "держит запрет (observe15 B1/A11)");
        board.stuck().see(spirit, new BlockCoord(at.x() + 7, at.y(), at.z()), clock[0], true);
        assertTrue(
                new DispatchRound(board, clock[0]).choose(mine, candidates).isPresent(),
                "Дух вышел на поверхность — запрет снимается, и задание снова выдаётся");
        assertEquals(
                BoardOutcome.CLAIMED,
                board.claim(mine.id(), spirit, clock[0]),
                "Тот же путь, что в игре: раздача -> claim выдаёт задание освободившемуся духу");
    }

    @Test
    void blockedStageTicket_PlanRecalculated_KeepsPauseAndReason() throws IOException {
        JsonNode boardJson = read("board.json");
        long[] clock = {bot(read("bots.json"), "spirit-3").get("tick").asLong()};
        Board board = new Board(() -> clock[0]);
        BoardTicket house = ticket(task(boardJson, "first:build_house"));
        StockView stock = stock(boardJson, "first:build_house");
        List<String> reasons = List.of(
                "не хватило материала 'minecraft:jungle_log'",
                "застрял в (-177.5, 69.0, -517.5): петля переносов",
                "не удалось добыть 'minecraft:cobblestone' x8",
                "не хватило материала 'minecraft:stick'",
                "задание стоит 1200 тиков: подход не двигается");
        for (int attempt = 0; attempt < BoardTuning.ticketFailLimit(); attempt++) {
            assertEquals(
                    BoardOutcome.ACCEPTED,
                    board.submit(house),
                    "Живая first:build_house ставится на попытку " + (attempt + 1));
            assertEquals(
                    BoardOutcome.CLAIMED,
                    board.claim(house.id(), UUID.randomUUID(), clock[0]),
                    "Попытка " + (attempt + 1) + " выдана духу");
            GraphBlock.Need need =
                    attempt + 1 == BoardTuning.ticketFailLimit() ? new GraphBlock.Item("netherite_ingot", 1) : null;
            assertTrue(
                    board.fail(house.id(), stock, reasons.get(attempt), need),
                    "Попытка " + (attempt + 1) + " провалена");
            clock[0] += BoardTuning.caseFailPauseCapTicks();
        }
        assertEquals(
                BoardStatus.BLOCKED,
                board.statusOf(house.id()).orElse(null),
                "После " + BoardTuning.ticketFailLimit() + " провалов живая хижина заблокирована, как в observe13");

        BoardTicket recalculated = new BoardTicket(
                house.id(),
                house.boardCase(),
                Optional.empty(),
                house.priority(),
                clock[0],
                house.level(),
                house.cause(),
                house.dependsOn(),
                Map.of("jungle_log", 2));

        assertEquals(
                BoardStatus.BLOCKED,
                board.statusOf(house.id()).orElse(null),
                "Пересчёт плана не снимает блокировку: иначе BLOCKED -> ASSIGNED за 0.34 с (N25)");
        board.submit(recalculated);
        assertEquals(
                BoardStatus.BLOCKED,
                board.statusOf(house.id()).orElse(null),
                "Пересчитанное задание с другой нехваткой остаётся BLOCKED до паузы, а не сбрасывает провалы");
        assertEquals(
                BoardOutcome.BLOCKED,
                board.claim(house.id(), UUID.randomUUID(), clock[0]),
                "Заблокированное задание не выдаётся после пересчёта плана");
        String card = BoardWhy.waitReason(board, recalculated, stock, clock[0]).orElse(null);
        assertTrue(
                card != null && card.contains("заблокировано"),
                "Карточка доски называет причину паузы после серии провалов: " + card);
    }

    @Test
    void rescueTicket_StuckSpirit_IssuedThroughBoard() throws IOException {
        JsonNode bot = bot(read("bots.json"), "spirit-3");
        UUID spirit = UUID.fromString(bot.get("id").asText());
        BlockCoord at = point(bot.get("position"));
        long now = bot.get("tick").asLong();
        Board board = new Board(() -> now);
        BoardTicket rescue = new BoardTicket(
                "rescue:" + spirit,
                BoardCase.RESCUE_SPIRIT,
                Optional.of(spirit),
                BoardRules.priorityOf(BoardCase.RESCUE_SPIRIT),
                now,
                1,
                "rescue:" + spirit,
                List.of(),
                Map.of());
        assertEquals(BoardOutcome.ACCEPTED, board.submit(rescue), "Живое задание спасения принимается доской");
        board.stuck().mark(spirit, now);

        Optional<DispatchRound.Candidate> chosen =
                new DispatchRound(board, now).choose(rescue, List.of(candidate(spirit, "spirit-3")));

        assertTrue(
                chosen.isPresent(),
                "Задание спасения выдаётся самому застрявшему духу: иначе метка «застрял» запрещает "
                        + "единственный способ сдвинуться (observe15 B1)");
        assertEquals(
                BoardOutcome.CLAIMED,
                board.claim(rescue.id(), spirit, now),
                "Claim спасения проходит через путь доски, несмотря на метку застревания");
        assertTrue(board.complete(rescue.id()), "Успешное спасение закрывает задание");
        assertTrue(
                board.stuck().isStuck(spirit, now),
                "Закрытие задания не выдаёт себя за выход: метка держится до наблюдённой поверхности "
                        + "(observe25 B6)");
        board.stuck().see(spirit, new BlockCoord(at.x() + 4, at.y() + 6, at.z()), now + 1, true);
        assertFalse(
                board.stuck().isStuck(spirit, now + 1),
                "Наблюдённый выход снимает метку «застрял»: дух снова получает обычные задания (observe15 B1)");
    }

    @Test
    void stuckSpirit_RescueWorkClaimable_OrdinaryWorkRefused() throws IOException {
        JsonNode boardJson = read("board.json");
        JsonNode bot = bot(read("bots.json"), "spirit-3");
        UUID spirit = UUID.fromString(bot.get("id").asText());
        long now = bot.get("tick").asLong();
        Board board = new Board(() -> now);
        BoardTicket mine = ticket(task(boardJson, "first:build_mine"));
        BoardTicket rescue = new BoardTicket(
                "rescue:" + spirit,
                BoardCase.RESCUE_SPIRIT,
                Optional.of(spirit),
                BoardRules.priorityOf(BoardCase.RESCUE_SPIRIT),
                now,
                1,
                "graph:rescue_spirit",
                List.of(),
                Map.of());
        assertEquals(BoardOutcome.ACCEPTED, board.submit(mine), "Живое обычное задание принимается доской");
        assertEquals(BoardOutcome.ACCEPTED, board.submit(rescue), "Задание выхода принимается доской");
        board.stuck().see(spirit, point(bot.get("position")), now);
        board.stuck().mark(spirit, now, "застрял (-188.5, 67.0, -516) у двора");

        List<DispatchRound.Candidate> candidates = List.of(candidate(spirit, "spirit-3"));
        DispatchRound round = new DispatchRound(board, now);

        assertTrue(
                round.choose(mine, candidates).isEmpty(),
                "Пока дух не двигается, обычное задание ему не выдают: иначе он снова упадёт");
        String why = round.why(mine, candidates);
        assertTrue(
                why.contains("не двигается") && why.contains("rescue"),
                "Причина ожидания честно называет выход, который ставит граф: " + why);
        Optional<DispatchRound.Candidate> exit = round.choose(rescue, candidates);
        assertTrue(exit.isPresent(), "Задание выхода выдаётся самому застрявшему духу, а не ждёт спасения извне");
        assertEquals(BoardOutcome.CLAIMED, board.claim(rescue.id(), spirit, now), "Выход выдаётся через claim доски");
        assertTrue(board.complete(rescue.id()), "Выполненный выход закрывает задание");
        assertTrue(
                board.stuck().isStuck(spirit, now),
                "Метка держится после закрытия задания: выход подтверждает только наблюдение поверхности "
                        + "(observe25 B6)");
        board.stuck().see(spirit, new BlockCoord(0, 0, 0), now + 1, true);
        assertFalse(board.stuck().isStuck(spirit, now + 1), "Наблюдённый выход снимает метку «не двигается» по факту");
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
}
