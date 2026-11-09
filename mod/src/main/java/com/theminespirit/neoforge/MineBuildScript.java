package com.theminespirit.neoforge;

import com.theminespirit.core.building.BuildRotation;
import com.theminespirit.core.building.MineLayout;
import com.theminespirit.core.building.Preset;
import com.theminespirit.core.building.Schematic;
import com.theminespirit.core.engine.ScriptInstance;
import com.theminespirit.core.engine.ScriptResult;
import com.theminespirit.core.engine.ScriptStatus;
import com.theminespirit.core.observation.BlockCoord;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

final class MineBuildScript implements ScriptInstance {
    private final MineBuildRun run;

    MineBuildScript(SpiritEntity spirit, MineLayout layout, BuildRotation rotation) {
        this.run = new MineBuildRun(spirit, layout, rotation);
    }

    static MineBuildScript of(
            SpiritEntity spirit, Preset preset, BlockCoord origin, BuildRotation rotation, MineLayout.Extent extent) {
        Schematic schematic = preset.schematic();
        return new MineBuildScript(
                spirit, MineLayout.place(schematic, preset.entry(), origin, rotation, extent), rotation);
    }

    @Override
    public ScriptStatus tick() {
        return run.tick();
    }

    @Override
    public Optional<Progress> progress() {
        return Optional.of(run.progress());
    }

    @Override
    public Optional<ScriptResult> result() {
        return run.result();
    }

    @Override
    public void cancel() {
        run.cancel();
    }

    @Override
    public void suspend() {
        run.suspend();
    }

    @Override
    public @Nullable String failureReason() {
        return run.failureReason();
    }
}
