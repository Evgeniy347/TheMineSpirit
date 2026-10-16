package com.theminespirit.core.policy;

import com.theminespirit.core.observation.BlockCoord;
import java.util.Objects;

public sealed interface ReflexAction {
    record Flee(BlockCoord cell) implements ReflexAction {
        public Flee {
            Objects.requireNonNull(cell, "Клетка бегства не может быть null: исполнителю некуда вести духа");
        }
    }

    record ExitWater(BlockCoord cell) implements ReflexAction {
        public ExitWater {
            Objects.requireNonNull(cell, "Клетка выхода из воды не может быть null: исполнителю некуда вести духа");
        }
    }

    record Surface() implements ReflexAction {
    }

    record StepOutOfPortal(BlockCoord cell) implements ReflexAction {
        public StepOutOfPortal {
            Objects.requireNonNull(cell, "Клетка вне портала не может быть null: исполнителю некуда вести духа");
        }
    }

    record ReturnToZone(BlockCoord cell) implements ReflexAction {
        public ReturnToZone {
            Objects.requireNonNull(cell, "Клетка возврата в зону не может быть null: исполнителю некуда вести духа");
        }
    }

    record StopMoving() implements ReflexAction {
    }

    record Eat() implements ReflexAction {
    }
}
