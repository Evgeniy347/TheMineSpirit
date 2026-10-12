package com.theminespirit.neoforge.rest;

import com.theminespirit.core.rest.IdempotencyCache;
import com.theminespirit.core.rest.PlanningService;
import com.theminespirit.core.rest.Router;
import com.theminespirit.core.rest.Route;
import com.theminespirit.core.rest.SseSlots;
import com.theminespirit.core.rest.TaskRoutes;
import com.theminespirit.core.rest.V1Routes;
import com.theminespirit.neoforge.SpiritScripts;
import java.util.ArrayList;
import java.util.List;

public final class RestRouter {
    private RestRouter() {
    }

    public static Router create(SpiritBotGateway gateway) {
        List<Route> routes = new ArrayList<>(V1Routes.routes(gateway, SpiritScripts.catalog()));
        SpiritTaskGateway taskGateway = new SpiritTaskGateway(gateway.index(), gateway.serverCall());
        TaskRoutes taskRoutes = new TaskRoutes(gateway.taskRegistry(), taskGateway,
                new PlanningService(SpiritScripts.catalog(), SpiritScripts.conditionCatalog()),
                new IdempotencyCache(), new SseSlots());
        routes.addAll(taskRoutes.routes());
        return Router.of(routes);
    }
}
