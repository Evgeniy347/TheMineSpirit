package com.theminespirit.neoforge;

import com.theminespirit.core.movement.DropProfile;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.level.pathfinder.Path;

public final class CarefulPath {
    private CarefulPath() {}

    public static String refusal(Path path) {
        if (path == null) {
            return "пути к цели нет: осторожный шаг не строит обход через обрыв";
        }
        if (!path.canReach()) {
            return "путь не доходит до цели: она за обрывом глубже " + DropProfile.MAX_SAFE_DROP
                    + " блоков или за преградой";
        }
        int from = path.getNextNodeIndex();
        int to = Math.min(path.getNodeCount(), from + DropProfile.LOOKAHEAD_CELLS + 1);
        List<Integer> heights = new ArrayList<>(to - from);
        for (int i = from; i < to; i++) {
            heights.add(path.getNode(i).y);
        }
        if (DropProfile.blocksCarefulStep(heights)) {
            return "на пути обрыв глубже " + DropProfile.MAX_SAFE_DROP + " блоков в ближайших "
                    + DropProfile.LOOKAHEAD_CELLS + " клетках";
        }
        return null;
    }
}
