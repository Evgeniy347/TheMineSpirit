package com.theminespirit.neoforge;

import com.theminespirit.core.engine.ConditionRegistry;
import com.theminespirit.core.engine.ScriptRegistry;
import com.theminespirit.core.script.CollectDropsParams;
import com.theminespirit.core.script.ConditionCatalog;
import com.theminespirit.core.script.CoreScripts;
import com.theminespirit.core.script.FollowParams;
import com.theminespirit.core.script.GotoParams;
import com.theminespirit.core.script.ScanParams;
import com.theminespirit.core.script.ScriptCatalog;
import java.util.Map;

public final class SpiritScripts {
    private SpiritScripts() {
    }

    public static ScriptRegistry registryFor(SpiritEntity spirit) {
        return ScriptRegistry.of(Map.of(
                "scan", params -> new ScanScript(spirit, ScanParams.from(params)),
                "collect_drops", params -> new CollectDropsScript(spirit, CollectDropsParams.from(params)),
                "goto", params -> new GotoScript(spirit, GotoParams.from(params)),
                "follow", params -> new FollowScript(spirit, FollowParams.from(params))));
    }

    public static ConditionRegistry conditions() {
        return ConditionRegistry.of(Map.of());
    }

    public static ScriptCatalog catalogFor(SpiritEntity spirit) {
        return CoreScripts.defaultCatalog().restrictedTo(registryFor(spirit).ids());
    }
}
