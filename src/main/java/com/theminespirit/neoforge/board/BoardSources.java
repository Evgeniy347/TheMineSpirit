package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.BoardTuning;
import com.theminespirit.core.board.StockView;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.server.level.ServerLevel;

/**
 * Оркестратор источников доски (периодических): опрос животных, первые шаги, чаровальня,
 * воронки и заборы. Возвращает живые причины пересборки: по ним доска снимает задания,
 * чья причина отпала.
 */
public final class BoardSources {
    private long lastHerdScan = -1;
    private long lastEnchantCheck = -1;

    private BoardSources() {
    }

    public static BoardSources create() {
        return new BoardSources();
    }

    public Set<String> tick(ServerLevel level, BoardService service, List<SpiritEntity> spirits,
            StockView stock, long now) {
        if (now < 0) {
            return Set.of();
        }
        Set<String> live = new HashSet<>();
        FirstStepsRunner.tick(level, service, spirits, stock, now);
        PenBuild.tick(level, service, spirits, now);
        if (now - lastEnchantCheck >= BoardTuning.enchantCheckInterval()) {
            lastEnchantCheck = now;
            EnchantRoomWatch.tick(level, service, spirits, stock, now);
        }
        if (now - lastHerdScan >= BoardTuning.herdScanIntervalTicks()) {
            lastHerdScan = now;
            HerdWatch.tick(level, service, spirits, now);
        }
        live.addAll(TransferWatch.liveCauses(service));
        live.addAll(CraterWatch.liveCauses(level, service));
        live.addAll(FenceWatch.liveCauses(level, service));
        return live;
    }
}
