package com.theminespirit.core.rest;

import com.theminespirit.core.board.graph.GraphDecisions;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class GraphRoutes {
    public static final String DECISIONS = "/v1/graph/decisions";

    private GraphRoutes() {}

    public static List<Route> of(GraphGateway gateway) {
        Objects.requireNonNull(gateway, "Мост графа не может быть null: без него решения не прочитать");
        return List.of(new Route("GET", DECISIONS, (request, params) -> RestResponse.ok(json(gateway.decisions()))));
    }

    static String json(GraphDecisions decisions) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("tick", decisions.tick());
        List<Map<String, Object>> rows = new ArrayList<>(decisions.decisions().size());
        for (GraphDecisions.Row row : decisions.decisions()) {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("spirit", row.spirit());
            out.put("task", row.task());
            out.put("weight", row.weight());
            out.put("feasible", row.feasible());
            out.put("next", row.next());
            out.put("next_weight", row.nextWeight());
            out.put("why", row.why());
            rows.add(out);
        }
        body.put("decisions", rows);
        List<Map<String, Object>> vital = new ArrayList<>(decisions.vital().size());
        for (GraphDecisions.Vital row : decisions.vital()) {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("spirit", row.spirit());
            out.put("need", row.need());
            out.put("reason", row.reason());
            out.put("task", row.task());
            out.put("doing", row.doing());
            vital.add(out);
        }
        body.put("vital", vital);
        return RestJson.write(body);
    }
}
