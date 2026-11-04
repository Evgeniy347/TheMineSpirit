package com.theminespirit.core.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.graph.WorldReport;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WorldRoutesTests {
    @Test
    void world_Get_ReportsSummaryGroupsAndDamaged() {
        WorldReport report = new WorldReport(
                3,
                1,
                2,
                4,
                5,
                1,
                false,
                List.of(new WorldReport.Stray("oak_fence", 1, 64, 1, 3, "spirit-1")),
                List.of(new WorldReport.Damaged("warehouse", 4, false)));
        Router router = Router.of(new WorldRoutes(() -> report).routes());

        RestResponse response =
                router.handle(new RestRequest("GET", WorldRoutes.PATH, Map.of(), Map.of(), new byte[0]));

        assertEquals(200, response.status(), "GET /v1/world отдаёт сводку мира наблюдателю");
        assertTrue(
                response.body().contains("\"strays\":3")
                        && response.body().contains("\"strayGroups\":1")
                        && response.body().contains("\"pits\":2")
                        && response.body().contains("\"mounds\":4")
                        && response.body().contains("\"hanging\":5")
                        && response.body().contains("\"damagedBuildings\":1"),
                "Сводка называет лишнее, ямы, насыпи, висящее и повреждённые постройки. Тело: " + response.body());
        assertTrue(
                response.body().contains("\"type\":\"oak_fence\"")
                        && response.body().contains("\"builder\":\"spirit-1\""),
                "Наблюдатель видит кусок лишнего и его строителя. Тело: " + response.body());
    }
}
