package com.theminespirit.neoforge;

import com.theminespirit.neoforge.policy.PlacedBlocksData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.gameevent.GameEvent;

final class PlaceParts {
    private PlaceParts() {}

    static void announce(SpiritEntity spirit, Level level, BlockPos target, BlockState state) {
        level.gameEvent(GameEvent.BLOCK_PLACE, target, GameEvent.Context.of(spirit, state));
        spirit.showInHand(new ItemStack(state.getBlock().asItem()));
        SpiritGesture.workOn(spirit, target);
        SoundType sound = state.getSoundType(level, target, spirit);
        level.playSound(
                null,
                target,
                sound.getPlaceSound(),
                SoundSource.BLOCKS,
                (sound.getVolume() + 1.0F) / 2.0F,
                sound.getPitch() * 0.8F);
        if (level instanceof ServerLevel server) {
            PlacedBlocksData.get(server).markSpirit(target);
            BlockPos second = secondPart(target, state);
            if (second != null) {
                PlacedBlocksData.get(server).markSpirit(second);
            }
        }
    }

    static boolean firstOfTwoParts(BlockState state) {
        return secondPart(BlockPos.ZERO, state) != null;
    }

    static BlockPos secondPart(BlockPos target, BlockState state) {
        if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
                && state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER) {
            return target.above();
        }
        if (state.hasProperty(BlockStateProperties.BED_PART)
                && state.getValue(BlockStateProperties.BED_PART) == BedPart.FOOT) {
            return target.relative(state.getValue(BlockStateProperties.HORIZONTAL_FACING));
        }
        return null;
    }
}
