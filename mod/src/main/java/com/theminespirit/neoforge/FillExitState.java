package com.theminespirit.neoforge;

import com.theminespirit.core.building.BuildTuning;
import com.theminespirit.core.observation.BlockCoord;
import net.minecraft.world.phys.Vec3;

final class FillExitState {
    private final FillSession host;

    FillExitState(FillSession host) {
        this.host = host;
    }

    void tick() {
        BlockCoord exit = host.standView.exitStand();
        if (exit == null) {
            host.state = FillSession.State.PICK;
            return;
        }
        if (!host.approach.isActive()) {
            host.approach.begin(new Vec3(exit.x() + 0.5, exit.y(), exit.z() + 0.5), BuildTuning.STAND_ARRIVAL);
        }
        switch (host.approach.tick()) {
            case MOVING -> {}
            case ARRIVED -> host.state = FillSession.State.PICK;
            case STUCK, TIMEOUT -> host.state = FillSession.State.PICK;
        }
    }
}
