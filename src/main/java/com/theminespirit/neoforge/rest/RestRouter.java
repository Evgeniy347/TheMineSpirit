package com.theminespirit.neoforge.rest;

import com.theminespirit.core.rest.BoardRoutes;
import com.theminespirit.core.rest.IdempotencyCache;
import com.theminespirit.core.rest.PlanningService;
import com.theminespirit.core.rest.PolicyRoutes;
import com.theminespirit.core.rest.Route;
import com.theminespirit.core.rest.Router;
import com.theminespirit.core.rest.SseSlots;
import com.theminespirit.core.rest.TaskRoutes;
import com.theminespirit.core.rest.V1Routes;
import com.theminespirit.neoforge.SpiritScripts;
import java.util.ArrayList;
import java.util.List;

public final class RestRouter {
    private RestRouter() {}

    public static Router create(SpiritBotGateway gateway) {
        return create(gateway, new SseSlots());
    }

    public static Router create(SpiritBotGateway gateway, SseSlots slots) {
        List<Route> routes = new ArrayList<>(V1Routes.routes(gateway, SpiritScripts.catalog()));
        PlanningService planning = new PlanningService(SpiritScripts.catalog(), SpiritScripts.conditionCatalog());
        SpiritTaskGateway taskGateway = new SpiritTaskGateway(gateway.index(), gateway.serverCall(), planning);
        TaskRoutes taskRoutes =
                new TaskRoutes(gateway.taskRegistry(), taskGateway, planning, new IdempotencyCache(), slots);
        routes.addAll(taskRoutes.routes());
        BoardRoutes boardRoutes = new BoardRoutes(new SpiritBoardGateway(gateway.server()), new IdempotencyCache());
        routes.addAll(boardRoutes.routes());
        PolicyRoutes policyRoutes = new PolicyRoutes(new SpiritPolicyGateway(gateway.server()), new IdempotencyCache());
        routes.addAll(policyRoutes.routes());
        return Router.of(routes);
    }
}
