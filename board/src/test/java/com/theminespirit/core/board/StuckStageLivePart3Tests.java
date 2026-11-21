package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

class StuckStageLivePart3Tests {
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
                    "Живая first:build_house ставится на попытку  Иначе тест blockedStageTicket_PlanRecalculated_KeepsPauseAndReason перестанет ловить эту поломку. При смене blockedStageTicket_PlanRecalculated_KeepsPauseAndReason проверь это утверждение."
                            + (attempt + 1));
            assertEquals(
                    BoardOutcome.CLAIMED,
                    board.claim(house.id(), UUID.randomUUID(), clock[0]),
                    "Попытка " + (attempt + 1)
                            + " выдана духу Иначе тест blockedStageTicket_PlanRecalculated_KeepsPauseAndReason перестанет ловить эту поломку. При смене blockedStageTicket_PlanRecalculated_KeepsPauseAndReason проверь это утверждение.");
            GraphBlock.Need need =
                    attempt + 1 == BoardTuning.ticketFailLimit() ? new GraphBlock.Item("netherite_ingot", 1) : null;
            assertTrue(
                    board.fail(house.id(), stock, reasons.get(attempt), need),
                    "Попытка " + (attempt + 1)
                            + " провалена Иначе тест blockedStageTicket_PlanRecalculated_KeepsPauseAndReason перестанет ловить эту поломку. При смене blockedStageTicket_PlanRecalculated_KeepsPauseAndReason проверь это утверждение.");
            clock[0] += BoardTuning.caseFailPauseCapTicks();
        }
        assertEquals(
                BoardStatus.BLOCKED,
                board.statusOf(house.id()).orElse(null),
                "После " + BoardTuning.ticketFailLimit()
                        + " провалов живая хижина заблокирована, как в observe13 Иначе тест blockedStageTicket_PlanRecalculated_KeepsPauseAndReason перестанет ловить эту поломку. При смене blockedStageTicket_PlanRecalculated_KeepsPauseAndReason проверь это утверждение.");
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
                "Пересчёт плана не снимает блокировку: иначе BLOCKED -> ASSIGNED за 0.34 с (N25) При смене blockedStageTicket_PlanRecalculated_KeepsPauseAndReason проверь это утверждение.");
        board.submit(recalculated);
        assertEquals(
                BoardStatus.BLOCKED,
                board.statusOf(house.id()).orElse(null),
                "Пересчитанное задание с другой нехваткой остаётся BLOCKED до паузы, а не сбрасывает провалы Иначе тест blockedStageTicket_PlanRecalculated_KeepsPauseAndReason перестанет ловить эту поломку. При смене blockedStageTicket_PlanRecalculated_KeepsPauseAndReason проверь это утверждение.");
        assertEquals(
                BoardOutcome.BLOCKED,
                board.claim(house.id(), UUID.randomUUID(), clock[0]),
                "Заблокированное задание не выдаётся после пересчёта плана Иначе тест blockedStageTicket_PlanRecalculated_KeepsPauseAndReason перестанет ловить эту поломку. При смене blockedStageTicket_PlanRecalculated_KeepsPauseAndReason проверь это утверждение.");
        String card = BoardWhy.waitReason(board, recalculated, stock, clock[0]).orElse(null);
        assertTrue(
                card != null && card.contains("заблокировано"),
                "Карточка доски называет причину паузы после серии провалов:  Иначе тест blockedStageTicket_PlanRecalculated_KeepsPauseAndReason перестанет ловить эту поломку. При смене blockedStageTicket_PlanRecalculated_KeepsPauseAndReason проверь это утверждение."
                        + card);
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
        assertEquals(
                BoardOutcome.ACCEPTED,
                board.submit(rescue),
                "Живое задание спасения принимается доской Иначе тест rescueTicket_StuckSpirit_IssuedThroughBoard перестанет ловить эту поломку. При смене rescueTicket_StuckSpirit_IssuedThroughBoard проверь это утверждение.");
        board.stuck().mark(spirit, now);
        Optional<DispatchRound.Candidate> chosen =
                new DispatchRound(board, now).choose(rescue, List.of(candidate(spirit, "spirit-3")));
        assertTrue(
                chosen.isPresent(),
                "Задание спасения выдаётся самому застрявшему духу: иначе метка «застрял» запрещает "
                        + "единственный способ сдвинуться (observe15 B1) При смене rescueTicket_StuckSpirit_IssuedThroughBoard проверь это утверждение.");
        assertEquals(
                BoardOutcome.CLAIMED,
                board.claim(rescue.id(), spirit, now),
                "Claim спасения проходит через путь доски, несмотря на метку застревания Иначе тест rescueTicket_StuckSpirit_IssuedThroughBoard перестанет ловить эту поломку. При смене rescueTicket_StuckSpirit_IssuedThroughBoard проверь это утверждение.");
        assertTrue(
                board.complete(rescue.id()),
                "Успешное спасение закрывает задание Иначе тест rescueTicket_StuckSpirit_IssuedThroughBoard перестанет ловить эту поломку. При смене rescueTicket_StuckSpirit_IssuedThroughBoard проверь это утверждение.");
        assertTrue(
                board.stuck().isStuck(spirit, now),
                "Закрытие задания не выдаёт себя за выход: метка держится до наблюдённой поверхности "
                        + "(observe25 B6) Иначе тест rescueTicket_StuckSpirit_IssuedThroughBoard перестанет ловить эту поломку. При смене rescueTicket_StuckSpirit_IssuedThroughBoard проверь это утверждение.");
        board.stuck().see(spirit, new BlockCoord(at.x() + 4, at.y() + 6, at.z()), now + 1, true);
        assertFalse(
                board.stuck().isStuck(spirit, now + 1),
                "Наблюдённый выход снимает метку «застрял»: дух снова получает обычные задания (observe15 B1) Иначе тест rescueTicket_StuckSpirit_IssuedThroughBoard перестанет ловить эту поломку. При смене rescueTicket_StuckSpirit_IssuedThroughBoard проверь это утверждение.");
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
        assertEquals(
                BoardOutcome.ACCEPTED,
                board.submit(mine),
                "Живое обычное задание принимается доской Иначе тест stuckSpirit_RescueWorkClaimable_OrdinaryWorkRefused перестанет ловить эту поломку. При смене stuckSpirit_RescueWorkClaimable_OrdinaryWorkRefused проверь это утверждение.");
        assertEquals(
                BoardOutcome.ACCEPTED,
                board.submit(rescue),
                "Задание выхода принимается доской Иначе тест stuckSpirit_RescueWorkClaimable_OrdinaryWorkRefused перестанет ловить эту поломку. При смене stuckSpirit_RescueWorkClaimable_OrdinaryWorkRefused проверь это утверждение.");
        board.stuck().see(spirit, point(bot.get("position")), now);
        board.stuck().mark(spirit, now, "застрял (-188.5, 67.0, -516) у двора");
        List<DispatchRound.Candidate> candidates = List.of(candidate(spirit, "spirit-3"));
        DispatchRound round = new DispatchRound(board, now);
        assertTrue(
                round.choose(mine, candidates).isEmpty(),
                "Пока дух не двигается, обычное задание ему не выдают: иначе он снова упадёт При смене stuckSpirit_RescueWorkClaimable_OrdinaryWorkRefused проверь это утверждение.");
        String why = round.why(mine, candidates);
        assertTrue(
                why.contains("не двигается") && why.contains("rescue"),
                "Причина ожидания честно называет выход, который ставит граф:  Иначе тест stuckSpirit_RescueWorkClaimable_OrdinaryWorkRefused перестанет ловить эту поломку. При смене stuckSpirit_RescueWorkClaimable_OrdinaryWorkRefused проверь это утверждение."
                        + why);
        Optional<DispatchRound.Candidate> exit = round.choose(rescue, candidates);
        assertTrue(
                exit.isPresent(),
                "Задание выхода выдаётся самому застрявшему духу, а не ждёт спасения извне Иначе тест stuckSpirit_RescueWorkClaimable_OrdinaryWorkRefused перестанет ловить эту поломку. При смене stuckSpirit_RescueWorkClaimable_OrdinaryWorkRefused проверь это утверждение.");
        assertEquals(
                BoardOutcome.CLAIMED,
                board.claim(rescue.id(), spirit, now),
                "Выход выдаётся через claim доски Иначе тест stuckSpirit_RescueWorkClaimable_OrdinaryWorkRefused перестанет ловить эту поломку. При смене stuckSpirit_RescueWorkClaimable_OrdinaryWorkRefused проверь это утверждение.");
        assertTrue(
                board.complete(rescue.id()),
                "Выполненный выход закрывает задание Иначе тест stuckSpirit_RescueWorkClaimable_OrdinaryWorkRefused перестанет ловить эту поломку. При смене stuckSpirit_RescueWorkClaimable_OrdinaryWorkRefused проверь это утверждение.");
        assertTrue(
                board.stuck().isStuck(spirit, now),
                "Метка держится после закрытия задания: выход подтверждает только наблюдение поверхности "
                        + "(observe25 B6) Иначе тест stuckSpirit_RescueWorkClaimable_OrdinaryWorkRefused перестанет ловить эту поломку. При смене stuckSpirit_RescueWorkClaimable_OrdinaryWorkRefused проверь это утверждение.");
        board.stuck().see(spirit, new BlockCoord(0, 0, 0), now + 1, true);
        assertFalse(
                board.stuck().isStuck(spirit, now + 1),
                "Наблюдённый выход снимает метку «не двигается» по факту Иначе тест stuckSpirit_RescueWorkClaimable_OrdinaryWorkRefused перестанет ловить эту поломку. При смене stuckSpirit_RescueWorkClaimable_OrdinaryWorkRefused проверь это утверждение.");
    }
}
