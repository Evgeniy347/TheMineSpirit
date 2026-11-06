package com.theminespirit.neoforge;

import com.theminespirit.core.engine.ScriptStatus;
import com.theminespirit.core.inventory.Stack;
import com.theminespirit.neoforge.inventory.UnloadScript;
import java.util.List;

final class HarvestDeposit {
    enum Step {
        RUNNING,
        PICK,
        FAILED
    }

    private final UnloadScript script;
    private final boolean mandatory;
    private final String blockedItem;
    private boolean freed;

    HarvestDeposit(SpiritEntity spirit, List<Stack> incoming, boolean mandatory, int toolSlot) {
        this.script = UnloadScript.relief(spirit, toolSlot);
        this.mandatory = mandatory;
        this.blockedItem = incoming.isEmpty() ? null : incoming.get(0).item();
    }

    Step tick() {
        ScriptStatus status = script.tick();
        if (status == ScriptStatus.RUNNING) {
            return Step.RUNNING;
        }
        boolean moved = script.movedTotal() > 0;
        if (status == ScriptStatus.SUCCEEDED && (moved || !mandatory)) {
            freed = moved;
            return Step.PICK;
        }
        if (!mandatory) {
            return Step.PICK;
        }
        return Step.FAILED;
    }

    boolean freed() {
        return freed;
    }

    String failureReason() {
        return blockedItem == null
                ? "инвентарь полон: не помещается добыча"
                : "инвентарь полон: не помещается " + blockedItem;
    }

    void cancel() {
        script.cancel();
    }

    void suspend() {
        script.suspend();
    }

    static int occupiedSlots(SpiritEntity spirit) {
        int occupied = 0;
        for (int slot = 0; slot < spirit.getInventory().getContainerSize(); slot++) {
            if (!spirit.getInventory().getItem(slot).isEmpty()) {
                occupied++;
            }
        }
        return occupied;
    }
}
