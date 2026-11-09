package com.theminespirit.neoforge.rest;

import com.theminespirit.core.rest.BoardRoutes;
import com.theminespirit.core.rest.GraphRoutes;
import com.theminespirit.core.rest.HealthGateway;
import com.theminespirit.core.rest.HealthRoutes;
import com.theminespirit.core.rest.IdempotencyCache;
import com.theminespirit.core.rest.PlanningService;
import com.theminespirit.core.rest.PolicyRoutes;
import com.theminespirit.core.rest.QuarryRoutes;
import com.theminespirit.core.rest.Route;
import com.theminespirit.core.rest.Router;
import com.theminespirit.core.rest.SettlementRoutes;
import com.theminespirit.core.rest.SseSlots;
import com.theminespirit.core.rest.TaskRoutes;
import com.theminespirit.core.rest.V1Routes;
import com.theminespirit.core.rest.WarehouseRoutes;
import com.theminespirit.core.rest.WorldRoutes;
import com.theminespirit.neoforge.SpiritScripts;
import java.util.ArrayList;
import java.util.List;

public final class RestRouter {
    private RestRouter() {}

    public static Router create(SpiritBotGateway gateway) {
        return create(gateway, new SseSlots(), HealthGateway.unknown());
    }

    public static Router create(SpiritBotGateway gateway, HealthGateway health) {
        return create(gateway, new SseSlots(), health);
    }

    public static Router create(SpiritBotGateway gateway, SseSlots slots) {
        return create(gateway, slots, HealthGateway.unknown());
    }

    public static Router create(SpiritBotGateway gateway, SseSlots slots, HealthGateway health) {
        List<Route> routes = new ArrayList<>(V1Routes.routes(gateway, SpiritScripts.catalog()));
        routes.addAll(HealthRoutes.of(health));
        PlanningService planning = new PlanningService(SpiritScripts.catalog(), SpiritScripts.conditionCatalog());
        SpiritTaskGateway taskGateway = new SpiritTaskGateway(gateway.index(), gateway.serverCall(), planning);
        SpiritBoardGateway boardGateway = new SpiritBoardGateway(gateway.server());
        TaskRoutes taskRoutes = new TaskRoutes(
                gateway.taskRegistry(), taskGateway, planning, new IdempotencyCache(), slots, boardGateway);
        routes.addAll(taskRoutes.routes());
        BoardRoutes boardRoutes = new BoardRoutes(boardGateway, new IdempotencyCache());
        routes.addAll(boardRoutes.routes());
        routes.addAll(GraphRoutes.of(new SpiritGraphGateway(gateway.server())));
        routes.addAll(new QuarryRoutes(new SpiritQuarryGateway(gateway.server(), gateway.serverCall())).routes());
        PolicyRoutes policyRoutes = new PolicyRoutes(new SpiritPolicyGateway(gateway.server()), new IdempotencyCache());
        routes.addAll(policyRoutes.routes());
        SettlementRoutes settlementRoutes = new SettlementRoutes(
                new SpiritSettlementGateway(gateway.server(), gateway.serverCall()), new IdempotencyCache());
        routes.addAll(settlementRoutes.routes());
        WarehouseRoutes warehouseRoutes = new WarehouseRoutes(
                new SpiritWarehouseGateway(gateway.server(), gateway.serverCall()), new IdempotencyCache());
        routes.addAll(warehouseRoutes.routes());
        routes.addAll(new WorldRoutes(new SpiritWorldGateway()).routes());
        return Router.of(routes);
    }
}
