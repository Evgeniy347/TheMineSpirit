package com.theminespirit.core.coordinator;

import com.theminespirit.core.tactics.Point;
import java.util.UUID;

public record HelpCall(UUID caller, Point pos, long raisedAt) {
    public HelpCall {
        if (caller == null || pos == null) {
            throw new IllegalArgumentException(
                    "HelpCall: caller и pos обязательны — зов без духа или места " + "не привести помощь");
        }
        if (raisedAt < 0) {
            throw new IllegalArgumentException(
                    "HelpCall.raisedAt = " + raisedAt + ": тик зова не может быть отрицательным");
        }
    }
}
