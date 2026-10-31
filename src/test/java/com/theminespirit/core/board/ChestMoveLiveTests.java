package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ChestMoveLiveTests {
    private static final Path BOARD = Path.of("src/test/resources/live/storefull_board.json");
    private static final Path OBSERVE = Path.of("src/test/resources/live/storefull_observe.json");
    private static final Path OBSERVE11_BOARD = Path.of("src/test/resources/live/observe11-board.json");

    @Test
    void liveMoveTicket_DepositsIntoFreeYardChest_NotIntoPinnedFullChest() throws IOException {
        JsonNode ticket = liveMove();
        String id = ticket.get("id").asText();
        String item = ticket.get("supplyItem").asText();
        int count = ticket.get("supplyCount").asInt();
        BlockCoord from = from(id);
        BlockCoord yard = yardChest();

        TicketTarget target = TicketTarget.at(yard).withSupply(item, count).withRelocate(from, yard);
        SequenceNode plan = (SequenceNode) TicketPlans.plan(BoardCase.SUPPLY_SITE, target);
        List<ScriptCall> calls =
                plan.steps().stream().map(ScriptCall.class::cast).toList();

        assertEquals(
                List.of("withdraw", "goto", "deposit"),
                calls.stream().map(ScriptCall::script).toList(),
                "перенос из старого сундука идёт к двору и только потом ищет свободный сундук: закреплённый "
                        + "при постановке сундук к сдаче уже полон (observe10 A3)");
        assertEquals(
                Map.of("x", yard.x(), "y", yard.y(), "z", yard.z()),
                calls.get(1).params(),
                "переход ведёт к сундуку двора, а не в пустую клетку");
        assertEquals(
                "yard",
                calls.get(2).params().get("container"),
                "deposit выбирает свободный сундук двора в момент сдачи; фиксированный полный сундук и дал "
                        + "«контейнер полон: перенесено 0 из " + count + "»");
    }

    @Test
    void liveFarMoveTicket_RefusedByDistanceLimit_NotSentAcrossCaves() throws IOException {
        JsonNode ticket = farMove();
        String id = ticket.get("id").asText();
        String item = ticket.get("supplyItem").asText();
        int count = ticket.get("supplyCount").asInt();
        BlockCoord from = from(id);
        BlockCoord yard = new BlockCoord(-181, 69, -517);

        TicketTarget target = TicketTarget.at(yard).withSupply(item, count).withRelocate(from, yard);

        IllegalArgumentException refused = assertThrows(
                IllegalArgumentException.class,
                () -> TicketPlans.plan(BoardCase.SUPPLY_SITE, target),
                "перенос из сундука -92,67,-113 не строится: до двора около 400 блоков через пещеры "
                        + "(observe11 N2)");
        assertTrue(
                refused.getMessage().contains("предел"),
                "отказ называет предел расстояния, а не молчит: " + refused.getMessage());
    }

    private static JsonNode farMove() throws IOException {
        JsonNode board = new ObjectMapper().readTree(read(OBSERVE11_BOARD));
        for (JsonNode task : board.get("tasks")) {
            String id = task.get("id").asText();
            if (id.startsWith("move:-92,67,-113:") && !task.get("supplyItem").isNull()) {
                return task;
            }
        }
        throw new AssertionError("на живой доске observe11 нет переноса из -92,67,-113: фикстура устарела");
    }

    private static JsonNode liveMove() throws IOException {
        JsonNode board = new ObjectMapper().readTree(read(BOARD));
        for (JsonNode task : board.get("tasks")) {
            String id = task.get("id").asText();
            String status = task.get("status").asText();
            if (id.startsWith("move:")
                    && !"DONE".equals(status)
                    && !task.get("supplyItem").isNull()) {
                return task;
            }
        }
        throw new AssertionError("на живой доске нет переноса из старого сундука: фикстура устарела");
    }

    private static BlockCoord from(String id) {
        String body = id.substring("move:".length(), id.indexOf(':', "move:".length()));
        int first = body.indexOf(',');
        int second = body.indexOf(',', first + 1);
        return new BlockCoord(
                Integer.parseInt(body.substring(0, first)),
                Integer.parseInt(body.substring(first + 1, second)),
                Integer.parseInt(body.substring(second + 1)));
    }

    private static BlockCoord yardChest() throws IOException {
        JsonNode observe = new ObjectMapper().readTree(read(OBSERVE));
        for (JsonNode container : observe.get("container_items")) {
            JsonNode pos = container.get("pos");
            BlockCoord at = new BlockCoord(
                    pos.get(0).asInt(), pos.get(1).asInt(), pos.get(2).asInt());
            if (at.x() == -181 && at.z() == -517) {
                return at;
            }
        }
        throw new AssertionError("в живом наблюдении нет сундука двора -181,69,-517: фикстура не о том складе");
    }

    private static String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}
