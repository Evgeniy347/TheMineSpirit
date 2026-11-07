package com.theminespirit.core.board;

import com.theminespirit.core.building.BuildRotation;
import com.theminespirit.core.building.Preset;
import com.theminespirit.core.building.PresetLibrary;
import com.theminespirit.core.building.Schematic;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class ExpeditionMinePlan {
    public static final String SCHEME = "mine_tunnel_v1";
    public static final String ROTATION = "counterclockwise_90";
    public static final int ORE_COUNT = 8;
    public static final int ORE_RADIUS = 16;
    public static final String RETURN_HOME = "return_home";

    private ExpeditionMinePlan() {}

    public static TaskNode plan(BlockCoord mineBottom) {
        Objects.requireNonNull(mineBottom, "Низ шахты не может быть null: поход начинается со спуска");
        BlockCoord origin = tunnelOrigin(mineBottom);
        return new SequenceNode(List.of(
                new ScriptCall("goto", Map.of("x", mineBottom.x(), "y", mineBottom.y(), "z", mineBottom.z())),
                new ScriptCall(
                        "build_schematic",
                        Map.of(
                                "file",
                                SCHEME,
                                "x",
                                origin.x(),
                                "y",
                                origin.y(),
                                "z",
                                origin.z(),
                                "rotation",
                                ROTATION)),
                new ScriptCall("mine_vein", Map.of("ore", "any", "count", ORE_COUNT, "radius", ORE_RADIUS)),
                new ScriptCall(RETURN_HOME, Map.of())));
    }

    public static BlockCoord tunnelOrigin(BlockCoord mineBottom) {
        Objects.requireNonNull(mineBottom, "Низ шахты не может быть null: от него считается начало хода");
        Preset preset = PresetLibrary.current()
                .byId(SCHEME)
                .orElseThrow(() -> new IllegalStateException(
                        "Нет схемы '" + SCHEME + "': шахтный ход не по чему строить, поход в шахту запрещён"));
        Schematic schematic = preset.schematic();
        BlockCoord entry = preset.entry();
        BuildRotation.Cell cell =
                BuildRotation.parse(ROTATION).rotate(entry.x(), entry.z(), schematic.sizeX(), schematic.sizeZ());
        return new BlockCoord(mineBottom.x() - cell.x(), mineBottom.y() - entry.y(), mineBottom.z() - cell.z());
    }
}
