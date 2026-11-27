package com.theminespirit.neoforge;

import com.theminespirit.core.building.BuildingType;
import com.theminespirit.core.building.MineLayout;
import com.theminespirit.core.building.Preset;
import com.theminespirit.core.building.api.BuildRotation;
import com.theminespirit.core.engine.ScriptInstance;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.reload.DataTables;
import com.theminespirit.core.script.BlockPointParams;
import com.theminespirit.core.script.BuildFarmParams;
import com.theminespirit.core.script.BuildFenceParams;
import com.theminespirit.core.script.DigDescentParams;
import com.theminespirit.core.script.FillCraterParams;
import com.theminespirit.core.script.FillParams;
import com.theminespirit.core.script.LightAreaParams;
import com.theminespirit.core.script.PlaceBlockParams;
import com.theminespirit.core.script.api.BuildSchematicParams;
import com.theminespirit.neoforge.blocks.OpenScript;
import com.theminespirit.neoforge.blocks.UseBlockScript;
import com.theminespirit.neoforge.crater.CraterResolver;
import com.theminespirit.neoforge.crater.FillCraterScript;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

final class BuildingScriptPack extends SpiritScriptPack {
    @Override
    public String id() {
        return "building";
    }

    @Override
    protected void register() {
        put("place_block", spirit -> params -> new PlaceBlockScript(spirit, PlaceBlockParams.from(params)));
        put("break_block", spirit -> params -> breakBlock(spirit, params));
        put("light_area", spirit -> params -> new LightAreaScript(spirit, LightAreaParams.from(params)));
        PortalRegistrations.register(this);
        put("fill", spirit -> params -> new FillScript(spirit, FillParams.from(params)));
        put(
                "fill_crater",
                spirit -> params ->
                        new FillCraterScript(spirit, CraterResolver.resolve(spirit, FillCraterParams.from(params))));
        put("clear_area", spirit -> params -> new ClearAreaScript(spirit, params));
        put(
                "place_descent_stairs",
                spirit -> params -> new PlaceDescentStairsScript(spirit, DigDescentParams.from(params)));
        put("build_schematic", spirit -> params -> buildSchematic(spirit, BuildSchematicParams.from(params)));
        put("audit_group", spirit -> params -> new AuditGroupScript(spirit, params));
        put("build_farm", spirit -> params -> new BuildFarmScript(spirit, BuildFarmParams.from(params)));
        put("build_fence", spirit -> params -> new BuildFenceScript(spirit, BuildFenceParams.from(params)));
        put("build_shelter", spirit -> params -> new BuildShelterScript(spirit));
        put("use_block", spirit -> params -> new UseBlockScript(spirit, BlockPointParams.from(params)));
        put("open", spirit -> params -> new OpenScript(spirit, BlockPointParams.from(params)));
    }

    private static ScriptInstance breakBlock(SpiritEntity spirit, Map<String, Object> params) {
        Map<String, Object> single = new LinkedHashMap<>(params);
        Object x = single.remove("x");
        Object y = single.remove("y");
        Object z = single.remove("z");
        single.put("x1", x);
        single.put("y1", y);
        single.put("z1", z);
        single.put("x2", x);
        single.put("y2", y);
        single.put("z2", z);
        return new ClearAreaScript(spirit, single);
    }

    private static ScriptInstance buildSchematic(SpiritEntity spirit, BuildSchematicParams params) {
        Optional<Preset> preset = DataTables.INSTANCE.presets().current().byId(params.file());
        BuildRotation rotation = Objects.requireNonNullElse(params.rotation(), BuildRotation.NONE);
        if (preset.isPresent() && preset.get().type() == BuildingType.UNDERGROUND) {
            return MineBuildScript.of(
                    spirit,
                    preset.get(),
                    new BlockCoord(params.x(), params.y(), params.z()),
                    rotation,
                    new MineLayout.Extent(
                            null,
                            params.segments() == BuildSchematicParams.SEGMENTS_FROM_FILE ? null : params.segments()));
        }
        return new BuildSchematicScript(spirit, params);
    }
}
