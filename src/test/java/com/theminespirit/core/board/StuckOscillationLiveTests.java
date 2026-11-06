package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.theminespirit.core.observation.BlockCoord;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StuckOscillationLiveTests {
    private static final Path LIVE = Path.of("src/test/resources/live/observe13-water-oscillation.json");

    @Test
    void see_LiveOscillationInsideArea_KeepsStuckMark() throws IOException {
        JsonNode live = new ObjectMapper().readTree(Files.readString(LIVE, StandardCharsets.UTF_8));
        UUID spirit = UUID.fromString(live.get("spirit").asText());
        BlockCoord first = coord(live.get("positions").get(0));
        BlockCoord second = coord(live.get("positions").get(1));
        StuckSpirits stuck = new StuckSpirits();
        stuck.see(spirit, first);
        stuck.mark(spirit, 100);

        stuck.see(spirit, second);

        assertTrue(
                stuck.isStuck(spirit, 100),
                "spirit-1 в observe13 ходил между " + first + " и " + second + " в воде: это колебание, а не "
                        + "движение; снятие метки на любой смене клетки снова отдаёт ему задания (N23)");
    }

    @Test
    void see_SpiritWanderingFarInsidePit_KeepsStuckMark() throws IOException {
        JsonNode live = new ObjectMapper().readTree(Files.readString(LIVE, StandardCharsets.UTF_8));
        UUID spirit = UUID.fromString(live.get("spirit").asText());
        BlockCoord first = coord(live.get("positions").get(0));
        BlockCoord far = new BlockCoord(first.x() + 12, first.y() - 4, first.z());
        StuckSpirits stuck = new StuckSpirits();
        stuck.see(spirit, first);
        stuck.mark(spirit, 100);

        stuck.see(spirit, far);

        assertFalse(
                stuck.drainChanges().stream().anyMatch(change -> !change.marked()),
                "Сдвиг внутри ямы не выдаётся за выход: метка снимается только по поверхности (observe24 B1)");
        assertTrue(
                stuck.isStuck(spirit, 100),
                "Ушёл далеко, но остался в ловушке: метка держится, иначе дух снова получит работу в яме");
    }

    @Test
    void see_SpiritReachedSurface_ClearsStuckMark() throws IOException {
        JsonNode live = new ObjectMapper().readTree(Files.readString(LIVE, StandardCharsets.UTF_8));
        UUID spirit = UUID.fromString(live.get("spirit").asText());
        BlockCoord first = coord(live.get("positions").get(0));
        BlockCoord surface = new BlockCoord(first.x() + 4, first.y(), first.z());
        StuckSpirits stuck = new StuckSpirits();
        stuck.see(spirit, first);
        stuck.mark(spirit, 100);

        stuck.see(spirit, surface, 0, true);

        assertFalse(
                stuck.isStuck(spirit, 100),
                "Дух вышел на поверхность: метка застревания снимается, иначе он навсегда выпадает из раздачи");
    }

    private static BlockCoord coord(JsonNode node) {
        return new BlockCoord(
                node.get(0).asInt(), node.get(1).asInt(), node.get(2).asInt());
    }
}
