package com.theminespirit.core.movement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.theminespirit.core.observation.BlockCoord;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class MagnetLiveTests {
    private static final Path LIVE = Path.of("src/test/resources/live/observe19/spirit2.json");

    @Test
    void around_LiveHomeIsSafe_ReturnsHome() throws IOException {
        JsonNode live = new ObjectMapper().readTree(Files.readString(LIVE, StandardCharsets.UTF_8));
        BlockCoord home = coord(live.get("home"));

        MagnetSearch.Result found = MagnetSearch.around(home, cell -> cell.equals(home), 8, 8);

        assertTrue(found.target().isPresent(), "дом spirit-2 " + home + " обязан быть точкой магнита");
        assertEquals(home, found.target().orElseThrow(), "магнит возвращает духа в поселение");
        assertTrue(found.checked() > 0, "поиск обязан сообщить, сколько клеток проверено");
    }

    @Test
    void around_HomeBlocked_FindsNeighbourOrReportsChecked() throws IOException {
        JsonNode live = new ObjectMapper().readTree(Files.readString(LIVE, StandardCharsets.UTF_8));
        BlockCoord home = coord(live.get("home"));
        BlockCoord safe = new BlockCoord(home.x() + 5, home.y(), home.z() + 5);

        MagnetSearch.Result found = MagnetSearch.around(home, cell -> cell.equals(safe), 8, 8);

        assertEquals(safe, found.target().orElseThrow(), "если дом занят, магнит ищет соседнюю безопасную клетку");
        assertTrue(found.checked() > 0, "причина отказа называет число проверенных клеток");
    }

    @Test
    void around_NoSafeCell_ReturnsEmptyWithCheckedCells() throws IOException {
        JsonNode live = new ObjectMapper().readTree(Files.readString(LIVE, StandardCharsets.UTF_8));
        BlockCoord home = coord(live.get("home"));

        MagnetSearch.Result found = MagnetSearch.around(home, cell -> false, 2, 2);

        assertTrue(found.target().isEmpty(), "без безопасной клетки магнит не выдумывает цель");
        assertTrue(found.checked() == 125, "проверено всё поле 5×5×5, а не случайная клетка: " + found.checked());
    }

    private static BlockCoord coord(JsonNode node) {
        return new BlockCoord(
                node.get(0).asInt(), node.get(1).asInt(), node.get(2).asInt());
    }
}
