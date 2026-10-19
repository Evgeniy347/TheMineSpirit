package com.theminespirit.neoforge.crater;

import com.theminespirit.core.building.Crater;
import com.theminespirit.core.building.CraterSurface;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.policy.BlockProtection;
import com.theminespirit.neoforge.SpiritEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

/**
 * Поверхность воронки в настоящем мире: уровень края из памяти воронок, текущие блоки из уровня.
 *
 * <p>Защищённой клетка считается, если духу запрещено там ставить (защищённая зона, рабочая зона):
 * политика защиты блоков та же, что у обычной стройки.
 */
final class LevelCraterSurface implements CraterSurface {
    private final Level level;
    private final SpiritEntity spirit;
    private final Crater crater;
    private int blockReads;

    LevelCraterSurface(Level level, SpiritEntity spirit, Crater crater) {
        this.level = level;
        this.spirit = spirit;
        this.crater = crater;
    }

    @Override
    public BlockCoord center() {
        return crater.center();
    }

    @Override
    public int radius() {
        return crater.radius();
    }

    @Override
    public int groundLevel(int x, int z) {
        return crater.groundLevel();
    }

    @Override
    public boolean isAir(int x, int y, int z) {
        blockReads++;
        BlockPos pos = new BlockPos(x, y, z);
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) {
            return true;
        }
        FluidState fluid = state.getFluidState();
        if (!fluid.isEmpty()) {
            return false;
        }
        return state.canBeReplaced();
    }

    @Override
    public boolean isProtected(int x, int y, int z) {
        return spirit.protectionGate().checkPlace(new BlockPos(x, y, z)) instanceof BlockProtection.Refused;
    }

    int blockReads() {
        return blockReads;
    }
}
