package com.theminespirit.core.policy;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import org.junit.jupiter.api.Test;

class Observe14ProtectionTests {
    private static final Path EDITS = Path.of("src/test/resources/live/hutreopen/plot-edits.json");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void decide_LiveHutPlanBlocksBrokenBySpirit_Refused() throws IOException {
        JsonNode root = MAPPER.readTree(Files.readString(EDITS, StandardCharsets.UTF_8));
        BlockProtection protection = ProtectionConfig.readClasspath();
        int checked = 0;
        for (JsonNode event : root.get("events")) {
            BlockProtection.Decision decision = SettlementBreak.decide(
                    protection,
                    new SettlementBreak.Facts(
                            SettlementBreak.Origin.SPIRIT,
                            true,
                            true,
                            event.get("planBlock").asText(),
                            false,
                            false),
                    SettlementBreak.Intent.BREAK,
                    () -> Collections.nCopies(BlockProtection.NEIGHBOUR_COUNT, "minecraft:air"));
            assertInstanceOf(
                    BlockProtection.Refused.class,
                    decision,
                    "Дух вырубил блок плана готовой хижины в " + event.get("cell")
                            + " (" + event.get("who").asText() + "): правка мира у постройки не материал для добычи, "
                            + "её снимает только задание сноса (observe14 A12/N8, 52 блока)");
            checked++;
        }
        assertTrue(
                checked >= 50,
                "В живой фикстуре observe14 меньше 50 событий [plot-edit]: тест перестал повторять живой прогон, "
                        + "проверено " + checked);
    }

    @Test
    void decide_SameLiveHutPlanBlockDemolishedByTask_StillAllowed() throws IOException {
        JsonNode root = MAPPER.readTree(Files.readString(EDITS, StandardCharsets.UTF_8));
        JsonNode event = root.get("events").get(0);
        BlockProtection protection = ProtectionConfig.readClasspath();

        BlockProtection.Decision decision = SettlementBreak.decide(
                protection,
                new SettlementBreak.Facts(
                        SettlementBreak.Origin.SPIRIT,
                        true,
                        true,
                        event.get("planBlock").asText(),
                        false,
                        false),
                SettlementBreak.Intent.DEMOLISH,
                () -> Collections.nCopies(BlockProtection.NEIGHBOUR_COUNT, "minecraft:air"));

        assertInstanceOf(
                BlockProtection.Allowed.class,
                decision,
                "Задание сноса/перестройки обязано снимать свои блоки плана: иначе постройку не исправить; "
                        + "запрет выше касается добычи и копания, а не сноса");
    }
}
