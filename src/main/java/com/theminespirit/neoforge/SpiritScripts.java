package com.theminespirit.neoforge;

import com.theminespirit.core.engine.ConditionRegistry;
import com.theminespirit.core.engine.ScriptFactory;
import com.theminespirit.core.engine.ScriptRegistry;
import com.theminespirit.core.script.CollectDropsParams;
import com.theminespirit.core.script.ConditionCatalog;
import com.theminespirit.core.script.CoreScripts;
import com.theminespirit.core.script.FollowParams;
import com.theminespirit.core.script.GotoParams;
import com.theminespirit.core.script.ScanParams;
import com.theminespirit.core.script.ScriptCatalog;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

public final class SpiritScripts {
    private static final Map<String, Function<SpiritEntity, ScriptFactory>> IMPLEMENTED = implemented();

    private SpiritScripts() {
    }

    private static Map<String, Function<SpiritEntity, ScriptFactory>> implemented() {
        Map<String, Function<SpiritEntity, ScriptFactory>> table = new LinkedHashMap<>();
        table.put("scan", spirit -> params -> new ScanScript(spirit, ScanParams.from(params)));
        table.put("collect_drops", spirit -> params -> new CollectDropsScript(spirit, CollectDropsParams.from(params)));
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

    public static ConditionCatalog conditionCatalog() {
        // Зеркало conditions(): у каждой проверки в реестре обязано быть описание в каталоге, иначе
        // PlanValidator отклонит условие, которое движок умеет исполнять. Пока проверок нет — каталог пуст.
        return ConditionCatalog.empty();
    }

    public static ScriptCatalog catalog() {
        return CoreScripts.defaultCatalog().restrictedTo(IMPLEMENTED.keySet());
    }

    public static ScriptCatalog catalogFor(SpiritEntity spirit) {
        return catalog();
    }
}
