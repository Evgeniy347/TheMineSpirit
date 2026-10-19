package com.theminespirit.core.rest;

import com.theminespirit.core.script.ScriptCatalog;
import java.util.List;

final class CapabilitiesRoutes {
    private CapabilitiesRoutes() {}

    static List<Route> of(ScriptCatalog catalog) {
        String json = CapabilitiesJson.toJson(catalog);
        return List.of(new Route("GET", "/v1/capabilities", (request, params) -> RestResponse.ok(json)));
    }
}
