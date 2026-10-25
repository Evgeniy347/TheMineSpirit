package com.theminespirit.neoforge;

import com.theminespirit.core.movement.DropProfile;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.level.pathfinder.Path;

public final class CarefulPath {
    private CarefulPath() {}

    public static String refusal(Path path) {
        if (path == null) {
            return DropProfile.carefulRefusal(false, false, List.of());
        }
        List<Integer> heights = new ArrayList<>();
        for (int i = path.getNextNodeIndex(); i < path.getNodeCount(); i++) {
            heights.add(path.getNode(i).y);
        }
        return DropProfile.carefulRefusal(true, path.canReach(), heights);
    }
}
