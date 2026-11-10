package com.theminespirit.neoforge;

import com.theminespirit.core.movement.CellCentering;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.script.CatalogLimits;
import com.theminespirit.core.town.PlotProtection;
import com.theminespirit.neoforge.lease.LeaseRun;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.jspecify.annotations.Nullable;

final class PillarColumn {
    enum Outcome {
        RUNNING,
        BUILT,
        FAILED
    }

    private static final double PILLAR_CLEARANCE = 1.0;
    private static final int MAX_STALL_TICKS = 200;

    private final SpiritEntity spirit;
    private final Level level;
    private final String blockId;
    private final BlockState state;
    private final int baseX;
    private final int baseY;
    private final int baseZ;
    private final int height;
    private final BlockPlacer placer;
    private final List<BlockCoord> cells = new ArrayList<>();

    private int built;
    private int stallTicks;
    private Outcome outcome = Outcome.RUNNING;
    private String failureReason;
    private @Nullable LeaseRun lease;

    private boolean waterOk;

    PillarColumn(SpiritEntity spirit, String blockId, int height) {
        this(spirit, blockId, height, spirit.blockPosition());
    }

    PillarColumn(SpiritEntity spirit, String blockId, int height, BlockPos base) {
        if (height < 1) {
            throw new IllegalArgumentException("Высота столба " + height + " меньше 1: строить нечего");
        }
        this.spirit = spirit;
        this.level = spirit.level();
        this.blockId = blockId;
        this.state = BlockStates.stateFor(blockId);
        this.height = height;
        BlockPos feet = base.immutable();
        this.baseX = feet.getX();
        this.baseY = feet.getY();
        this.baseZ = feet.getZ();
        this.placer = new BlockPlacer(spirit, PlotProtection.Ground.TEMPORARY_MOVEMENT);
    }

    static PillarColumn throughWater(SpiritEntity spirit, String blockId) {
        PillarColumn pillar = new PillarColumn(spirit, blockId, 1);
        pillar.waterOk = true;
        return pillar;
    }

    static String cheapBlock(SpiritEntity spirit) {
        if (spirit.getInventory().countItem(Items.DIRT) > 0) {
            return "minecraft:dirt";
        }
        if (spirit.getInventory().countItem(Items.COBBLESTONE) > 0) {
            return "minecraft:cobblestone";
        }
        return null;
    }

    static int count(SpiritEntity spirit, String blockId) {
        return BlockItems.countIn(spirit.getInventory(), blockId);
    }

    Outcome tick() {
        if (outcome != Outcome.RUNNING) {
            return outcome;
        }
        if (spirit.isRemoved()) {
            return fail("дух исчез во время стройки столба");
        }
        if (built >= height) {
            outcome = Outcome.BUILT;
            return outcome;
        }
        BlockPos target = new BlockPos(baseX, baseY + built, baseZ);
        String danger = danger(target);
        if (danger != null) {
            return fail(danger);
        }
        keepCentered();
        if (spirit.getY() >= target.getY() + PILLAR_CLEARANCE) {
            stallTicks = 0;
            return place(target);
        }
        if (++stallTicks > MAX_STALL_TICKS) {
            return fail("дух не поднялся на " + (target.getY() - baseY + 1) + "-й блок за " + MAX_STALL_TICKS
                    + " тиков (дух на " + spirit.blockPosition().toShortString() + ", y=" + spirit.getY()
                    + ", на земле " + spirit.onGround() + ", столб от " + baseX + "," + baseY + "," + baseZ
                    + ", построено " + built + "): вероятно, потолок над столбом");
        }
        if (spirit.onGround() || (waterOk && spirit.isInWater())) {
            spirit.getJumpControl().jump();
        }
        return Outcome.RUNNING;
    }

    private void keepCentered() {
        double centerX = baseX + 0.5;
        double centerZ = baseZ + 0.5;
        double dx = spirit.getX() - centerX;
        double dz = spirit.getZ() - centerZ;
        if (dx * dx + dz * dz <= CellCentering.TOLERANCE * CellCentering.TOLERANCE) {
            return;
        }
        spirit.getNavigation().stop();
        spirit.getMoveControl().setWantedPosition(centerX, spirit.getY(), centerZ, 1.0);
    }

    List<BlockCoord> cells() {
        return List.copyOf(cells);
    }

    @Nullable
    LeaseRun lease() {
        return lease;
    }

    LeaseRun requireLease() {
        if (lease == null) {
            lease = LeaseRun.open(spirit, "scaffold");
        }
        return lease;
    }

    int built() {
        return built;
    }

    int height() {
        return height;
    }

    int baseY() {
        return baseY;
    }

    String failureReason() {
        return failureReason;
    }

    private String danger(BlockPos target) {
        if (target.getY() > CatalogLimits.WORLD_Y_MAX) {
            return "достигнут потолок мира на y=" + target.getY() + ": столб дальше не строится";
        }
        if (level.getChunkSource().getChunkNow(target.getX() >> 4, target.getZ() >> 4) == null) {
            return "чанк с клеткой " + target.toShortString() + " не загружен: дух не грузит чанки ради столба";
        }
        BlockState current = level.getBlockState(target);
        if (!current.isAir() && !current.canBeReplaced()) {
            return "клетка " + target.toShortString() + " занята блоком " + BlockStates.idOf(current)
                    + ": потолок или помеха, столб не строится";
        }
        if (!level.getFluidState(target).isEmpty()
                && !(waterOk && level.getFluidState(target).is(FluidTags.WATER))) {
            return "в клетке " + target.toShortString() + " жидкость: лава или вода, столб не строится";
        }
        BlockPos head = target.above();
        if (!level.getFluidState(head).isEmpty()
                && !(waterOk && level.getFluidState(head).is(FluidTags.WATER))) {
            return "над клеткой " + target.toShortString() + " жидкость: лава или вода, столб не строится";
        }
        if (!level.getBlockState(head).isAir() && !level.getBlockState(head).canBeReplaced()) {
            return "над клеткой " + target.toShortString() + " блок " + BlockStates.idOf(level.getBlockState(head))
                    + ": потолок, дух не поднимется";
        }
        for (Direction side : Direction.Plane.HORIZONTAL) {
            FluidState beside = level.getFluidState(target.relative(side));
            if (!beside.isEmpty() && !(waterOk && beside.is(FluidTags.WATER))) {
                return "рядом с клеткой " + target.toShortString() + " жидкость: лава или вода, столб не строится";
            }
        }
        return null;
    }

    private Outcome place(BlockPos target) {
        if (count(spirit, blockId) < 1) {
            return fail("нет материала '" + blockId + "': нужно " + height + ", а закончился на " + built + "-м блоке");
        }
        BlockPlacer.Begin begin = placer.begin(target, state, blockId);
        if (begin instanceof BlockPlacer.Started) {
            cells.add(new BlockCoord(target.getX(), target.getY(), target.getZ()));
            requireLease().placed(target, blockId);
            built++;
            stallTicks = 0;
            return Outcome.RUNNING;
        }
        if (begin instanceof BlockPlacer.Protected protectedBlock) {
            return fail("ставить запрещено: " + protectedBlock.reason());
        }
        BlockPlacer.Refused refused = (BlockPlacer.Refused) begin;
        return switch (refused.reason()) {
            case NO_MATERIAL -> fail("нет материала '" + blockId + "': нужно 1, есть 0");
            case OCCUPIED -> fail("клетка " + target.toShortString() + " занята: потолок или помеха");
            case NO_SUPPORT -> fail("под клеткой " + target.toShortString() + " нет опоры");
            case OUT_OF_REACH -> fail("клетка " + target.toShortString() + " вне досягаемости");
            case CHUNK_NOT_LOADED -> fail("чанк с клеткой " + target.toShortString() + " не загружен");
        };
    }

    private Outcome fail(String reason) {
        failureReason = reason;
        outcome = Outcome.FAILED;
        return outcome;
    }
}
