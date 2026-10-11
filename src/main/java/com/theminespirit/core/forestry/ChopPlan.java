package com.theminespirit.core.forestry;

import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import java.util.Objects;

public record ChopPlan(List<BlockCoord> reachable, int leftStanding) {
    public ChopPlan {
        reachable = List.copyOf(Objects.requireNonNull(reachable, "reachable не может быть null: пустой список — законный результат"));
        if (leftStanding < 0) {
            throw new IllegalArgumentException("leftStanding=" + leftStanding
                    + ": число оставшихся брёвен не может быть отрицательным");
        }
    }

    public static ChopPlan of(Tree tree, int reachAboveBase) {
        Objects.requireNonNull(tree, "tree не может быть null: план рубки строится по разобранному дереву");
        if (reachAboveBase < 0) {
            throw new IllegalArgumentException("reachAboveBase=" + reachAboveBase
                    + ": досягаемость не может быть отрицательной, иначе рубить нечего");
        }
        int limit = tree.base().y() + reachAboveBase;
        List<BlockCoord> reachable = tree.logs().stream().filter(coord -> coord.y() <= limit).toList();
        return new ChopPlan(reachable, tree.logs().size() - reachable.size());
    }
}
