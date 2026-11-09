package com.theminespirit.neoforge;

import com.theminespirit.core.building.BuildRotation;
import com.theminespirit.core.building.Schematic;
import com.theminespirit.core.building.SchematicActions;
import com.theminespirit.core.building.SchematicBlocks;
import com.theminespirit.core.building.SchematicUpgrade;
import com.theminespirit.core.forestry.TreeSpan;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.town.PlanBuilding;
import com.theminespirit.core.town.StageSite;
import com.theminespirit.neoforge.board.SettlementPlotStore;
import com.theminespirit.neoforge.policy.PlacedBlocksData;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.state.BlockState;

final class SchematicClearance {
    private final SchematicSite site;

    SchematicClearance(SchematicSite site) {
        this.site = site;
    }

    List<Schematic.PlacedEntry> missing() {
        List<Schematic.PlacedEntry> missing = new ArrayList<>();
        for (Schematic.PlacedEntry entry : site.entries) {
            if (!entry.optional() && !site.matches(entry)) {
                missing.add(entry);
            }
        }
        return missing;
    }

    List<BlockCoord> toClear() {
        Set<BlockCoord> unique = new LinkedHashSet<>();
        for (BlockCoord cell : site.air) {
            BlockState current = site.level.getBlockState(SchematicSite.toPos(cell));
            if (current.is(BlockTags.LOGS)) {
                unique.addAll(TreeSpan.of(cell, at -> SchematicWild.log(site.level, site.schemeCells, at)));
            } else if (!current.isAir() && current.getFluidState().isEmpty()) {
                unique.add(cell);
            }
        }
        List<BlockCoord> cells = SchematicWild.clearCells(site.level, unique, site.entries);
        for (Schematic.PlacedEntry entry : site.entries) {
            if (entry.optional() || SchematicActions.isActionPlaced(entry.block())) {
                continue;
            }
            BlockState current = site.level.getBlockState(SchematicSite.toPos(entry.pos()));
            if (current.isAir()
                    || current.canBeReplaced()
                    || current.is(site.wanted(entry).getBlock())
                    || (SchematicBlocks.isFloor(entry.block())
                            && SchematicBlocks.isFloorClass(BlockStates.idOf(current)))) {
                continue;
            }
            cells.add(entry.pos());
        }
        addOldSchemeExtras(cells);
        cells.sort(Comparator.comparingInt(BlockCoord::y).reversed().thenComparing(Comparator.naturalOrder()));
        return cells;
    }

    private void addOldSchemeExtras(List<BlockCoord> cells) {
        if (site.previous != null) {
            cells.addAll(SchematicUpgrade.extraPositions(site.previous, site.schematic, site.origin, site.rotation));
            return;
        }
        if (!(site.level instanceof ServerLevel server)) {
            return;
        }
        StageSite stage = SettlementPlotStore.liveSite(server).orElse(null);
        if (stage == null) {
            return;
        }
        PlanBuilding own = stage.protectionBuilding(site.origin.x(), site.origin.y(), site.origin.z());
        if (own == PlanBuilding.NONE) {
            return;
        }
        PlacedBlocksData placed = PlacedBlocksData.get(server);
        List<BlockCoord> previous = new ArrayList<>();
        for (int y = site.minY; y <= site.maxY + StageSite.BUILDING_HEIGHT; y++) {
            for (int x = site.minX; x <= site.maxX; x++) {
                for (int z = site.minZ; z <= site.maxZ; z++) {
                    if (stage.protectionBuilding(x, y, z) != own) {
                        continue;
                    }
                    BlockPos pos = new BlockPos(x, y, z);
                    if (placed.isSpiritMarked(pos)
                            && !site.level.getBlockState(pos).isAir()) {
                        previous.add(new BlockCoord(x, y, z));
                    }
                }
            }
        }
        cells.addAll(SchematicUpgrade.extraPositions(previous, site.schematic, site.origin, BuildRotation.NONE));
    }
}
