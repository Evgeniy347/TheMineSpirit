package com.theminespirit.neoforge.board;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

final class TransferNote {
    private static final Logger LOGGER = LogUtils.getLogger();

    private TransferNote() {}

    static void delivered(SpiritEntity donor, BoardTicket ticket, @Nullable TicketTarget target) {
        if (target == null || target.transferTo() == null) {
            return;
        }
        LOGGER.info(
                "[transfer] передано {}×{}: {} → {} (задание {})",
                target.supplyItem(),
                target.supplyCount(),
                donor.displayName(),
                name(donor, target.transferTo()),
                ticket.id());
    }

    private static String name(SpiritEntity donor, UUID recipient) {
        return donor.level() instanceof ServerLevel level && level.getEntity(recipient) instanceof SpiritEntity spirit
                ? spirit.displayName()
                : recipient.toString();
    }
}
