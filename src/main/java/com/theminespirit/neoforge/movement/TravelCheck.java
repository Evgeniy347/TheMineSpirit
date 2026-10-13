package com.theminespirit.neoforge.movement;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.policy.BlockProtection;
import com.theminespirit.neoforge.SpiritEntity;

public final class TravelCheck {
    private TravelCheck() {
    }

    public static String refusal(SpiritEntity spirit, BlockCoord goal) {
        if (spirit.protectionGate().checkTravel(goal) instanceof BlockProtection.Refused refused) {
            return refused.reason();
        }
        if (spirit.protectionGate().checkPortalTarget(goal) instanceof BlockProtection.Refused refused) {
            return refused.reason();
        }
        return null;
    }
}
