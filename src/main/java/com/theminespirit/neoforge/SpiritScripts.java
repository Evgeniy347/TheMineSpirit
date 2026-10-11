package com.theminespirit.neoforge;

import com.theminespirit.core.engine.ConditionRegistry;
import com.theminespirit.core.engine.ScriptFactory;
import com.theminespirit.core.engine.ScriptRegistry;
import com.theminespirit.core.script.CoreScripts;
import com.theminespirit.core.script.FollowParams;
import com.theminespirit.core.script.GotoParams;
import com.theminespirit.core.script.ScriptCatalog;
import com.theminespirit.core.script.ScriptSpec;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public final class SpiritScripts {
    private static final Map<String, Function<SpiritEntity, ScriptFactory>> IMPLEMENTED = implemented();

    private SpiritScripts() {
    }

    private static Map<String, Function<SpiritEntity, ScriptFactory>> implemented() {
        Map<String, Function<SpiritEntity, ScriptFactory>> table = new LinkedHashMap<>();
        table.put("goto", spirit -> params -> new GotoScript(spirit, GotoParams.from(params)));
        table.put("follow", spirit -> params -> new FollowScript(spirit, FollowParams.from(params)));
        return table;
    }

    public static ScriptRegistry registryFor(SpiritEntity spirit) {
        Map<String, ScriptFactory> factories = new LinkedHashMap<>();
        IMPLEMENTED.forEach((id, builder) -> factories.put(id, builder.apply(spirit)));
        return ScriptRegistry.of(factories);
    }

    public static ConditionRegistry conditions() {
        return ConditionRegistry.of(Map.of());
    }

    public static ScriptCatalog catalog() {
        ScriptCatalog all = CoreScripts.defaultCatalog();
        Set<String> ids = new LinkedHashSet<>();
        for (ScriptSpec spec : all.all()) {
            if (IMPLEMENTED.containsKey(spec.id())) {
                ids.add(spec.id());
            }
        }
        ids.addAll(IMPLEMENTED.keySet());
        return all.restrictedTo(ids);
    }

    public static ScriptCatalog catalogFor(SpiritEntity spirit) {
        return catalog();
    }
}
