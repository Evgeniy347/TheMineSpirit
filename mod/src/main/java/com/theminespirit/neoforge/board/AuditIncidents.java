package com.theminespirit.neoforge.board;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.board.BoardLog;
import com.theminespirit.core.board.api.BoardCase;
import com.theminespirit.core.board.api.ConfigStamp;
import com.theminespirit.core.board.audit.AuditMiss;
import com.theminespirit.neoforge.log.SpiritLog;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;

final class AuditIncidents {
    private static final Logger LOGGER = LogUtils.getLogger();

    private AuditIncidents() {}

    static void report(ServerLevel level, AuditMiss miss) {
        String text = miss.text();
        LOGGER.warn("[audit-miss] {}", text);
        SpiritLog.auditMiss(
                level,
                new BlockPos(miss.cell().x(), miss.cell().y(), miss.cell().z()),
                miss.cached(),
                miss.actual(),
                text);
        BoardRegistry.get(level)
                .journal()
                .recordEntry(BoardLog.note(
                        "audit-miss:" + miss.cell().x() + "," + miss.cell().y() + ","
                                + miss.cell().z(),
                        BoardCase.ACTIVITY_EXTERNAL,
                        level.getGameTime(),
                        List.of("audit-miss", text),
                        null,
                        ConfigStamp.current()));
    }
}
