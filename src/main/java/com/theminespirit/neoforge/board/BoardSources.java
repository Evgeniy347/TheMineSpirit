package com.theminespirit.neoforge.board;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.BoardTuning;
import com.theminespirit.core.board.SourceFailureLog;
import com.theminespirit.core.board.SourceGuard;
import com.theminespirit.core.board.StockView;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.board.graph.GraphDecisions;
import com.theminespirit.core.board.graph.GraphFailureMemory;
import com.theminespirit.core.board.graph.GraphNotes;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public final class BoardSources {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final DigOutAction digOut = new DigOutAction();
    private final TransferDriftWatch transferDrift = new TransferDriftWatch();
    private final DemandWatch demand = new DemandWatch();
    private final GraphSource graph = new GraphSource();
    private final SourceFailureLog failures = new SourceFailureLog(BoardTuning.logSummaryTicks());
    private final GraphFailureMemory graphFailures = new GraphFailureMemory();
    private final GraphNotes notes = new GraphNotes();
    private final SourceGuard guard = new SourceGuard(this::report);
    private long roundNow;

    private BoardSources() {}

    public static BoardSources create() {
        return new BoardSources();
    }

    public boolean failedRound() {
        return guard.failedRound();
    }

    public GraphDecisions graphDecisions() {
        return graph.decisionLog.decisions();
    }

    public GraphFailureMemory graphFailures() {
        return graphFailures;
    }

    public boolean due(String key, String text, long now) {
        return notes.due(key, text, now);
    }

    public @Nullable String supplyOf(BoardTicket ticket, Map<String, TicketTarget> targets) {
        TicketTarget target = targets.get(ticket.id());
        return target == null ? null : target.supplyItem();
    }

    public void graphFailure(BoardTicket ticket, @Nullable TicketTarget target, @Nullable String reason, long now) {
        String node = GraphFailureMemory.node(ticket.id(), ticket.cause());
        if (node == null) {
            return;
        }
        String where = target == null
                ? ""
                : GraphFailureMemory.target(
                        target.target().x(),
                        target.target().y(),
                        target.target().z());
        graphFailures.note(node, where, reason, now);
    }

    private void report(String source, RuntimeException error, boolean repeated) {
        failures.observe(source, String.valueOf(error.getMessage()), roundNow).ifPresent(summary -> {
            if (summary.changed()) {
                LOGGER.error(
                        "[incident] РёСЃС‚РѕС‡РЅРёРє РґРѕСЃРєРё {} {} СѓРїР°Р», РїСЂРѕРїСѓС‰РµРЅ РґРѕ СЃР»РµРґСѓСЋС‰РµР№ РїРµСЂРµСЃР±РѕСЂРєРё; С‚РёРє СЃРµСЂРІРµСЂР° РёРґС‘С‚",
                        source,
                        repeated ? "СЃРЅРѕРІР°" : "",
                        error);
                return;
            }
            LOGGER.warn(
                    "[incident] СЃРІРѕРґРєР°: РёСЃС‚РѕС‡РЅРёРє РґРѕСЃРєРё {} РїР°РґР°Р» {} СЂР°Р· Р·Р° РѕРєРЅРѕ СЃ С‚РѕР№ Р¶Рµ РѕС€РёР±РєРѕР№: {}",
                    source,
                    summary.count(),
                    error.getMessage());
        });
    }

    public Set<String> tick(
            ServerLevel level, BoardService service, List<SpiritEntity> spirits, StockView stock, long now) {
        if (now < 0) {
            return Set.of();
        }
        Set<String> live = new HashSet<>();
        roundNow = now;
        guard.startRound();
        Set<String> craters = guard.call("crater", () -> CraterWatch.liveCauses(level, service), Set.of());
        Set<String> fences = guard.call("fence", () -> FenceWatch.liveCauses(level), Set.of());
        live.addAll(craters);
        live.addAll(fences);
        live.addAll(guard.call(
                "graph",
                () -> graph.tick(
                        level,
                        service,
                        spirits,
                        stock,
                        GraphSource.events(level, service, spirits, craters, fences),
                        now),
                Set.of()));
        guard.run("demand", () -> demand.tick(level, service, spirits, now));
        live.addAll(guard.call("transfer", () -> TransferWatch.liveCauses(service), Set.of()));
        guard.run("transfer_drift", () -> transferDrift.tick(level, service, spirits));
        live.addAll(guard.call("left_ore", () -> LeftOreWatch.tick(level), Set.of()));
        guard.run("dig_out", () -> digOut.tick(level, service, spirits, now));
        return live;
    }
}
