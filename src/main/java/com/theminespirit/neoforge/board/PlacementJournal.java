package com.theminespirit.neoforge.board;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.board.BoardActivities;
import com.theminespirit.core.board.BoardLog;
import com.theminespirit.core.board.ConfigStamp;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.neoforge.SpiritEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;

/**
 * Установка блока духом вне плана стройки (станция, сундук склада) — строка в логе и заметка в журнале доски: кто,
 * в какой задаче, где и зачем (INC-54: три пустых сундука вне участка, кто поставил — не видно).
 */
public final class PlacementJournal {
    private static final Logger LOGGER = LogUtils.getLogger();

    private PlacementJournal() {}

    public static void placed(SpiritEntity spirit, String block, BlockPos pos, String reason) {
        String task = spirit.currentPlan().map(BoardActivities::scriptOf).orElse("нет");
        LOGGER.info(
                "[place] {} ({}) поставил {} в {}: {}",
                spirit.getName().getString(),
                task,
                block,
                pos.toShortString(),
                reason);
        if (spirit.level() instanceof ServerLevel level) {
            BoardRegistry.peek(level)
                    .ifPresent(service -> service.journal()
                            .recordEntry(BoardLog.placement(
                                    spirit.getUUID().toString(),
                                    task,
                                    block,
                                    new BlockCoord(pos.getX(), pos.getY(), pos.getZ()),
                                    reason,
                                    level.getGameTime(),
                                    ConfigStamp.current())));
        }
    }
}
