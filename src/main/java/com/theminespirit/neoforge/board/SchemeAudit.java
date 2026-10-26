package com.theminespirit.neoforge.board;

import com.theminespirit.core.building.BuildRotation;
import com.theminespirit.core.building.BuildingType;
import com.theminespirit.core.building.Schematic;
import com.theminespirit.core.building.SchematicBlocks;
import com.theminespirit.core.building.SchemeFilter;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.reload.DataTables;
import com.theminespirit.core.script.BuildSchematicParams;
import com.theminespirit.neoforge.SchematicResources;
import java.util.List;
import java.util.Optional;

public final class SchemeAudit {
    private SchemeAudit() {}

    public static Optional<List<Schematic.PlacedEntry>> entries(ScriptCall call) {
        if (!"build_schematic".equals(call.script())
                || !(call.params().get("file") instanceof String file)
                || !(call.params().get("x") instanceof Integer x)
                || !(call.params().get("y") instanceof Integer y)
                || !(call.params().get("z") instanceof Integer z)) {
            return Optional.empty();
        }
        Optional<BuildingType> type =
                DataTables.INSTANCE.presets().current().byId(file).map(preset -> preset.type());
        if (type.isEmpty() || type.get() == BuildingType.UNDERGROUND) {
            return Optional.empty();
        }
        BuildRotation rotation =
                call.params().get("rotation") instanceof String turn ? BuildRotation.parse(turn) : BuildRotation.NONE;
        List<String> only = call.params().get(BuildSchematicParams.ONLY) instanceof String blocks && !blocks.isBlank()
                ? List.of(blocks.split(","))
                : List.of();
        return Optional.of(
                SchemeFilter.only(SchematicResources.load(file), only)
                        .entries(new BlockCoord(x, y, z), rotation)
                        .stream()
                        .filter(entry ->
                                !entry.optional() && !SchematicBlocks.isSecondaryPart(entry.block(), entry.state()))
                        .toList());
    }
}
