package com.theminespirit.core.policy;

import com.theminespirit.core.mining.BreakSafety;
import com.theminespirit.core.mining.SafetyView;
import com.theminespirit.core.observation.BlockCoord;
import java.util.Objects;
import java.util.OptionalInt;

public final class BreakVeto {
    private BreakVeto() {}

    public static BreakSafety checkBelowFeet(BlockCoord target, BlockCoord feet, SafetyView view) {
        Objects.requireNonNull(target, "Цель ломки не может быть null: без неё неизвестно, что проверять под ногами");
        Objects.requireNonNull(feet, "Клетка ног не может быть null: без неё неизвестно, стоит ли дух на этом блоке");
        Objects.requireNonNull(view, "SafetyView не может быть null: по нему читается, что под ломаемым блоком");
        boolean underFeet = target.x() == feet.x() && target.z() == feet.z() && target.y() == feet.y() - 1;
        if (!underFeet) {
            return new BreakSafety.Safe();
        }
        OptionalInt below = DropPolicy.fallBelow(target, view);
        if (below.isEmpty() || 1 + below.getAsInt() > DropPolicy.MAX_DROP) {
            return new BreakSafety.Unsafe("под блоком " + target.x() + " " + target.y() + " " + target.z()
                    + " пустота глубже " + DropPolicy.MAX_DROP
                    + " блоков или жидкость, огонь, опасный блок: дух упадёт, сначала положите опору");
        }
        return new BreakSafety.Safe();
    }
}
