package com.theminespirit.core.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BoardUnblockRoutesTests {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String TICKET = "graph:building:wheat_field";

    private FakeBoardGateway gateway;
    private Router router;

    @BeforeEach
    void setUp() {
        gateway = new FakeBoardGateway();
        router = Router.of(new BoardRoutes(gateway, new IdempotencyCache()).routes());
    }

    @Test
    void unblock_BlockedTicket_ReturnsPendingAndPreservesId() throws Exception {
        gateway.with(new BoardTask(
                TICKET,
                "BUILD_PLACED",
                50,
                "spirit-1",
                "BLOCKED",
                Map.of(),
                Map.of(),
                List.of(),
                7,
                1,
                "graph:building:wheat_field"));

        RestResponse response = send();

        assertEquals(
                200,
                response.status(),
                "Снятие блокировки обязано вернуть 200: иначе оператор снова начнёт отменять билет и потеряет "
                        + "его цель. При смене unblock_BlockedTicket_ReturnsPendingAndPreservesId проверь "
                        + "BoardRoutes.unblock и живой wheat_field.");
        JsonNode task = MAPPER.readTree(response.body());
        assertEquals(
                TICKET,
                task.get("id").asText(),
                "Разблокировка сохраняет id: иначе граф создаст дубль вместо возврата исходного задания. "
                        + "При смене unblock_BlockedTicket_ReturnsPendingAndPreservesId проверь BoardGateway.unblock.");
        assertEquals(
                "PENDING",
                task.get("status").asText(),
                "После разблокировки билет снова ждёт исполнителя: иначе BLOCKED останется на доске и дух "
                        + "не получит работу. При смене unblock_BlockedTicket_ReturnsPendingAndPreservesId "
                        + "проверь BoardFailureDesk.unblock.");
        assertEquals(
                List.of(TICKET),
                gateway.unblocks(),
                "Мост обязан вызвать разблокировку ровно один раз: иначе REST будет только менять ответ, "
                        + "не снимая блокировку. При смене unblock_BlockedTicket_ReturnsPendingAndPreservesId "
                        + "проверь SpiritBoardGateway.unblockTask.");
    }

    @Test
    void unblock_ActiveTicket_ReturnsConflict() {
        gateway.with(new BoardTask(
                TICKET,
                "BUILD_PLACED",
                50,
                "spirit-1",
                "ASSIGNED",
                Map.of(),
                Map.of(),
                List.of(),
                7,
                1,
                "graph:building:wheat_field"));

        RestResponse response = send();

        assertEquals(
                409,
                response.status(),
                "Активное задание нельзя разблокировать: иначе оператор вмешается в работу духа и снимет "
                        + "чужие аренды. При смене unblock_ActiveTicket_ReturnsConflict проверь ветку отказа "
                        + "SpiritBoardGateway.unblockTask.");
        assertTrue(
                response.body().contains("BLOCKED"),
                "Отказ обязан назвать ожидаемый статус: иначе оператор не поймёт, почему запрос не сработал. "
                        + "При смене unblock_ActiveTicket_ReturnsConflict проверь текст ApiException.");
        assertTrue(
                gateway.unblocks().isEmpty(),
                "Отказ не должен менять доску: иначе активный билет молча сбросит счетчики. "
                        + "При смене unblock_ActiveTicket_ReturnsConflict проверь FakeBoardGateway.unblock.");
    }

    private RestResponse send() {
        return router.handle(
                new RestRequest("POST", "/v1/board/tasks/" + TICKET + "/unblock", Map.of(), Map.of(), new byte[0]));
    }
}
