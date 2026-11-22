package com.theminespirit.neoforge;

import com.theminespirit.core.movement.api.DropProfile;
import com.theminespirit.core.movement.api.RouteDescent;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Node;
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

    public static @Nullable String descentRefusal(Path path, int startY, int targetY, Level level) {
        if (path == null) {
            return null;
        }
        return RouteDescent.refusalOf(startY, targetY, heights(path, level));
    }

    private static List<Integer> heights(Path path) {
        List<Integer> heights = new ArrayList<>();
        for (int i = path.getNextNodeIndex(); i < path.getNodeCount(); i++) {
            heights.add(path.getNode(i).y);
        }
        return heights;
    }

    private static List<RouteDescent.Height> heights(Path path, Level level) {
        List<RouteDescent.Height> heights = new ArrayList<>();
        for (int i = path.getNextNodeIndex(); i < path.getNodeCount(); i++) {
            Node node = path.getNode(i);
            heights.add(new RouteDescent.Height(node.y, wet(level, node)));
        }
        return heights;
    }

    private static boolean wet(Level level, Node node) {
        BlockPos pos = new BlockPos(node.x, node.y, node.z);
        return level.getFluidState(pos).is(FluidTags.WATER)
                || level.getFluidState(pos.above()).is(FluidTags.WATER);
    }
}
