package com.theminespirit.core.town;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.theminespirit.core.observation.BlockCoord;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ChestSpaceLiveTests {
    private static final Path LIVE = Path.of("src/test/resources/live/yard-chest-overflow/space.json");

    @Test
    void liveChests_YardFullFarEmpty_CountsOnlySettlement() throws IOException {
        JsonNode live = live();
        List<ChestSpace.Spot> spots = spots(live);

        ChestSpace space = ChestSpace.of(spots, reach(live));

        assertEquals(
                162,
                space.slots(),
                "в поселении три двойных сундука двора по 54 слота: дальние сундуки -92/-82 за границей "
                        + "поселения не входят в склад и не должны разбавлять процент");
        assertEquals(12, space.free(), "во дворе свободно 0+0+12 слотов: " + space);
        assertTrue(
                space.freePercent() < 20,
                "двор заполнен на " + (100 - space.freePercent()) + "%: порог 20% обязан сработать, а не "
                        + "тонуть в пустых дальних сундуках");
    }

    @Test
    void liveChests_NoReach_FallsBackToAll() throws IOException {
        List<ChestSpace.Spot> spots = spots(live());

        ChestSpace space = ChestSpace.of(spots, null);

        assertEquals(
                spots.stream().mapToInt(ChestSpace.Spot::slots).sum(),
                space.slots(),
                "без границы поселения считать нечего, кроме всех известных сундуков: так же, как раньше");
    }

    @Test
    void liveChests_ReachElsewhere_CountsNothing() throws IOException {
        List<ChestSpace.Spot> spots = spots(live());

        ChestSpace space = ChestSpace.of(spots, new SettlementReach(0, 0, 10, 10));

        assertEquals(0, space.slots(), "ни один живой сундук не попал в чужую границу: склад пуст");
        assertEquals(100, space.freePercent(), "пустой склад — 100% свободного места, а не деление на ноль");
    }

    @Test
    void emptyChests_NoContainers_IsHundredPercent() {
        assertEquals(
                100,
                ChestSpace.of(List.of(), null).freePercent(),
                "сундуков нет — свободного места 100%, а не деление на ноль");
    }

    private static JsonNode live() throws IOException {
        return new ObjectMapper().readTree(Files.readString(LIVE, StandardCharsets.UTF_8));
    }

    private static List<ChestSpace.Spot> spots(JsonNode live) {
        List<ChestSpace.Spot> spots = new ArrayList<>();
        for (JsonNode spot : live.get("spots")) {
            spots.add(new ChestSpace.Spot(
                    coord(spot.get("pos")),
                    spot.get("free").asInt(),
                    spot.get("slots").asInt()));
        }
        return spots;
    }

    private static SettlementReach reach(JsonNode live) {
        JsonNode value = live.get("reach");
        return new SettlementReach(
                value.get(0).asInt(),
                value.get(1).asInt(),
                value.get(2).asInt(),
                value.get(3).asInt());
    }

    private static BlockCoord coord(JsonNode value) {
        return new BlockCoord(
                value.get(0).asInt(), value.get(1).asInt(), value.get(2).asInt());
    }
}
