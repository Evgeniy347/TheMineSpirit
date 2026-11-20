package com.theminespirit.core.building;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ChestPairTests {
    private static final Path FIXTURE = Path.of("src/test/resources/live/observe10-chests.json");

    @Test
    void liveSecondCell_FreeButUnsupported_RefusesTheFirstHalf() throws IOException {
        JsonNode cell = new ObjectMapper().readTree(read()).get("second_cell");

        assertEquals(
                ChestPair.Decision.REFUSE,
                new ChestPair(
                                false,
                                cell.get("free").asBoolean(),
                                cell.get("supported").asBoolean(),
                                cell.get("reachable").asBoolean(),
                                cell.get("stale").asBoolean())
                        .decision(),
                "клетка -180,69,-517 из живой задачи chests:1 свободна, но без опоры: постановка первой половины "
                        + "оставила бы воздух с BlockEntity и краш-цикл сервера"
                        + " Иначе тест liveSecondCell_FreeButUnsupported_RefusesTheFirstHalf перестанет ловить эту поломку. При смене liveSecondCell_FreeButUnsupported_RefusesTheFirstHalf проверь это утверждение.");
    }

    @Test
    void existingPartner_AlreadyChest_AllowsTheHalf() {
        assertEquals(
                ChestPair.Decision.PLACE,
                new ChestPair(true, false, false, false, false).decision(),
                "вторая половина уже стоит сундуком: достраивать пару можно даже без свободной клетки"
                        + " Иначе тест existingPartner_AlreadyChest_AllowsTheHalf перестанет ловить эту поломку. При смене existingPartner_AlreadyChest_AllowsTheHalf проверь это утверждение.");
    }

    @Test
    void freeSupportedReachablePartner_CellFree_AllowsTheHalf() {
        assertEquals(
                ChestPair.Decision.PLACE,
                new ChestPair(false, true, true, true, false).decision(),
                "вторая клетка свободна, с опорой и в досягаемости: половину ставить можно"
                        + " Иначе тест freeSupportedReachablePartner_CellFree_AllowsTheHalf перестанет ловить эту поломку. При смене freeSupportedReachablePartner_CellFree_AllowsTheHalf проверь это утверждение.");
    }

    @Test
    void occupiedPartner_WrongBlock_RefusesTheHalf() {
        assertEquals(
                ChestPair.Decision.REFUSE,
                new ChestPair(false, false, true, true, false).decision(),
                "клетка занята чужим блоком: половина двойного сундука осталась бы без пары"
                        + " Иначе тест occupiedPartner_WrongBlock_RefusesTheHalf перестанет ловить эту поломку. При смене occupiedPartner_WrongBlock_RefusesTheHalf проверь это утверждение.");
    }

    private static String read() throws IOException {
        return Files.readString(FIXTURE, StandardCharsets.UTF_8);
    }
}
