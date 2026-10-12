package com.theminespirit.neoforge;

import com.theminespirit.core.engine.ConditionRegistry;
import com.theminespirit.core.engine.ScriptFactory;
import com.theminespirit.core.engine.ScriptRegistry;
import com.theminespirit.core.script.BoneMealParams;
import com.theminespirit.core.script.ChopTreeParams;
import com.theminespirit.core.script.CollectDropsParams;
import com.theminespirit.core.script.CraftParams;
import com.theminespirit.core.script.ConditionCatalog;
import com.theminespirit.core.script.CoreScripts;
import com.theminespirit.core.script.FollowParams;
import com.theminespirit.core.script.GatherParams;
import com.theminespirit.core.script.GotoParams;
import com.theminespirit.core.script.HarvestCropParams;
import com.theminespirit.core.script.LocateParams;
import com.theminespirit.core.script.MineVeinParams;
import com.theminespirit.core.script.PerceptionParams;
import com.theminespirit.core.script.PlantCropParams;
import com.theminespirit.core.script.ScanParams;
import com.theminespirit.core.script.SmeltParams;
import com.theminespirit.core.script.ScriptCatalog;
import com.theminespirit.core.script.TillParams;
import com.theminespirit.neoforge.crafting.CraftScript;
import com.theminespirit.neoforge.crafting.SmeltScript;
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
        table.put("scan_blocks", spirit -> params -> new ScanScript(spirit, PerceptionParams.blocks(params)));
        table.put("scan_entities", spirit -> params -> new ScanScript(spirit, PerceptionParams.entities(params)));
        table.put("sense_hazard", spirit -> params -> new HazardScript(spirit, PerceptionParams.hazards(params)));
        table.put("locate_block", spirit -> params -> new LocateScript(spirit, LocateParams.block(params)));
        table.put("locate_tree", spirit -> params -> new LocateScript(spirit, LocateParams.tree(params)));
        table.put("locate_container", spirit -> params -> new LocateScript(spirit, LocateParams.container(params)));
        table.put("collect_drops", spirit -> params -> new CollectDropsScript(spirit, CollectDropsParams.from(params)));
        table.put("goto", spirit -> params -> new GotoScript(spirit, GotoParams.from(params)));
        table.put("chop_tree", spirit -> params -> new ChopTreeScript(spirit, ChopTreeParams.from(params)));
        table.put("follow", spirit -> params -> new FollowScript(spirit, FollowParams.from(params)));
        table.put("mine_vein", spirit -> params -> new MineVeinScript(spirit, MineVeinParams.from(params)));
        table.put("gather", spirit -> params -> new GatherScript(spirit, GatherParams.from(params)));
        table.put("till", spirit -> params -> new TillScript(spirit, TillParams.from(params)));
        table.put("plant_crop", spirit -> params -> new PlantCropScript(spirit, PlantCropParams.from(params)));
        table.put("harvest_crop", spirit -> params -> new HarvestCropScript(spirit, HarvestCropParams.from(params)));
        table.put("bone_meal", spirit -> params -> new BoneMealScript(spirit, BoneMealParams.from(params)));
        table.put("craft", spirit -> params -> new CraftScript(spirit, CraftParams.from(params)));
        table.put("smelt", spirit -> params -> new SmeltScript(spirit, SmeltParams.from(params)));
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
