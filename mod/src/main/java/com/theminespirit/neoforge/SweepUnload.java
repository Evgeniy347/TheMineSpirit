package com.theminespirit.neoforge;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.script.api.CatalogLimits;
import java.util.List;
import org.slf4j.Logger;

final class SweepUnload {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final ClearAreaSession session;
    private HarvestDeposit deposit;
    private boolean tried;

    SweepUnload(ClearAreaSession session) {
        this.session = session;
    }

    boolean tryStart() {
        if (tried
                || PillarDownScript.ownBelow(session.spirit)
                || HarvestDeposit.occupiedSlots(session.spirit) < CatalogLimits.UNLOAD_TRIGGER_SLOTS) {
            return false;
        }
        deposit = new HarvestDeposit(session.spirit, List.of(), false, -1);
        session.state = ClearAreaSession.State.UNLOAD;
        return true;
    }

    void reset() {
        tried = false;
    }

    void tick() {
        HarvestDeposit current = deposit;
        if (current == null) {
            session.state = ClearAreaSession.State.PICK;
            return;
        }
        switch (current.tick()) {
            case RUNNING -> {}
            case PICK -> {
                deposit = null;
                tried = true;
                session.state = ClearAreaSession.State.PICK;
            }
            case FAILED -> {
                LOGGER.info(
                        "[clear] {}: {} — продолжаю с полным инвентарём",
                        session.spirit.getName().getString(),
                        current.failureReason());
                deposit = null;
                tried = true;
                session.state = ClearAreaSession.State.PICK;
            }
        }
    }
}
