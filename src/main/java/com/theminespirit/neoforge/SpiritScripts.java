package com.theminespirit.neoforge;

import com.theminespirit.core.engine.ConditionRegistry;
import com.theminespirit.core.engine.ScriptRegistry;
import com.theminespirit.core.script.FollowParams;
import com.theminespirit.core.script.GotoParams;
import java.util.Map;

public final class SpiritScripts {
    private SpiritScripts() {
    }

    public static ScriptRegistry registryFor(SpiritEntity spirit) {
        return ScriptRegistry.of(Map.of(
                "goto", params -> new GotoScript(spirit, GotoParams.from(params)),
                "follow", params -> new FollowScript(spirit, FollowParams.from(params))));
    }

    public static ConditionRegistry conditions() {
        return ConditionRegistry.of(Map.of());
    }
}