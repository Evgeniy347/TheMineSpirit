package com.theminespirit.neoforge;

import net.minecraft.core.BlockPos;

final class FillApproachState {
    private final FillSession host;

    FillApproachState(FillSession host) {
        this.host = host;
    }

    void tick() {
        BlockPos feet = host.spirit.blockPosition();
        if (host.currentStand != null
                && feet.getX() == host.currentStand.x()
                && feet.getZ() == host.currentStand.z()
                && Math.abs(feet.getY() - host.currentStand.y()) <= 1
                && (host.spirit.onGround() || host.spirit.isInWater())) {
            host.approach.cancel();
            host.placeCurrent();
            return;
        }
        if (host.current != null && host.standView.inReach(host.current)) {
            host.approach.cancel();
            host.placeCurrent();
            return;
        }
        switch (host.approach.tick()) {
            case MOVING -> {}
            case ARRIVED -> host.placeCurrent();
            case STUCK, TIMEOUT -> {
                host.tally.noStand();
                host.tally.skipped();
                host.brokenY = Math.min(host.brokenY, host.current == null ? Integer.MAX_VALUE : host.current.y());
                host.releaseAndAdvance();
                host.state = FillSession.State.PICK;
            }
        }
    }
}
