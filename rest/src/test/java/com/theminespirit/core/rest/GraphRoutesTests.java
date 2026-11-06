package com.theminespirit.core.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.graph.GraphDecisions;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GraphRoutesTests {
    @Test
    void decisions_Snapshot_ReturnsTickAndRows() {
        GraphGateway gateway = () -> new GraphDecisions(
                777,
                List.of(new GraphDecisions.Row(
                        "spirit-1", "make:dirt", 42, true, "chop_oak_log", 38, "вес ниже на 4")));
        Router router = Router.of(GraphRoutes.of(gateway));

        RestResponse response =
                router.handle(new RestRequest("GET", GraphRoutes.DECISIONS, Map.of(), Map.of(), new byte[0]));

        assertEquals(200, response.status(), "GET /v1/graph/decisions обязан отвечать снимком решений");
        assertTrue(response.body().contains("\"tick\":777"), "Снимок называет тик: " + response.body());
        assertTrue(response.body().contains("\"spirit\":\"spirit-1\""), "Снимок называет духа: " + response.body());
        assertTrue(response.body().contains("\"task\":\"make:dirt\""), "Снимок называет работу: " + response.body());
        assertTrue(response.body().contains("\"next\":\"chop_oak_log\""), "Снимок называет следующего кандидата");
        assertTrue(response.body().contains("\"why\":\"вес ниже на 4\""), "Снимок объясняет отказ: " + response.body());
    }

    @Test
    void decisions_EmptySnapshot_StillValidJson() {
        GraphGateway gateway = GraphDecisions::empty;

        RestResponse response = Router.of(GraphRoutes.of(gateway))
                .handle(new RestRequest("GET", GraphRoutes.DECISIONS, Map.of(), Map.of(), new byte[0]));

        assertEquals(200, response.status(), "Пустой снимок — не ошибка: граф ещё не считался");
        assertTrue(response.body().contains("\"decisions\":[]"), "Пустой список решений в ответе: " + response.body());
    }
}
