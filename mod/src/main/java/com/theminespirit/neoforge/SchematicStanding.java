package com.theminespirit.neoforge;

import com.theminespirit.core.building.BuildBox;
import com.theminespirit.core.building.RetreatPath;
import com.theminespirit.core.building.Schematic;
import com.theminespirit.core.mining.Cell;
import com.theminespirit.core.mining.MiningSafety;
import com.theminespirit.core.mining.SafetyView;
import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

final class SchematicStanding {
    private static final double FLAT_HEIGHT = 0.1;
    private static final int ROOF_MIN_HEIGHT = 3;

    private final SchematicSite site;
    private @Nullable Set<BlockCoord> standBlockers;

    SchematicStanding(SchematicSite site) {
        this.site = site;
    }

    Set<BlockCoord> standBlockers() {
        Set<BlockCoord> cached = standBlockers;
        if (cached == null) {
            Set<BlockCoord> cells = new HashSet<>();
            for (Schematic.PlacedEntry entry : site.entries) {
                BlockState state = site.wanted(entry);
                BlockPos pos = SchematicSite.toPos(entry.pos());
                VoxelShape shape = state.getCollisionShape(site.level, pos);
                if (!(state.getBlock() instanceof DoorBlock)
                        && !shape.isEmpty()
                        && shape.max(Direction.Axis.Y) > FLAT_HEIGHT) {
                    cells.add(entry.pos());
                }
            }
            cached = Set.copyOf(cells);
            standBlockers = cached;
        }
        return cached;
    }

    boolean standable(BlockCoord feet) {
        return MiningSafety.canStand(feet, site.safety);
    }

    BuildBox networkBox(int ring) {
        return BuildBox.of(
                site.minX - ring, site.minY - 1, site.minZ - ring, site.maxX + ring, site.maxY + 2, site.maxZ + ring);
    }

    List<BlockCoord> doorCells() {
        List<BlockCoord> doors = new ArrayList<>();
        for (Schematic.PlacedEntry entry : site.primaries) {
            if (site.wanted(entry).getBlock() instanceof DoorBlock) {
                doors.add(entry.pos());
            }
        }
        return doors;
    }

    boolean onRoof(BlockCoord stand) {
        if (outsideFootprint(stand) || stand.y() < site.minY + ROOF_MIN_HEIGHT) {
            return false;
        }
        for (int y = stand.y() + 2; y <= site.maxY; y++) {
            if (standBlockers().contains(new BlockCoord(stand.x(), y, stand.z()))) {
                return false;
            }
        }
        return true;
    }

    boolean outsideFootprint(BlockCoord feet) {
        return feet.x() < site.minX || feet.x() > site.maxX || feet.z() < site.minZ || feet.z() > site.maxZ;
    }

    boolean overlaps(Schematic.PlacedEntry entry, AABB body) {
        BlockPos pos = SchematicSite.toPos(entry.pos());
        return body.intersects(new AABB(pos))
                && !site.wanted(entry).getCollisionShape(site.level, pos).isEmpty();
    }

    boolean keepsRetreat(BlockCoord feet, List<Schematic.PlacedEntry> entries) {
        Set<BlockCoord> placed = new HashSet<>();
        for (Schematic.PlacedEntry entry : entries) {
            BlockState state = site.wanted(entry);
            if (!(state.getBlock() instanceof DoorBlock)
                    && !state.getCollisionShape(site.level, SchematicSite.toPos(entry.pos()))
                            .isEmpty()) {
                placed.add(entry.pos());
            }
        }
        return RetreatPath.allows(feet, placed, walkView(), this::outsideFootprint);
    }

    Optional<Set<BlockCoord>> walkable(BlockCoord feet, int margin) {
        return RetreatPath.walkable(
                feet,
                walkView(),
                cell -> cell.x() >= site.minX - margin
                        && cell.x() <= site.maxX + margin
                        && cell.z() >= site.minZ - margin
                        && cell.z() <= site.maxZ + margin);
    }

    private SafetyView walkView() {
        return cell -> site.level.getBlockState(SchematicSite.toPos(cell)).getBlock() instanceof DoorBlock
                ? Cell.PASSABLE
                : site.safety.cellAt(cell);
    }
}
