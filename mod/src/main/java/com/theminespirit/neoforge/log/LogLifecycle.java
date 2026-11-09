package com.theminespirit.neoforge.log;

import com.theminespirit.core.board.BoardState;
import com.theminespirit.core.board.BoardStatus;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.board.BoardRegistry;
import com.theminespirit.neoforge.board.BoardService;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public final class LogLifecycle {
    public static final int SAMPLE_TICKS = 600;
    public static final int HEALTH_TICKS = 1200;

    private static double worstMspt;

    private LogLifecycle() {}

    public static void register(IEventBus bus) {
        if (bus == null) {
            throw new IllegalArgumentException("bus не может быть null: без шины логи мира не начнутся");
        }
        bus.addListener((ServerStartedEvent event) -> start(event.getServer()));
        bus.addListener((ServerStoppingEvent event) -> stop(event.getServer()));
        bus.addListener(LogLifecycle::onTick);
    }

    private static void start(MinecraftServer server) {
        ModLog.start(server);
        health(server, "start");
    }

    private static void stop(MinecraftServer server) {
        if (ModLog.active()) {
            health(server, "stop");
        }
        ModLog.stop();
    }

    private static void onTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server == null || !ModLog.active()) {
            return;
        }
        double mspt = server.getAverageTickTimeNanos() / 1_000_000.0;
        worstMspt = Math.max(worstMspt, mspt);
        long tick = server.overworld().getGameTime();
        if (tick % HEALTH_TICKS == 0) {
            health(server, "sample");
        }
        if (tick % SAMPLE_TICKS == 0) {
            for (ServerLevel level : server.getAllLevels()) {
                for (var entity : level.getAllEntities()) {
                    if (entity instanceof SpiritEntity spirit && !spirit.isRemoved() && spirit.isAlive()) {
                        SpiritWorldLog.sample(spirit);
                    }
                }
            }
        }
    }

    private static void health(MinecraftServer server, String event) {
        int pending = 0;
        int active = 0;
        int blocked = 0;
        long boardMs = 0;
        long pruneMs = 0;
        int tickets = 0;
        ServerLevel level = server.overworld();
        BoardService service = BoardRegistry.peek(level).orElse(null);
        if (service != null) {
            BoardState state = service.board().snapshot();
            for (BoardState.Entry entry : state.entries()) {
                if (entry.status() == BoardStatus.PENDING) {
                    pending++;
                } else if (entry.status() == BoardStatus.ASSIGNED) {
                    active++;
                } else if (entry.status() == BoardStatus.BLOCKED) {
                    blocked++;
                }
            }
            blocked = Math.max(blocked, state.blocked().size());
            tickets = state.entries().size();
            boardMs = service.meter().rebuildMillis();
            pruneMs = service.meter().pruneMillis();
        }
        double mspt = "sample".equals(event) ? server.getAverageTickTimeNanos() / 1_000_000.0 : 0;
        SpiritWorldLog.health(server, mspt, worstMspt, 0, pending, active, blocked, boardMs, pruneMs, tickets, event);
        if ("sample".equals(event)) {
            worstMspt = 0;
        }
    }
}
