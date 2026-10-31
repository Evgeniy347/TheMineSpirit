package com.theminespirit.core.inventory;

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
import org.junit.jupiter.api.Test;

class ContainerCellTests {
    private static final Path FIXTURE = Path.of("src/test/resources/live/observe10-chests.json");

    @Test
    void brokenContainer_AirWithChestEntity_IsAirWithEntity() throws IOException {
        ContainerCell cell = cell("broken_container");

        assertEquals(
                ContainerCell.Problem.AIR_WITH_ENTITY,
                cell.problem(),
                "живая клетка -180,69,-517 из observe10 — воздух с BlockEntity сундука: без распознавания "
                        + "ContainerLid снова полезет за ChestType в air и уронит тик сервера");
        assertFalse(
                cell.hasChestType(),
                "у воздуха не бывает свойства ChestType: проверка перед getValue обязана его отвергнуть, "
                        + "иначе IllegalArgumentException снова поймается в тике");
    }

    @Test
    void validHalf_ChestWithType_IsUsable() throws IOException {
        ContainerCell cell = cell("valid_half");

        assertEquals(
                ContainerCell.Problem.NONE,
                cell.problem(),
                "соседняя клетка -181,69,-517 — настоящий сундук: её крышка и звук должны работать без отказа");
        assertTrue(
                cell.hasChestType(),
                "у настоящего сундука есть type: без этого признака ContainerLid перестал бы открывать крышку");
        assertEquals("right", cell.chestType(), "тип половины из живой клетки обязан дойти до лога и звука");
    }

    @Test
    void chestBlockWithoutEntity_RegisteredChest_IsReportedAsBlockWithoutEntity() throws IOException {
        ContainerCell cell = cell("block_without_entity");

        assertEquals(
                ContainerCell.Problem.BLOCK_WITHOUT_ENTITY,
                cell.problem(),
                "склад -171,72,-487 отклонил клетку «нет блочной сущности»: StorageFinder обязан писать это "
                        + "расхождение отдельным событием, а не молчать про обратный случай air ↔ BlockEntity");
    }

    @Test
    void failedTicket_SecondCell_MatchesBrokenContainer() throws IOException {
        JsonNode root = new ObjectMapper().readTree(read());
        JsonNode ticket = root.get("failed_ticket");
        JsonNode pos = root.get("broken_container").get("pos");
        String coord =
                pos.get(0).asInt() + "," + pos.get(1).asInt() + "," + pos.get(2).asInt();

        assertEquals(
                "STOCK_CHESTS",
                ticket.get("case").asText(),
                "фикстура обязана быть живым провалом chests:1: подмена тикета сделает тест зелёным не про тот путь");
        assertTrue(
                ticket.get("message").asText().contains(coord),
                "тикет chests:1 обязан называть ту же клетку " + coord
                        + ", что и битый контейнер: иначе тест проверяет выдуманное состояние");
        assertTrue(
                ticket.get("message").asText().contains("опора false"),
                "живой провал говорит «опора false»: именно это обязано запрещать постановку второй половины, "
                        + "а не оставлять воздух с BlockEntity");
    }

    private static ContainerCell cell(String key) throws IOException {
        JsonNode node = new ObjectMapper().readTree(read()).get(key);
        JsonNode pos = node.get("pos");
        return new ContainerCell(
                new BlockCoord(
                        pos.get(0).asInt(), pos.get(1).asInt(), pos.get(2).asInt()),
                node.get("block").asText(),
                node.get("chest_block").asBoolean(),
                node.get("chest_type_property").asBoolean(),
                node.get("chest_type").isNull() ? null : node.get("chest_type").asText(),
                node.get("block_entity").isNull()
                        ? null
                        : node.get("block_entity").asText());
    }

    private static String read() throws IOException {
        return Files.readString(FIXTURE, StandardCharsets.UTF_8);
    }
}
