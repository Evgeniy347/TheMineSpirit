package com.theminespirit.neoforge;

import com.theminespirit.core.script.BoneMealParams;
import com.theminespirit.core.script.GatherParams;
import com.theminespirit.core.script.HarvestCropParams;
import com.theminespirit.core.script.PlantCropParams;
import com.theminespirit.core.script.TillParams;

final class FarmingScriptPack extends SpiritScriptPack {
    @Override
    public String id() {
        return "farming";
    }

    @Override
    protected void register() {
        put("gather", spirit -> params -> new GatherScript(spirit, GatherParams.from(params)));
        put("till", spirit -> params -> new TillScript(spirit, TillParams.from(params)));
        put("plant_crop", spirit -> params -> new PlantCropScript(spirit, PlantCropParams.from(params)));
        put("harvest_crop", spirit -> params -> new HarvestCropScript(spirit, HarvestCropParams.from(params)));
        put("bone_meal", spirit -> params -> new BoneMealScript(spirit, BoneMealParams.from(params)));
    }
}
