package com.theminespirit.neoforge;

import com.theminespirit.core.movement.DropProfile;
import com.theminespirit.core.movement.RouteDescent;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.level.pathfinder.Path;
import org.jspecify.annotations.Nullable;

public final class CarefulPath {
    private CarefulPath() {}

    public static String refusal(Path path) {
        if (path == null) {
            return DropProfile.carefulRefusal(false, false, List.of());
        }
        return DropProfile.carefulRefusal(true, path.canReach(), heights(path));
    }

    public static @Nullable String descentRefusal(Path path, int startY, int targetY) {
        if (path == null) {
            return null;
        }
        return RouteDescent.refusal(startY, targetY, heights(path));
    }

    private static List<Integer> heights(Path path) {
        List<Integer> heights = new ArrayList<>();
        for (int i = path.getNextNodeIndex(); i < path.getNodeCount(); i++) {
            heights.add(path.getNode(i).y);
        }
        return heights;
    }
}
