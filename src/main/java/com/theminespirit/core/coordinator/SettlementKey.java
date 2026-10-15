package com.theminespirit.core.coordinator;

import com.theminespirit.core.observation.BlockCoord;
import java.util.UUID;

public sealed interface SettlementKey permits SettlementKey.Homed, SettlementKey.Lone {
    static SettlementKey homed(BlockCoord home) {
        return new Homed(home);
    }

    static SettlementKey lone(UUID spirit) {
        return new Lone(spirit);
    }

    record Homed(BlockCoord home) implements SettlementKey {
        public Homed {
            if (home == null) {
                throw new IllegalArgumentException("home не может быть null: дом группы задаёт поселение, "
                        + "дух без дома — SettlementKey.lone, иначе поселения склеятся");
            }
        }
    }

    record Lone(UUID spirit) implements SettlementKey {
        public Lone {
            if (spirit == null) {
                throw new IllegalArgumentException("spirit не может быть null: одиночная группа привязана к духу, "
                        + "иначе её не отличить от чужой группы");
            }
        }
    }
}
