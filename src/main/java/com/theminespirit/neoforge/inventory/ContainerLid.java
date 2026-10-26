package com.theminespirit.neoforge.inventory;

import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.CompoundContainer;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

public final class ContainerLid {
    private ContainerLid() {}

    public static void begin(ServerLevel level, BlockPos pos, int openers, boolean playSound) {
        if (!level.isLoaded(pos)) {
            return;
        }
        BlockEntity entity = level.getBlockEntity(pos);
        BlockState state = level.getBlockState(pos);
        if (entity instanceof ChestBlockEntity) {
            chestEvent(level, pos, state, openers);
            if (playSound) {
                chestSounds(level, pos, state, SoundEvents.CHEST_OPEN);
            }
        } else if (entity instanceof BarrelBlockEntity) {
            if (!state.getValue(BarrelBlock.OPEN)) {
                level.setBlock(pos, state.setValue(BarrelBlock.OPEN, true), 3);
            }
            if (playSound) {
                barrelSound(level, pos, state, SoundEvents.BARREL_OPEN);
            }
        } else if (entity instanceof ShulkerBoxBlockEntity shulker) {
            shulker.startOpen(FakePlayerFactory.getMinecraft(level));
        }
    }

    public static void keep(ServerLevel level, BlockPos pos, int openers) {
        if (!level.isLoaded(pos)) {
            return;
        }
        BlockEntity entity = level.getBlockEntity(pos);
        BlockState state = level.getBlockState(pos);
        if (entity instanceof ChestBlockEntity) {
            chestEvent(level, pos, state, openers);
        } else if (entity instanceof BarrelBlockEntity && !state.getValue(BarrelBlock.OPEN)) {
            level.setBlock(pos, state.setValue(BarrelBlock.OPEN, true), 3);
        }
    }

    public static void close(ServerLevel level, BlockPos pos) {
        if (!level.isLoaded(pos)) {
            return;
        }
        BlockEntity entity = level.getBlockEntity(pos);
        BlockState state = level.getBlockState(pos);
        if (entity instanceof ChestBlockEntity) {
            if (playerOpeners(level, pos) > 0) {
                return;
            }
            chestEvent(level, pos, state, 0);
            chestSounds(level, pos, state, SoundEvents.CHEST_CLOSE);
        } else if (entity instanceof BarrelBlockEntity) {
            if (playerOpeners(level, pos) > 0) {
                return;
            }
            if (state.getValue(BarrelBlock.OPEN)) {
                level.setBlock(pos, state.setValue(BarrelBlock.OPEN, false), 3);
            }
            barrelSound(level, pos, state, SoundEvents.BARREL_CLOSE);
        } else if (entity instanceof ShulkerBoxBlockEntity shulker) {
            shulker.stopOpen(FakePlayerFactory.getMinecraft(level));
        }
    }

    public static int playerOpeners(ServerLevel level, BlockPos pos) {
        if (!level.isLoaded(pos) || !(level.getBlockEntity(pos) instanceof Container own)) {
            return 0;
        }
        int openers = 0;
        for (ServerPlayer player : level.players()) {
            if (holds(player.containerMenu, own)) {
                openers++;
            }
        }
        return openers;
    }

    private static boolean holds(AbstractContainerMenu menu, Container own) {
        for (Slot slot : menu.slots) {
            Container container = slot.container;
            if (Objects.equals(container, own)) {
                return true;
            }
            if (container instanceof CompoundContainer both && both.contains(own)) {
                return true;
            }
        }
        return false;
    }

    private static void chestEvent(ServerLevel level, BlockPos pos, BlockState state, int openers) {
        level.blockEvent(pos, state.getBlock(), 1, openers);
        if (state.getValue(ChestBlock.TYPE) == ChestType.SINGLE) {
            return;
        }
        BlockPos other = pos.relative(ChestBlock.getConnectedDirection(state));
        if (!level.isLoaded(other)) {
            return;
        }
        BlockState otherState = level.getBlockState(other);
        level.blockEvent(other, otherState.getBlock(), 1, openers);
    }

    private static void chestSounds(ServerLevel level, BlockPos pos, BlockState state, SoundEvent sound) {
        playChestSound(level, pos, state, sound);
        if (state.getValue(ChestBlock.TYPE) == ChestType.SINGLE) {
            return;
        }
        BlockPos other = pos.relative(ChestBlock.getConnectedDirection(state));
        if (!level.isLoaded(other)) {
            return;
        }
        playChestSound(level, other, level.getBlockState(other), sound);
    }

    private static void playChestSound(ServerLevel level, BlockPos pos, BlockState state, SoundEvent sound) {
        if (state.getValue(ChestBlock.TYPE) == ChestType.LEFT) {
            return;
        }
        double x = (double) pos.getX() + 0.5;
        double y = (double) pos.getY() + 0.5;
        double z = (double) pos.getZ() + 0.5;
        if (state.getValue(ChestBlock.TYPE) == ChestType.RIGHT) {
            Direction direction = ChestBlock.getConnectedDirection(state);
            x += (double) direction.getStepX() * 0.5;
            z += (double) direction.getStepZ() * 0.5;
        }
        level.playSound(null, x, y, z, sound, SoundSource.BLOCKS, 0.5F, level.random.nextFloat() * 0.1F + 0.9F);
    }

    private static void barrelSound(ServerLevel level, BlockPos pos, BlockState state, SoundEvent sound) {
        Vec3i normal = state.getValue(BarrelBlock.FACING).getNormal();
        double x = (double) pos.getX() + 0.5 + (double) normal.getX() / 2.0;
        double y = (double) pos.getY() + 0.5 + (double) normal.getY() / 2.0;
        double z = (double) pos.getZ() + 0.5 + (double) normal.getZ() / 2.0;
        level.playSound(null, x, y, z, sound, SoundSource.BLOCKS, 0.5F, level.random.nextFloat() * 0.1F + 0.9F);
    }
}
