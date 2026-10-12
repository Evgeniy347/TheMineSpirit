package com.theminespirit.neoforge;

import com.theminespirit.core.mining.Cell;
import com.theminespirit.core.mining.SafetyView;
import com.theminespirit.core.observation.BlockCoord;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.CactusBlock;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.EndPortalBlock;
import net.minecraft.world.level.block.MagmaBlock;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.PowderSnowBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.WebBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

public final class LevelSafetyView implements SafetyView {
    private final Level level;
    private int reads;

    public LevelSafetyView(Level level) {
        this.level = Objects.requireNonNull(level, "Уровень не может быть null: по нему читаются клетки мира");
    }

    @Override
    public Cell cellAt(BlockCoord coord) {
        Objects.requireNonNull(coord, "Координата не может быть null: неизвестно, какую клетку читать");
        reads++;
        BlockPos pos = new BlockPos(coord.x(), coord.y(), coord.z());
        return toCell(level.getBlockState(pos), pos);
    }

    public int reads() {
        return reads;
    }

    private Cell toCell(BlockState state, BlockPos pos) {
        FluidState fluid = state.getFluidState();
        if (!fluid.isEmpty()) {
            if (fluid.is(FluidTags.WATER)) {
                return Cell.WATER;
            }
            if (fluid.is(FluidTags.LAVA)) {
                return Cell.LAVA;
            }
        }
        if (state.getBlock() instanceof BaseFireBlock) {
            return Cell.FIRE;
        }
        if (state.getBlock() instanceof MagmaBlock || state.getBlock() instanceof CactusBlock
                || state.getBlock() instanceof SweetBerryBushBlock || state.getBlock() instanceof WebBlock
                || state.getBlock() instanceof PowderSnowBlock) {
            return Cell.HAZARD;
        }
        if (state.getBlock() instanceof NetherPortalBlock || state.getBlock() instanceof EndPortalBlock) {
            return Cell.PORTAL;
        }
        if (state.getBlock() instanceof FallingBlock) {
            return Cell.FALLING;
        }
        if (state.getCollisionShape(level, pos).isEmpty()) {
            return Cell.PASSABLE;
        }
        return Cell.SOLID;
    }
}