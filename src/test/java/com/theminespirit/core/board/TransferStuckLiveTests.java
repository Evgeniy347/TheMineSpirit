package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TransferStuckLiveTests {
    private static final Path DIR = Path.of("src/test/resources/live/observe16-transfer");
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final EatNeed FED = new EatNeed(20, 20, 20, 20);

    @Test
    void restore_StuckMarkOldSession_RebasesTickToRestart() throws IOException {
        JsonNode bots = MAPPER.readTree(Files.readString(DIR.resolve("bots.json"), StandardCharsets.UTF_8));
        UUID spirit = UUID.fromString(bot(bots, "spirit-3").get("id").asText());
        long old = 23_625_631L;
        long restart = 23_680_700L;
        long[] clock = {old};
        Board board = new Board(() -> clock[0]);
        board.stuck().mark(spirit, old);
        BoardState snapshot = board.snapshot();

        clock[0] = restart;
        Board restored = new Board(() -> clock[0]);
        restored.restore(snapshot);

        long since = restored.stuck().of(spirit, restart).orElseThrow().since();
        assertEquals(
                restart,
                since,
                "Тик застревания после перезапуска — текущий, а не из прошлой сессии: иначе waitReason "
                        + "показывает «с тика " + old + "» при state_since_tick " + restart + " (observe16 N3)");
    }

    @Test
    void transfer_StuckAssignee_FreeSpiritCarries() throws IOException {
        JsonNode bots = MAPPER.readTree(Files.readString(DIR.resolve("bots.json"), StandardCharsets.UTF_8));
        UUID stuck = UUID.fromString(bot(bots, "spirit-3").get("id").asText());
        UUID free = UUID.fromString(bot(bots, "spirit-2").get("id").asText());
        long now = bot(bots, "spirit-3").get("tick").asLong();
        Board board = new Board(() -> now);
        BoardTicket transfer = new BoardTicket(
                "transfer:graph:craft_stone_axe:stick:warehouse",
                BoardCase.TRANSFER_ITEM,
                Optional.of(stuck),
                BoardRules.priorityOf(BoardCase.TRANSFER_ITEM),
                now,
                1,
                "graph:craft_stone_axe",
                List.of(),
                Map.of(),
                TransferTicket.modifiers(free, "minecraft:stick", 1, null));
        assertEquals(BoardOutcome.ACCEPTED, board.submit(transfer), "Живой перенос со склада принимается доской");
        board.stuck().mark(stuck, now);
        List<DispatchRound.Candidate> candidates = List.of(candidate(stuck, "spirit-3"), candidate(free, "spirit-2"));

        Optional<DispatchRound.Candidate> chosen = new DispatchRound(board, now).choose(transfer, candidates);

        assertTrue(
                chosen.isPresent(),
                "Перенос, чей держатель застрял, передаётся свободному духу, а не стоит вечно " + "(observe16 N9)");
        assertEquals(free, chosen.get().id(), "Свободный дух spirit-2 несёт перенос вместо застрявшего spirit-3");
        assertEquals(
                BoardOutcome.CLAIMED,
                board.claim(transfer.id(), free, now),
                "Claim переноса свободным духом проходит через путь доски");
        assertEquals(
                Optional.of(free),
                board.holderOf(transfer.id()),
                "Держателем переноса становится фактический носитель, а не старый застрявший дух");
    }

    private static DispatchRound.Candidate candidate(UUID id, String name) {
        return new DispatchRound.Candidate(id, name, null, List.of(), FED, SpiritView.free());
    }

    private static JsonNode bot(JsonNode bots, String name) {
        for (JsonNode bot : bots.get("bots")) {
            if (bot.get("name").asText().equals(name)) {
                return bot;
            }
        }
        throw new IllegalStateException("В фикстуре observe16 нет духа " + name);
    }
}
