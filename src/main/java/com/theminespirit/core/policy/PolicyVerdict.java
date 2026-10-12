package com.theminespirit.core.policy;

import com.theminespirit.core.observation.BlockCoord;
import java.util.Objects;
import java.util.Optional;

public sealed interface PolicyVerdict permits PolicyVerdict.Clear, PolicyVerdict.Reflex {

    record Clear() implements PolicyVerdict {
    }

    record Reflex(PolicyId policy, ReflexAction action, String reason, Optional<BlockCoord> targetCell) implements PolicyVerdict {
        public Reflex {
            Objects.requireNonNull(policy, "policy не может быть null");
            Objects.requireNonNull(action, "action не может быть null");
            Objects.requireNonNull(reason, "reason не может быть null");
            Objects.requireNonNull(targetCell, "targetCell не может быть null");
        }

        public static Reflex flee(PolicyId policy, BlockCoord cell, String reason) {
            return new Reflex(policy, ReflexAction.FLEE, reason, Optional.of(cell));
        }

        public static Reflex exitWater(PolicyId policy, BlockCoord cell, String reason) {
            return new Reflex(policy, ReflexAction.EXIT_WATER, reason, Optional.of(cell));
        }

        public static Reflex surface(PolicyId policy, String reason) {
            return new Reflex(policy, ReflexAction.SURFACE, reason, Optional.empty());
        }

        public static Reflex stepOutOfPortal(PolicyId policy, BlockCoord cell, String reason) {
            return new Reflex(policy, ReflexAction.STEP_OUT_OF_PORTAL, reason, Optional.of(cell));
        }

        public static Reflex returnToZone(PolicyId policy, BlockCoord cell, String reason) {
            return new Reflex(policy, ReflexAction.RETURN_TO_ZONE, reason, Optional.of(cell));
        }

        public static Reflex stopMoving(PolicyId policy, String reason) {
            return new Reflex(policy, ReflexAction.STOP_MOVING, reason, Optional.empty());
        }
    }
}