package com.theminespirit.core.policy;

import com.theminespirit.core.tactics.Layer;
import com.theminespirit.core.tactics.LayerOrder;
import java.util.Objects;

public final class LayerArbiter {
    public static final int RESUME_DELAY_TICKS = 10;

    private Layer owner = Layer.IDLE;
    private int quietTicks;

    public Layer step(PolicyVerdict verdict, boolean tacticsWantControl, boolean hasTask) {
        Objects.requireNonNull(verdict, "Вердикт политик не может быть null: Clear задаётся new PolicyVerdict.Clear()");
        Layer demanded = demanded(verdict, tacticsWantControl, hasTask);
        if (!LayerOrder.outranks(owner, demanded)) {
            owner = demanded;
            quietTicks = 0;
            return owner;
        }
        if (owner == Layer.STRATEGY) {
            owner = demanded;
            quietTicks = 0;
            return owner;
        }
        quietTicks++;
        if (quietTicks >= RESUME_DELAY_TICKS) {
            owner = demanded;
            quietTicks = 0;
        }
        return owner;
    }

    public Layer owner() {
        return owner;
    }

    private static Layer demanded(PolicyVerdict verdict, boolean tacticsWantControl, boolean hasTask) {
        if (verdict instanceof PolicyVerdict.Reflex) {
            return Layer.POLICY;
        }
        if (tacticsWantControl) {
            return Layer.TACTICS;
        }
        return hasTask ? Layer.STRATEGY : Layer.IDLE;
    }
}
