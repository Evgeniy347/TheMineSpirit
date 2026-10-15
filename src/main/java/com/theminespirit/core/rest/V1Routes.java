package com.theminespirit.core.rest;

import com.theminespirit.core.script.ScriptCatalog;
import java.util.ArrayList;
import java.util.List;

public final class V1Routes {
    private V1Routes() {
    }

    public static List<Route> routes(BotGateway gateway, ScriptCatalog catalog) {
        List<Route> routes = new ArrayList<>(CapabilitiesRoutes.of(catalog));
        routes.addAll(new BotRoutes(gateway).routes());
        routes.addAll(new DecisionRoutes(gateway).routes());
        return List.copyOf(routes);
    }
}
