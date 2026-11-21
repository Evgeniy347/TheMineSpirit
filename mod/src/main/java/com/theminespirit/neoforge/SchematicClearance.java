package com.theminespirit.neoforge;

import com.theminespirit.core.building.BlockMatch;
import com.theminespirit.core.building.Schematic;
import com.theminespirit.core.building.SchematicActions;
import com.theminespirit.core.building.SchematicBlocks;
import com.theminespirit.core.building.SchematicUpgrade;
import com.theminespirit.core.building.api.BuildRotation;
import com.theminespirit.core.forestry.TreeSpan;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.town.StageSite;
import com.theminespirit.core.town.api.PlanBuilding;
import com.theminespirit.neoforge.board.SettlementPlotStore;
import com.theminespirit.neoforge.lease.LeaseData;
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
            if (orchardPlant(cell, current)) {
                continue;
            }
            if (current.is(BlockTags.LOGS)) {
                List<BlockCoord> span = TreeSpan.of(cell, at -> SchematicWild.log(site.level, site.schemeCells, at));
                if (span.stream().anyMatch(at -> orchardPlant(at, site.level.getBlockState(SchematicSite.toPos(at))))) {
                    continue;
                }
                unique.addAll(span);
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
            if (orchardPlant(entry.pos(), current)) {
                continue;
            }
            if (current.isAir()
                    || current.canBeReplaced()
                    || current.is(site.wanted(entry).getBlock())
                    || BlockMatch.matches(BlockStates.idOf(current), entry.block())
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
            cells.addAll(SchematicUpgrade.extraPositionsToClear(
                    site.previous, site.schematic, site.origin, site.rotation, this::aired));
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
        LeaseData leases = LeaseData.get(server);
        List<BlockCoord> previous = new ArrayList<>();
        for (int y = site.minY; y <= site.maxY + StageSite.BUILDING_HEIGHT; y++) {
            for (int x = site.minX; x <= site.maxX; x++) {
                for (int z = site.minZ; z <= site.maxZ; z++) {
                    if (stage.protectionBuilding(x, y, z) != own) {
                        continue;
                    }
                    BlockPos pos = new BlockPos(x, y, z);
                    if (leases.isActive(pos) && !site.level.getBlockState(pos).isAir()) {
                        previous.add(new BlockCoord(x, y, z));
                    }
                }
            }
        }
        cells.addAll(SchematicUpgrade.extraPositionsToClear(
                previous, site.schematic, site.origin, BuildRotation.NONE, this::aired));
    }

    private boolean orchardPlant(BlockCoord cell, BlockState state) {
        if (!(state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES) || state.is(BlockTags.SAPLINGS))) {
            return false;
        }
        if (!(site.level instanceof ServerLevel server)) {
            return false;
        }
        return SettlementPlotStore.liveSite(server)
                .filter(stage -> state.is(BlockTags.LOGS)
                        ? stage.orchardTree(cell.x(), cell.y(), cell.z())
                        : stage.orchardCrown(cell.x(), cell.y(), cell.z()))
                .isPresent();
    }

    private boolean aired(BlockCoord cell) {
        return site.level.getBlockState(SchematicSite.toPos(cell)).isAir();
    }
}
