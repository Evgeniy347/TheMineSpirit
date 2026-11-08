package com.theminespirit.neoforge;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

final class FillPillarState {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final FillSession host;

    FillPillarState(FillSession host) {
        this.host = host;
    }

    void tick() {
        switch (host.pillar.tick()) {
            case RUNNING -> {}
            case BUILT -> {
                host.passPlaced++;
                host.tally.placed(host.blockId());
                host.releaseAndAdvance();
                host.state = FillSession.State.PICK;
            }
            case FAILED -> {
                LOGGER.info(
                        "[fill] {}: столб в {} не вышел: {}",
                        host.spirit.getName().getString(),
                        host.current,
                        host.pillar.failureReason());
                host.tally.skipped();
                host.brokenY = Math.min(host.brokenY, host.current.y());
                host.releaseAndAdvance();
                host.state = FillSession.State.PICK;
            }
        }
    }
}
