package com.theminespirit.neoforge;

import com.theminespirit.core.building.Schematic;
import com.theminespirit.core.building.SchematicActions;
import com.theminespirit.core.building.SchematicGround;
import com.theminespirit.core.building.SpiritReach;
import com.theminespirit.core.engine.ScriptStatus;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.policy.BlockProtection;
import com.theminespirit.core.town.PlotProtection;
import com.theminespirit.core.town.StageSite;
import com.theminespirit.neoforge.board.SettlementPlotStore;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

final class SchematicFieldBase {
    private final BuildSchematicScript host;
    private final List<BlockCoord> ground;
    private int cursor;

    SchematicFieldBase(BuildSchematicScript host) {
        this.host = host;
        List<BlockCoord> cells = new ArrayList<>();
        if (SchematicGround.tilled(host.site.schematic())) {
            for (Schematic.PlacedEntry entry : host.site.primaries()) {
                if (entry.pos().y() == host.site.origin().y()
                        && (SchematicActions.isTilled(entry.block())
                                || SchematicActions.isWalkway(entry.block())
                                || SchematicActions.isFluidPlaced(entry.block()))) {
                    cells.add(entry.pos());
                }
            }
        }
        this.ground = List.copyOf(cells);
    }

    static int missingBaseCount(SchematicSite site) {
        if (!SchematicGround.tilled(site.schematic())) {
            return 0;
        }
        int missing = 0;
        for (Schematic.PlacedEntry entry : site.primaries()) {
            if (entry.pos().y() == site.origin().y()
                    && (SchematicActions.isTilled(entry.block())
                            || SchematicActions.isWalkway(entry.block())
                            || SchematicActions.isFluidPlaced(entry.block()))
                    && !ready(site.level, SchematicSite.toPos(entry.pos()))) {
                missing++;
            }
        }
        return missing;
    }

    Optional<ScriptStatus> tick() {
        List<BlockCoord> pending = new ArrayList<>();
        for (int index = cursor; index < ground.size(); index++) {
            BlockCoord cell = ground.get(index);
            if (!ready(host.level, SchematicSite.toPos(cell))) {
                pending.add(cell);
            }
        }
        if (pending.isEmpty()) {
            cursor = ground.size();
            return Optional.empty();
        }
        BlockCoord cell = pending.get(0);
        BlockCoord below = new BlockCoord(cell.x(), cell.y() - 1, cell.z());
        if (!SpiritReach.current().reaches(host.feet(), below)) {
            ScriptStatus moved = host.placement.moveTowards(
                    pending,
                    host.placement.clearWorld(),
                    false,
                    (stand, target) -> SpiritReach.current()
                            .reaches(stand, new BlockCoord(target.x(), target.y() - 1, target.z())));
            if (!host.approach.isActive() && host.blocked.containsKey(cell)) {
                return Optional.of(
                        host.report.fail("нет стоянки для основания поля под " + cell + ": " + host.blocked.get(cell)));
            }
            return Optional.of(moved);
        }
        BlockPlacer.Refused refusal = prepare(host.spirit, SchematicSite.toPos(cell));
        if (refusal == null) {
            cursor++;
            host.basePrepared++;
            host.lastPlaceTick = host.ticks;
            host.blocked.remove(cell);
            host.excludedStands.clear();
            return Optional.of(ScriptStatus.RUNNING);
        }
        if (refusal.reason() == BlockPlacer.RefuseReason.NO_MATERIAL) {
            String material =
                    PathTrample.hasTool(host.spirit, "minecraft:dirt_path") ? "minecraft:dirt" : "minecraft:dirt_path";
            return Optional.of(host.report.noMaterial(material, refusal.message()));
        }
        return Optional.of(host.report.fail("основание поля под " + cell + " не подготовлено: " + refusal.message()));
    }

    private static boolean ready(Level level, BlockPos target) {
        return SchematicActions.isTrampleGround(BlockStates.idOf(level.getBlockState(target.below())));
    }

    private static BlockPlacer.@Nullable Refused prepare(SpiritEntity spirit, BlockPos target) {
        Level level = spirit.level();
        BlockPos below = target.below();
        if (ready(level, target)) {
            return null;
        }
        if (level.getChunkSource().getChunkNow(below.getX() >> 4, below.getZ() >> 4) == null) {
            return refused(
                    BlockPlacer.RefuseReason.NO_SUPPORT, "чанк основания " + below.toShortString() + " не загружен");
        }
        if (!PathTrample.hasTool(spirit, "minecraft:dirt_path")) {
            return refused(BlockPlacer.RefuseReason.NO_MATERIAL, "для основания поля нужна лопата");
        }
        if (spirit.protectionGate().checkPlace(below) instanceof BlockProtection.Refused protectedBlock) {
            return refused(BlockPlacer.RefuseReason.NO_SUPPORT, "основание защищено: " + protectedBlock.reason());
        }
        StageSite site = level instanceof ServerLevel server
                ? SettlementPlotStore.liveSite(server).orElse(null)
                : null;
        PlotProtection.Decision plot = PlotProtection.check(
                site,
                new BlockCoord(below.getX(), below.getY(), below.getZ()),
                PlotProtection.Action.PLACE,
                PlotProtection.Ground.PLAN_BUILD,
                BuildPlanOwner.of(spirit));
        if (plot instanceof PlotProtection.Refused refusedPlot) {
            return refused(BlockPlacer.RefuseReason.NO_SUPPORT, "основание защищено: " + refusedPlot.reason());
        }
        if (spirit.getInventory().countItem(Items.DIRT) == 0) {
            return refused(BlockPlacer.RefuseReason.NO_MATERIAL, "для основания поля нужна земля");
        }
        if (!PlaceSupport.hasSupport(level, below, Blocks.DIRT.defaultBlockState())) {
            return refused(
                    BlockPlacer.RefuseReason.NO_SUPPORT, "под основанием " + below.toShortString() + " нет опоры");
        }
        BlockState current = level.getBlockState(below);
        if (!(level instanceof ServerLevel server)
                || (!current.isAir() && !SpiritBlocks.destroy(server, below, true, spirit, "FIELD_BASE"))
                || !level.getBlockState(below).isAir()) {
            return refused(
                    BlockPlacer.RefuseReason.NO_SUPPORT, "не удалось очистить основание " + below.toShortString());
        }
        spirit.getInventory().removeItemType(Items.DIRT, 1);
        if (!SpiritBlocks.place(server, below, Blocks.DIRT.defaultBlockState(), spirit, "FIELD_BASE")
                || !level.getBlockState(below).is(Blocks.DIRT)) {
            spirit.getInventory().addItem(new ItemStack(Items.DIRT));
            return refused(
                    BlockPlacer.RefuseReason.NO_SUPPORT, "земля в основании " + below.toShortString() + " не держится");
        }
        return null;
    }

    private static BlockPlacer.Refused refused(BlockPlacer.RefuseReason reason, String message) {
        return new BlockPlacer.Refused(reason, message);
    }
}
