package com.theminespirit.neoforge.board;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.board.BoardLog;
import com.theminespirit.core.town.PlotProtection;
import com.theminespirit.core.town.StageSite;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public final class PlotBreakLog {
    public static final String TAG = "[plot-break]";
    public static final int DEDUPE_TICKS = 200;
    private static final int STACK_FRAMES = 3;
    private static final int MAX_KEYS = 4096;
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<String, Long> SEEN = new HashMap<>();

    private PlotBreakLog() {}

    public static void broken(
            ServerLevel level, BlockPos pos, @Nullable Entity breaker, String action, PlotProtection.Ground ground) {
        record(level, pos, breaker, action, PlotProtection.basisText(ground), "");
    }

    public static void brokenBy(
            ServerLevel level, BlockPos pos, @Nullable Entity breaker, String action, String basis) {
        record(level, pos, breaker, action, basis, "");
    }

    public static void refused(
            ServerLevel level,
            BlockPos pos,
            @Nullable Entity breaker,
            String action,
            PlotProtection.Ground ground,
            String reason) {
        record(level, pos, breaker, action, PlotProtection.basisText(ground), reason);
    }

    private static void record(
            ServerLevel level, BlockPos pos, @Nullable Entity breaker, String action, String basis, String reason) {
        StageSite site = SettlementPlotStore.get(level).site().orElse(null);
        if (site == null || !site.protectionCell(pos.getX(), pos.getY(), pos.getZ())) {
            return;
        }
        boolean refusal = !reason.isEmpty();
        if (!refusal && !PlotCellStructure.isStructure(level, pos)) {
            return;
        }
        String subject = refusal ? reason : basis;
        String key =
                pos.getX() + "," + pos.getY() + "," + pos.getZ() + "|" + (refusal ? "refuse|" : "break|") + subject;
        long now = level.getGameTime();
        Long seen = SEEN.get(key);
        if (seen != null && now - seen < DEDUPE_TICKS) {
            return;
        }
        if (SEEN.size() > MAX_KEYS) {
            SEEN.clear();
        }
        SEEN.put(key, now);
        if (refusal) {
            LOGGER.warn(
                    "{} {}: отказ ломать {} в ({}, {}, {}) — {}; {}; задание {}; действие {}; основание {}; причина {}; "
                            + "вызов {}",
                    TAG,
                    site.protectionArea(pos.getX(), pos.getY(), pos.getZ()),
                    level.getBlockState(pos).getBlock(),
                    pos.getX(),
                    pos.getY(),
                    pos.getZ(),
                    site.protectionBuilding(pos.getX(), pos.getY(), pos.getZ()),
                    who(breaker),
                    task(breaker),
                    action,
                    basis,
                    reason,
                    caller());
            return;
        }
        LOGGER.warn(
                "{} {}: сломан {} в ({}, {}, {}) — {}; {}; задание {}; действие {}; основание {}; вызов {}",
                TAG,
                site.protectionArea(pos.getX(), pos.getY(), pos.getZ()),
                level.getBlockState(pos).getBlock(),
                pos.getX(),
                pos.getY(),
                pos.getZ(),
                site.protectionBuilding(pos.getX(), pos.getY(), pos.getZ()),
                who(breaker),
                task(breaker),
                action,
                basis,
                caller());
    }

    static String task(@Nullable Entity breaker) {
        if (!(breaker instanceof SpiritEntity spirit) || !(spirit.level() instanceof ServerLevel server)) {
            return "—";
        }
        String boardTask = BoardRegistry.peek(server)
                .map(service -> BoardLog.culprit(service.board().assignedTo(spirit.getUUID())))
                .orElse(null);
        return BoardLog.culpritOrAction(boardTask, spirit.currentTaskScript().orElse(null));
    }

    private static String who(@Nullable Entity breaker) {
        if (breaker instanceof Player player) {
            return "игрок " + player.getGameProfile().getName();
        }
        if (breaker instanceof SpiritEntity spirit) {
            return "дух " + spirit.getName().getString() + " (" + spirit.getUUID() + ")";
        }
        if (breaker == null) {
            return "взрыв или мир";
        }
        return breaker.getType().toString() + " " + breaker.getUUID();
    }

    private static String caller() {
        StackTraceElement[] stack = new Throwable().getStackTrace();
        StringBuilder text = new StringBuilder();
        int taken = 0;
        for (StackTraceElement frame : stack) {
            String name = frame.getClassName();
            if (name.equals(PlotBreakLog.class.getName())
                    || name.startsWith("com.mojang.logging.")
                    || name.startsWith("org.slf4j.")
                    || name.startsWith("java.")) {
                continue;
            }
            if (taken > 0) {
                text.append(" <- ");
            }
            text.append(name)
                    .append('.')
                    .append(frame.getMethodName())
                    .append("(")
                    .append(frame.getFileName())
                    .append(':')
                    .append(frame.getLineNumber())
                    .append(')');
            if (++taken >= STACK_FRAMES) {
                break;
            }
        }
        return text.isEmpty() ? "неизвестно" : text.toString();
    }
}
