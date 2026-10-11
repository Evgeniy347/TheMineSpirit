package com.theminespirit.neoforge.rest;

import com.theminespirit.core.rest.BotGateway;
import com.theminespirit.core.rest.Router;
import com.theminespirit.core.rest.V1Routes;
import com.theminespirit.neoforge.SpiritScripts;

public final class RestRouter {
    private RestRouter() {
    }

    public static Router create(BotGateway gateway) {
        return Router.of(V1Routes.routes(gateway, SpiritScripts.catalog()));
    }
}
