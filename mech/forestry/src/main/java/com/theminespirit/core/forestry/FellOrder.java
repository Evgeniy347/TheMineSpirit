package com.theminespirit.core.forestry;

import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import java.util.Objects;

public final class FellOrder {
    private FellOrder() {}

    public static List<BlockCoord> of(BlockCoord start, TreeView view) {
        Objects.requireNonNull(start, "Клетка начала валки не может быть null: от неё разбирается дерево");
        Objects.requireNonNull(view, "Вид мира не может быть null: без него дерево не разобрать");
        if (!view.isLog(start)) {
            return List.of();
        }
        return TreeShape.detect(start, view) instanceof Tree tree
                ? ChopPlan.of(tree, start).order()
                : List.of();
    }
}
