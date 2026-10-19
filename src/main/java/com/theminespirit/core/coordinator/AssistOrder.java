package com.theminespirit.core.coordinator;

import java.util.UUID;

public record AssistOrder(UUID helper, UUID ward, boolean urgent) {
    public AssistOrder {
        if (helper == null || ward == null) {
            throw new IllegalArgumentException("AssistOrder: helper и ward обязательны — приказ без помощника "
                    + "или без подопечного не исполним");
        }
        if (helper.equals(ward)) {
            throw new IllegalArgumentException(
                    "AssistOrder: дух " + helper + " не может помогать сам себе — в помощники выбирается другой дух");
        }
    }

    public AssistOrder(UUID helper, UUID ward) {
        this(helper, ward, false);
    }
}
