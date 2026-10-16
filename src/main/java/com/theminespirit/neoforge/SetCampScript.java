package com.theminespirit.neoforge;

import com.theminespirit.core.base.Camp;
import com.theminespirit.core.engine.ScriptInstance;
import com.theminespirit.core.engine.ScriptResult;
import com.theminespirit.core.engine.ScriptStatus;
import com.theminespirit.core.script.SetCampParams;
import com.theminespirit.core.tactics.Point;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;

final class SetCampScript implements ScriptInstance {
    private final SpiritEntity spirit;
    private final SetCampParams params;
    private String failureReason;
    private Camp published;

    SetCampScript(SpiritEntity spirit, SetCampParams params) {
        this.spirit = spirit;
        this.params = params;
    }

    @Override
    public ScriptStatus tick() {
        if (spirit.isRemoved()) {
            failureReason = "дух исчез, не успев поставить лагерь";
            return ScriptStatus.FAILED;
        }
        BlockPos feet = spirit.blockPosition();
        int x = params.x().orElse(feet.getX());
        int y = params.y().orElse(feet.getY());
        int z = params.z().orElse(feet.getZ());
        published = new Camp(new Point(x + 0.5, y, z + 0.5), params.radius(), 0, spirit.level().getGameTime());
        spirit.setCamp(published);
        return ScriptStatus.SUCCEEDED;
    }

    @Override
    public Optional<ScriptResult> result() {
        return published == null ? Optional.empty()
                : Optional.of(new ScriptResult.Data(Map.of("camp", true, "x", published.center().x(),
                        "y", published.center().y(), "z", published.center().z(), "radius", published.radius())));
    }

    @Override
    public String failureReason() {
        return failureReason;
    }
}
