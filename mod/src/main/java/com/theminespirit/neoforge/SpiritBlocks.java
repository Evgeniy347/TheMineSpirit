package com.theminespirit.neoforge;

import com.theminespirit.core.log.LogCause;
import com.theminespirit.core.tactics.adapter.Visibility;
import com.theminespirit.neoforge.log.SpiritLog;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public final class SpiritBlocks {
    private SpiritBlocks() {}

    public static boolean place(ServerLevel level, BlockPos pos, BlockState state, SpiritEntity spirit, String basis) {
        return place(level, pos, state, spirit, basis, "place");
    }

    public static boolean restore(
            ServerLevel level, BlockPos pos, BlockState state, SpiritEntity spirit, String basis) {
        return place(level, pos, state, spirit, basis, "restore");
    }

    public static boolean destroy(ServerLevel level, BlockPos pos, boolean drop, SpiritEntity spirit, String basis) {
        BlockState before = level.getBlockState(pos);
        if (BreakSight.visible(spirit, pos, false) != Visibility.CLEAR) {
            SpiritLog.blockRefused(
                    level, spirit, pos, "break", basis, "блок не виден из глаз духа: ломать сквозь стену нельзя");
            return false;
        }
        boolean changed = level.destroyBlock(pos, drop, spirit);
        SpiritLog.block(
                level,
                spirit,
                "spirit",
                spirit.displayName(),
                "break",
                before,
                level.getBlockState(pos),
                pos,
                basis,
                LogCause.BREAK,
                "");
        return changed;
    }

    public static void playerBreak(ServerLevel level, BlockPos pos, Player player, String basis) {
        BlockState before = level.getBlockState(pos);
        SpiritLog.playerBlock(
                level, player.getGameProfile().getName(), "break", before, Blocks.AIR.defaultBlockState(), pos, basis);
    }

    public static void playerPlace(ServerLevel level, BlockPos pos, BlockState placed, Player player, String basis) {
        BlockState before = level.getBlockState(pos);
        if (before.equals(placed)) {
            before = Blocks.AIR.defaultBlockState();
        }
        SpiritLog.playerBlock(level, player.getGameProfile().getName(), "place", before, placed, pos, basis);
    }

    public static void explosion(ServerLevel level, BlockPos pos, BlockState before) {
        SpiritLog.block(
                level,
                null,
                "world",
                "взрыв",
                "break",
                before,
                Blocks.AIR.defaultBlockState(),
                pos,
                "взрыв",
                LogCause.BREAK,
                "");
    }

    public static void refuse(
            ServerLevel level, SpiritEntity spirit, BlockPos pos, String action, String basis, String reason) {
        SpiritLog.blockRefused(level, spirit, pos, action, basis, reason);
    }

    private static boolean place(
            ServerLevel level, BlockPos pos, BlockState state, SpiritEntity spirit, String basis, String action) {
        BlockState before = level.getBlockState(pos);
        boolean changed = level.setBlock(pos, state, Block.UPDATE_ALL);
        SpiritLog.block(
                level,
                spirit,
                "spirit",
                spirit.displayName(),
                action,
                before,
                level.getBlockState(pos),
                pos,
                basis,
                "restore".equals(action) ? LogCause.RESTORE : LogCause.PLACE,
                "");
        return changed;
    }
}
