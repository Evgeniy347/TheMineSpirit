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

class Observe10ProtectionTests {
    private static final Path PLAN = Path.of("src/test/resources/live/observe10/plan-torch.json");

    @Test
    void decide_PlayerWallTorchInObserve10MinePlan_Allowed() throws IOException {
        JsonNode plan = new ObjectMapper().readTree(Files.readString(PLAN, StandardCharsets.UTF_8));
        JsonNode torch = plan.get("torch");
        BlockProtection protection = ProtectionConfig.readClasspath();

        assertInstanceOf(
                BlockProtection.Refused.class,
                protection.checkOwn("minecraft:wall_torch", false, false),
                "protection.json обязан держать '*torch' в deny: тест повторяет живой отказ observe10, "
                        + "а не выдуманный список");
        assertTrue(
                torch.get("inPlan").asBoolean(),
                "Живой факел шахты лежит в клетке плана: иначе тест проверяет не тот случай observe10");
        assertTrue(
                torch.get("placedByPlayer").asBoolean(),
                "Живой факел записан в наборе блоков игрока: именно этот признак уводил решение в DEFAULT");

        BlockProtection.Decision decision = SettlementBreak.decide(
                protection,
                new SettlementBreak.Facts(
                        SettlementBreak.Origin.PLAYER,
                        torch.get("onPlot").asBoolean(),
                        torch.get("inPlan").asBoolean(),
                        torch.get("block").asText(),
                        false,
                        false),
                () -> Collections.nCopies(BlockProtection.NEIGHBOUR_COUNT, "minecraft:air"));

        assertInstanceOf(
                BlockProtection.Allowed.class,
                decision,
                "Факел в клетке плана шахты обязан ломаться: иначе wall_torch держит спуск на 75% "
                        + "(observe10: 20 отказов, правка minetorch не сработала). Перепроверьте порядок "
                        + "SettlementBreak.of: план важнее метки «поставил игрок»");
    }

    @Test
    void decide_PlayerBlockOutsidePlan_StillRefused() throws IOException {
        JsonNode plan = new ObjectMapper().readTree(Files.readString(PLAN, StandardCharsets.UTF_8));
        BlockProtection protection = ProtectionConfig.readClasspath();

        BlockProtection.Decision decision = SettlementBreak.decide(
                protection,
                new SettlementBreak.Facts(
                        SettlementBreak.Origin.PLAYER,
                        plan.get("torch").get("onPlot").asBoolean(),
                        false,
                        plan.get("torch").get("block").asText(),
                        false,
                        false),
                () -> Collections.nCopies(BlockProtection.NEIGHBOUR_COUNT, "minecraft:air"));

        assertInstanceOf(
                BlockProtection.Refused.class,
                decision,
                "Шаг в план не должен открывать чужие постройки: поставленное игроком вне клеток плана "
                        + "по-прежнему защищено");
    }
}
