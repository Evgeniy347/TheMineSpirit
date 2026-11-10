package com.theminespirit.core.script;

import java.util.ArrayList;
import java.util.List;

public final class CoreScripts {

    private CoreScripts() {}

    public static ScriptCatalog defaultCatalog() {
        List<ScriptSpec> specs = new ArrayList<>();
        specs.addAll(SenseScriptSpecs.perceptionSpecs());
        specs.addAll(SenseScriptSpecs.memorySpecs());
        specs.addAll(TravelScriptSpecs.travelSpecs());
        specs.addAll(TravelScriptSpecs.navigationSpecs());
        specs.addAll(TerrainScriptSpecs.terrainSpecs());
        specs.addAll(TerrainScriptSpecs.waterAndFleeSpecs());
        specs.addAll(ResourceScriptSpecs.forestryAndMiningSpecs());
        specs.addAll(ResourceScriptSpecs.descentSpecs());
        specs.addAll(GatheringSpecs.all());
        specs.addAll(FarmScriptSpecs.cropCareSpecs());
        specs.addAll(FarmScriptSpecs.breedingSpecs());
        specs.addAll(AnimalProduceSpecs.all());
        specs.addAll(PortalScripts.all());
        specs.addAll(HuntScriptSpecs.huntingSpecs());
        specs.addAll(HuntScriptSpecs.fishingSpecs());
        specs.addAll(HandsScriptSpecs.itemSpecs());
        specs.add(ItemScriptSpecs.acquireAt());
        specs.addAll(HandsScriptSpecs.combatAndClimbSpecs());
        specs.addAll(BuildScriptSpecs.placementSpecs());
        specs.addAll(BuildScriptSpecs.fillAndClearSpecs());
        specs.addAll(BuildScriptSpecs.buildSpecs());
        specs.addAll(BuildScriptSpecs.structureSpecs());
        specs.addAll(SocialScriptSpecs.socialSpecs());
        specs.addAll(SocialScriptSpecs.campSpecs());
        specs.addAll(SocialScriptSpecs.interactionSpecs());
        specs.addAll(SocialScriptSpecs.exchangeSpecs());
        specs.addAll(BrewScriptSpecs.all());
        return ScriptCatalog.of(specs);
    }
}
