package com.theminespirit.neoforge;

import com.theminespirit.core.building.BuildBox;
import com.theminespirit.core.building.StandPathGate;
import com.theminespirit.core.observation.BlockCoord;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.jspecify.annotations.Nullable;

final class BuildSupplyReach {
    private BuildSupplyReach() {}

    static Optional<BlockCoord> feet(SchematicSite site, BlockPos chest) {
        BlockCoord origin = site.origin();
        @Nullable BlockCoord best = null;
        double bestDistance = Double.MAX_VALUE;
        BuildBox box = site.networkBox(0);
        BlockPos centre = new BlockPos((box.minX() + box.maxX()) / 2, origin.y(), (box.minZ() + box.maxZ()) / 2);
        for (Direction side : Direction.Plane.HORIZONTAL) {
            BlockPos cell = chest.relative(side);
            BlockCoord coord = new BlockCoord(cell.getX(), cell.getY(), cell.getZ());
            if (cell.getY() != origin.y()
                    || !site.standable(coord)
                    || site.schemeCells().contains(coord)
                    || !site.outsideFootprint(coord)) {
                continue;
            }
            double distance = cell.distSqr(centre);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = coord;
            }
        }
        return Optional.ofNullable(best);
    }

    static Predicate<BlockPos> reachable(SchematicSite site, BlockCoord feet) {
        boolean near = site.networkBox(BuildSchematicScript.WALK_MARGIN).contains(feet);
        Optional<Set<BlockCoord>> walkable = site.walkable(feet, BuildSchematicScript.WALK_MARGIN);
        return chest -> feet(site, chest)
                .map(stand -> StandPathGate.usable(site.standable(stand), near, walkable, stand))
                .orElse(true);
    }
}
