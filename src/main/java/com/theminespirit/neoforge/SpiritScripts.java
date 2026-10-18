package com.theminespirit.neoforge;

import com.theminespirit.core.engine.ConditionRegistry;
import com.theminespirit.core.engine.ScriptFactory;
import com.theminespirit.core.engine.ScriptInstance;
import com.theminespirit.core.engine.ScriptRegistry;
import com.theminespirit.core.planner.AcquireScript;
import com.theminespirit.core.script.AcquireParams;
import com.theminespirit.core.script.ApproachParams;
import com.theminespirit.core.script.AttackParams;
import com.theminespirit.core.script.DefendParams;
import com.theminespirit.core.script.BoneMealParams;
import com.theminespirit.core.script.BreedParams;
import com.theminespirit.core.script.BuildSchematicParams;
import com.theminespirit.core.script.CaptureParams;
import com.theminespirit.core.script.CatalogLimits;
import com.theminespirit.core.script.ClearCampParams;
import com.theminespirit.core.script.SetCampParams;
import com.theminespirit.core.script.ChopTreeParams;
import com.theminespirit.core.script.ChopTargetParams;
import com.theminespirit.core.script.ClearAreaParams;
import com.theminespirit.core.script.BlockPointParams;
import com.theminespirit.core.script.ComeToPlayerParams;
import com.theminespirit.core.script.DigAreaParams;
import com.theminespirit.core.script.DigDescentParams;
import com.theminespirit.core.script.FillCraterParams;
import com.theminespirit.core.script.FillParams;
import com.theminespirit.core.script.FishingParams;
import com.theminespirit.core.script.PlaceBlockParams;
import com.theminespirit.core.script.PillarUpParams;
import com.theminespirit.core.script.CollectDropsParams;
import com.theminespirit.core.script.CollectEggsParams;
import com.theminespirit.core.script.CraftParams;
import com.theminespirit.core.script.CullParams;
import com.theminespirit.core.script.ConditionCatalog;
import com.theminespirit.core.script.CoreScripts;
import com.theminespirit.core.script.DeliverParams;
import com.theminespirit.core.script.EatParams;
import com.theminespirit.core.script.EquipParams;
import com.theminespirit.core.script.FleeParams;
import com.theminespirit.core.script.EscortParams;
import com.theminespirit.core.script.ExploreParams;
import com.theminespirit.core.script.FollowParams;
import com.theminespirit.core.script.GatherParams;
import com.theminespirit.core.script.GotoBlockParams;
import com.theminespirit.core.script.GotoParams;
import com.theminespirit.core.script.KeepDistanceParams;
import com.theminespirit.core.script.HarvestCropParams;
import com.theminespirit.core.script.LocateParams;
import com.theminespirit.core.script.LookAtParams;
import com.theminespirit.core.script.MemoryParams;
import com.theminespirit.core.script.MeetParams;
import com.theminespirit.core.script.HerdCheckParams;
import com.theminespirit.core.script.HuntParams;
import com.theminespirit.core.script.LightAreaParams;
import com.theminespirit.core.script.MilkParams;
import com.theminespirit.core.script.MineVeinParams;
import com.theminespirit.core.script.PatrolParams;
import com.theminespirit.core.script.PerceptionParams;
import com.theminespirit.core.script.PlantCropParams;
import com.theminespirit.core.script.RetreatParams;
import com.theminespirit.core.script.RepairParams;
import com.theminespirit.core.script.ScanParams;
import com.theminespirit.core.script.SayParams;
import com.theminespirit.core.script.ShearParams;
import com.theminespirit.core.script.SmeltParams;
import com.theminespirit.core.script.ScriptCatalog;
import com.theminespirit.core.script.SortChestParams;
import com.theminespirit.core.script.SetCombatModeParams;
import com.theminespirit.core.script.StepOverParams;
import com.theminespirit.core.script.TillParams;
import com.theminespirit.core.script.FeedParams;
import com.theminespirit.neoforge.combat.AttackScript;
import com.theminespirit.neoforge.combat.DefendScript;
import com.theminespirit.neoforge.combat.FleeScript;
import com.theminespirit.neoforge.combat.RetreatScript;
import com.theminespirit.neoforge.combat.SetCombatModeScript;
import com.theminespirit.neoforge.blocks.OpenScript;
import com.theminespirit.neoforge.blocks.UseBlockScript;
import com.theminespirit.neoforge.crafting.CraftScript;
import com.theminespirit.neoforge.crafting.RecipeKnowledge;
import com.theminespirit.neoforge.crafting.SmeltScript;
import com.theminespirit.neoforge.doors.CloseDoorScript;
import com.theminespirit.neoforge.husbandry.BreedScript;
import com.theminespirit.neoforge.husbandry.CaptureScript;
import com.theminespirit.neoforge.husbandry.CollectEggsScript;
import com.theminespirit.neoforge.husbandry.CullScript;
import com.theminespirit.neoforge.husbandry.FeedScript;
import com.theminespirit.neoforge.husbandry.HerdCheckScript;
import com.theminespirit.neoforge.husbandry.HuntScript;
import com.theminespirit.neoforge.husbandry.MilkScript;
import com.theminespirit.neoforge.husbandry.ShearScript;
import com.theminespirit.neoforge.hunger.EatScript;
import com.theminespirit.core.script.TransferParams;
import com.theminespirit.core.script.UnloadParams;
import com.theminespirit.core.script.WaitParams;
import com.theminespirit.core.script.WalkCarefulParams;
import com.theminespirit.core.script.WanderParams;
import com.theminespirit.neoforge.inventory.DepositScript;
import com.theminespirit.neoforge.inventory.EquipScript;
import com.theminespirit.neoforge.inventory.SortChestScript;
import com.theminespirit.neoforge.inventory.SpiritSupplies;
import com.theminespirit.neoforge.inventory.UnloadScript;
import com.theminespirit.neoforge.crater.CraterResolver;
import com.theminespirit.neoforge.crater.FillCraterScript;
import com.theminespirit.neoforge.inventory.WithdrawScript;
import com.theminespirit.neoforge.repair.RepairScript;
import com.theminespirit.neoforge.movement.ApproachScript;
import com.theminespirit.neoforge.movement.ComeToPlayerScript;
import com.theminespirit.neoforge.movement.EscortScript;
import com.theminespirit.neoforge.movement.ExploreScript;
import com.theminespirit.neoforge.movement.GotoBlockScript;
import com.theminespirit.neoforge.movement.KeepDistanceScript;
import com.theminespirit.neoforge.movement.MeetScript;
import com.theminespirit.neoforge.movement.PatrolScript;
import com.theminespirit.neoforge.movement.ReturnHomeScript;
import com.theminespirit.neoforge.movement.StepOverScript;
import com.theminespirit.neoforge.movement.WanderScript;
import com.theminespirit.neoforge.social.DeliverScript;
import com.theminespirit.neoforge.social.SayScript;
import com.theminespirit.neoforge.social.WaitScript;
import com.theminespirit.neoforge.sleep.SleepScript;import java.util.LinkedHashMap;
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
        table.put("look_at", spirit -> params -> new LookAtScript(spirit, LookAtParams.target(params)));
        table.put("remember", spirit -> params -> new MemoryScript(spirit, MemoryParams.remember(params)));
        table.put("recall", spirit -> params -> new MemoryScript(spirit, MemoryParams.recall(params)));
        table.put("recall_all", spirit -> params -> new MemoryScript(spirit, MemoryParams.recallAll(params)));
        table.put("forget", spirit -> params -> new MemoryScript(spirit, MemoryParams.forget(params)));
        table.put("collect_drops", spirit -> params -> new CollectDropsScript(spirit, CollectDropsParams.from(params)));
        table.put("goto", spirit -> params -> new GotoScript(spirit, GotoParams.from(params)));
        table.put("chop_tree", spirit -> params -> new ChopTreeScript(spirit, ChopTreeParams.from(params)));
        table.put("chop_target", spirit -> params -> new ChopTargetScript(spirit, ChopTargetParams.from(params)));
        table.put("follow", spirit -> params -> new FollowScript(spirit, FollowParams.from(params)));
        table.put("escort", spirit -> params -> new EscortScript(spirit, EscortParams.from(params)));
        table.put("return_home", spirit -> params -> new ReturnHomeScript(spirit));
        table.put("set_camp", spirit -> params -> new SetCampScript(spirit, SetCampParams.from(params)));
        table.put("clear_camp", spirit -> params -> new ClearCampScript(spirit, ClearCampParams.from(params)));
        table.put("goto_block", spirit -> params -> new GotoBlockScript(spirit, GotoBlockParams.from(params)));
        table.put("come_to_player", spirit -> params -> new ComeToPlayerScript(spirit, ComeToPlayerParams.from(params)));
        table.put("patrol", spirit -> params -> new PatrolScript(spirit, PatrolParams.from(params)));
        table.put("wander", spirit -> params -> new WanderScript(spirit, WanderParams.from(params)));
        table.put("explore", spirit -> params -> new ExploreScript(spirit, ExploreParams.from(params)));
        table.put("keep_distance", spirit -> params -> new KeepDistanceScript(spirit, KeepDistanceParams.from(params)));
        table.put("walk_careful", spirit -> params -> GotoScript.careful(spirit, WalkCarefulParams.from(params)));
        table.put("step_over", spirit -> params -> new StepOverScript(spirit, StepOverParams.from(params)));
        table.put("approach", spirit -> params -> new ApproachScript(spirit, ApproachParams.from(params)));
        table.put("mine_vein", spirit -> params -> new MineVeinScript(spirit, MineVeinParams.from(params)));
        table.put("attack", spirit -> observing(params -> new AttackScript(spirit, AttackParams.from(params))));
        table.put("defend", spirit -> observing(params -> new DefendScript(spirit, DefendParams.from(params))));
        table.put("flee", spirit -> observing(params -> new FleeScript(spirit, FleeParams.from(params))));
        table.put("retreat", spirit -> observing(params -> new RetreatScript(spirit, RetreatParams.from(params))));
        table.put("deliver", spirit -> params -> new DeliverScript(spirit, DeliverParams.from(params)));
        table.put("meet", spirit -> params -> new MeetScript(spirit, MeetParams.from(params)));
        table.put("wait", spirit -> params -> new WaitScript(spirit, WaitParams.from(params)));
        table.put("sleep", spirit -> observing(params -> new SleepScript(spirit)));
        table.put("say", spirit -> params -> new SayScript(spirit, SayParams.from(params)));
        table.put("gather", spirit -> params -> new GatherScript(spirit, GatherParams.from(params)));
        table.put("till", spirit -> params -> new TillScript(spirit, TillParams.from(params)));
        table.put("plant_crop", spirit -> params -> new PlantCropScript(spirit, PlantCropParams.from(params)));
        table.put("harvest_crop", spirit -> params -> new HarvestCropScript(spirit, HarvestCropParams.from(params)));
        table.put("bone_meal", spirit -> params -> new BoneMealScript(spirit, BoneMealParams.from(params)));
        table.put("craft", spirit -> params -> new CraftScript(spirit, CraftParams.from(params)));
        table.put("smelt", spirit -> params -> new SmeltScript(spirit, SmeltParams.from(params)));
        table.put("breed", spirit -> params -> new BreedScript(spirit, BreedParams.from(params)));
        table.put("feed", spirit -> params -> new FeedScript(spirit, FeedParams.from(params)));
        table.put("shear", spirit -> params -> new ShearScript(spirit, ShearParams.from(params)));
        table.put("milk", spirit -> params -> new MilkScript(spirit, MilkParams.from(params)));
        table.put("collect_eggs", spirit -> params -> new CollectEggsScript(spirit, CollectEggsParams.from(params)));
        table.put("cull", spirit -> params -> new CullScript(spirit, CullParams.from(params)));
        table.put("herd_check", spirit -> params -> new HerdCheckScript(spirit, HerdCheckParams.from(params)));
        table.put("hunt", spirit -> params -> new HuntScript(spirit, HuntParams.from(params)));
        table.put("capture", spirit -> params -> new CaptureScript(spirit, CaptureParams.from(params)));
        table.put("fish", spirit -> params -> new FishScript(spirit, FishingParams.from(params)));
        table.put("deposit", spirit -> params -> new DepositScript(spirit, TransferParams.from(params)));
        table.put("withdraw", spirit -> params -> new WithdrawScript(spirit, TransferParams.from(params)));
        table.put("eat", spirit -> params -> new EatScript(spirit, EatParams.from(params)));
        table.put("sort_chest", spirit -> params -> new SortChestScript(spirit, SortChestParams.from(params)));
        table.put("unload", spirit -> params -> new UnloadScript(spirit, UnloadParams.from(params)));
        table.put("equip", spirit -> params -> new EquipScript(spirit, EquipParams.from(params)));
        table.put("repair", spirit -> params -> new RepairScript(spirit, RepairParams.from(params)));
        table.put("place_block", spirit -> params -> new PlaceBlockScript(spirit, PlaceBlockParams.from(params)));
        table.put("pillar_up", spirit -> params -> new PillarUpScript(spirit, PillarUpParams.from(params)));
        table.put("pillar_down", spirit -> params -> new PillarDownScript(spirit));
        table.put("light_area", spirit -> params -> new LightAreaScript(spirit, LightAreaParams.from(params)));
        table.put("fill", spirit -> params -> new FillScript(spirit, FillParams.from(params)));
        table.put("fill_crater", spirit -> params -> new FillCraterScript(spirit,
                CraterResolver.resolve(spirit, FillCraterParams.from(params))));
        table.put("clear_area", spirit -> params -> new ClearAreaScript(spirit, ClearAreaParams.from(params)));
        table.put("dig_area", spirit -> params -> new DigAreaScript(spirit, DigAreaParams.from(params)));
        table.put("dig_descent", spirit -> params -> new DescentScript(spirit, DigDescentParams.from(params)));
        table.put("dig_descent_widen",
                spirit -> params -> new DigDescentWidenScript(spirit, DigDescentParams.from(params)));
        table.put("place_descent_stairs",
                spirit -> params -> new PlaceDescentStairsScript(spirit, DigDescentParams.from(params)));
        table.put("build_schematic",
                spirit -> params -> new BuildSchematicScript(spirit, BuildSchematicParams.from(params)));
        table.put("close_door", spirit -> params -> new CloseDoorScript(spirit, BlockPointParams.from(params)));
        table.put("use_block", spirit -> params -> new UseBlockScript(spirit, BlockPointParams.from(params)));
        table.put("open", spirit -> params -> new OpenScript(spirit, BlockPointParams.from(params)));
        table.put("set_combat_mode", spirit -> params -> new SetCombatModeScript(spirit, SetCombatModeParams.from(params)));
        table.put("acquire", spirit -> params -> {
            AcquireParams parsed = AcquireParams.from(params);
            return new AcquireScript(parsed.item(), parsed.count(), RecipeKnowledge.of(spirit.level()),
                    new SpiritSupplies(spirit, CatalogLimits.CONTAINER_RADIUS_DEFAULT),
                    catalog(), conditionCatalog(), () -> registryFor(spirit), conditions());
        });
        return table;
    }

    public static ScriptRegistry registryFor(SpiritEntity spirit) {
        Map<String, ScriptFactory> factories = new LinkedHashMap<>();
        IMPLEMENTED.forEach((id, builder) -> factories.put(id, builder.apply(spirit)));
        return ScriptRegistry.of(factories);
    }

    private static ScriptFactory observing(ScriptFactory delegate) {
        return new ScriptFactory() {
            @Override
            public ScriptInstance create(Map<String, Object> params) {
                return delegate.create(params);
            }

            @Override
            public boolean observesWhileTacticsOwns() {
                return true;
            }
        };
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
