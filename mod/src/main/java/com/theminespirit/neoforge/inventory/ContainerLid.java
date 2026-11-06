package com.theminespirit.neoforge.inventory;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.board.StateChangeLog;
import com.theminespirit.core.inventory.ContainerCell;
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
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import org.slf4j.Logger;

public final class ContainerLid {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final StateChangeLog CHANGES = new StateChangeLog();
    private static final String BEGIN = "begin";
    private static final String KEEP = "keep";
    private static final String CLOSE = "close";

    private ContainerLid() {}

    public static void begin(ServerLevel level, BlockPos pos, int openers, boolean playSound) {
        if (!level.isLoaded(pos)) {
            return;
        }
        ContainerCell cell = ContainerCells.at(level, pos);
        if (broken(cell, BEGIN)) {
            return;
        }
        BlockEntity entity = level.getBlockEntity(pos);
        BlockState state = level.getBlockState(pos);
        if (entity instanceof ChestBlockEntity) {
            ChestType type = ContainerCells.chestType(state);
            if (type == null) {
                skip(cell, BEGIN, ContainerCell.Problem.CHEST_WITHOUT_TYPE.text());
                return;
            }
            chestEvent(level, pos, state, type, openers);
            if (playSound) {
                chestSounds(level, pos, state, type, SoundEvents.CHEST_OPEN);
            }
        } else if (entity instanceof BarrelBlockEntity) {
            if (!(state.getBlock() instanceof BarrelBlock)) {
                skip(cell, BEGIN, "BlockEntity бочки стоит на блоке другого типа");
                return;
            }
            if (!state.getValue(BarrelBlock.OPEN)) {
                level.setBlock(pos, state.setValue(BarrelBlock.OPEN, true), 3);
            }
            if (playSound) {
                barrelSound(level, pos, state, SoundEvents.BARREL_OPEN);
            }
        } else if (entity instanceof ShulkerBoxBlockEntity shulker) {
            if (!(state.getBlock() instanceof ShulkerBoxBlock)) {
                skip(cell, BEGIN, "BlockEntity шалкера стоит на блоке другого типа");
                return;
            }
            shulker.startOpen(FakePlayerFactory.getMinecraft(level));
        }
    }

    public static void keep(ServerLevel level, BlockPos pos, int openers) {
        if (!level.isLoaded(pos)) {
            return;
        }
        ContainerCell cell = ContainerCells.at(level, pos);
        if (broken(cell, KEEP)) {
            return;
        }
        BlockEntity entity = level.getBlockEntity(pos);
        BlockState state = level.getBlockState(pos);
        if (entity instanceof ChestBlockEntity) {
            ChestType type = ContainerCells.chestType(state);
            if (type == null) {
                skip(cell, KEEP, ContainerCell.Problem.CHEST_WITHOUT_TYPE.text());
                return;
            }
            chestEvent(level, pos, state, type, openers);
        } else if (entity instanceof BarrelBlockEntity) {
            if (!(state.getBlock() instanceof BarrelBlock)) {
                skip(cell, KEEP, "BlockEntity бочки стоит на блоке другого типа");
                return;
            }
            if (!state.getValue(BarrelBlock.OPEN)) {
                level.setBlock(pos, state.setValue(BarrelBlock.OPEN, true), 3);
            }
        }
    }

    public static void close(ServerLevel level, BlockPos pos) {
        if (!level.isLoaded(pos)) {
            return;
        }
        ContainerCell cell = ContainerCells.at(level, pos);
        if (broken(cell, CLOSE)) {
            return;
        }
        BlockEntity entity = level.getBlockEntity(pos);
        BlockState state = level.getBlockState(pos);
        if (entity instanceof ChestBlockEntity) {
            if (playerOpeners(level, pos) > 0) {
                return;
            }
            ChestType type = ContainerCells.chestType(state);
            if (type == null) {
                skip(cell, CLOSE, ContainerCell.Problem.CHEST_WITHOUT_TYPE.text());
                return;
            }
            chestEvent(level, pos, state, type, 0);
            chestSounds(level, pos, state, type, SoundEvents.CHEST_CLOSE);
        } else if (entity instanceof BarrelBlockEntity) {
            if (!(state.getBlock() instanceof BarrelBlock)) {
                skip(cell, CLOSE, "BlockEntity бочки стоит на блоке другого типа");
                return;
            }
            if (playerOpeners(level, pos) > 0) {
                return;
            }
            if (state.getValue(BarrelBlock.OPEN)) {
                level.setBlock(pos, state.setValue(BarrelBlock.OPEN, false), 3);
            }
            barrelSound(level, pos, state, SoundEvents.BARREL_CLOSE);
        } else if (entity instanceof ShulkerBoxBlockEntity shulker) {
            if (!(state.getBlock() instanceof ShulkerBoxBlock)) {
                skip(cell, CLOSE, "BlockEntity шалкера стоит на блоке другого типа");
                return;
            }
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

    private static void chestEvent(ServerLevel level, BlockPos pos, BlockState state, ChestType type, int openers) {
        level.blockEvent(pos, state.getBlock(), 1, openers);
        if (type == ChestType.SINGLE) {
            return;
        }
        BlockPos other = pos.relative(ChestBlock.getConnectedDirection(state));
        if (!level.isLoaded(other)) {
            return;
        }
        ContainerCell partner = ContainerCells.at(level, other);
        if (!partner.hasChestType()) {
            partnerBroken(level, pos, partner);
            return;
        }
        level.blockEvent(other, level.getBlockState(other).getBlock(), 1, openers);
    }

    private static void chestSounds(
            ServerLevel level, BlockPos pos, BlockState state, ChestType type, SoundEvent sound) {
        playChestSound(level, pos, state, type, sound);
        if (type == ChestType.SINGLE) {
            return;
        }
        BlockPos other = pos.relative(ChestBlock.getConnectedDirection(state));
        if (!level.isLoaded(other)) {
            return;
        }
        ContainerCell partner = ContainerCells.at(level, other);
        if (!partner.hasChestType()) {
            partnerBroken(level, pos, partner);
            return;
        }
        BlockState otherState = level.getBlockState(other);
        playChestSound(level, other, otherState, ContainerCells.chestType(otherState), sound);
    }

    private static void playChestSound(
            ServerLevel level, BlockPos pos, BlockState state, ChestType type, SoundEvent sound) {
        if (type == ChestType.LEFT) {
            return;
        }
        double x = (double) pos.getX() + 0.5;
        double y = (double) pos.getY() + 0.5;
        double z = (double) pos.getZ() + 0.5;
        if (type == ChestType.RIGHT) {
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

    private static boolean broken(ContainerCell cell, String action) {
        ContainerCell.Problem problem = cell.problem();
        if (problem == ContainerCell.Problem.NONE) {
            return false;
        }
        skip(cell, action, problem.text());
        return true;
    }

    private static void skip(ContainerCell cell, String action, String reason) {
        if (CHANGES.changed("container:" + cell.pos(), action + ":" + reason + ":" + cell.describe())) {
            LOGGER.warn("[container] {} пропущен ({}): {} — {}", cell.pos(), action, reason, cell.describe());
        }
    }

    private static void partnerBroken(ServerLevel level, BlockPos pos, ContainerCell partner) {
        ContainerCell main = ContainerCells.at(level, pos);
        if (CHANGES.changed("container-pair:" + main.pos(), partner.describe())) {
            LOGGER.warn(
                    "[container] сундук {} объявлен половиной пары, а соседняя клетка не сундук: {}",
                    main.pos(),
                    partner.describe());
        }
    }
}
