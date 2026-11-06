package com.theminespirit.neoforge.movement;

import com.theminespirit.core.movement.DescentPath;
import com.theminespirit.core.navigation.NavAStarSearch;
import com.theminespirit.core.navigation.NavContext;
import com.theminespirit.core.navigation.NavGoalNear;
import com.theminespirit.core.navigation.NavPath;
import com.theminespirit.core.navigation.NavResult;
import com.theminespirit.core.navigation.NavSettings;
import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;

public final class RescueReach {
    private static final int RADIUS = 2;
    private static final NavSettings WALK_ONLY = new NavSettings(300, 600, 30_000, 50, 3, 12, 2, 20, 2, false, false);

    public record Verdict(boolean reachable, String why) {}

    private RescueReach() {}

    public static Verdict check(ServerLevel level, BlockPos from, BlockCoord target) {
        BlockCoord start = new BlockCoord(from.getX(), from.getY(), from.getZ());
        NavContext context = new NavContext(new LevelNavWorld(level), WALK_ONLY, 0);
        NavResult result = NavAStarSearch.plan(
                context, start.x(), start.y(), start.z(), new NavGoalNear(target.x(), target.y(), target.z(), RADIUS));
        NavPath path = result.path();
        if (!result.found() || path == null) {
            return new Verdict(false, "путь не найден");
        }
        List<BlockCoord> cells = path.positions();
        BlockCoord end = cells.get(cells.size() - 1);
        boolean water =
                level.getFluidState(new BlockPos(end.x(), end.y(), end.z())).is(FluidTags.WATER);
        String refusal = DescentPath.refusalOrNull(cells, water);
        if (refusal != null) {
            return new Verdict(false, refusal);
        }
        return new Verdict(true, "путь " + cells.size() + " клеток");
    }
}
