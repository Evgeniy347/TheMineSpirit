package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.BoardTuning;
import com.theminespirit.core.board.StockView;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.List;
import net.minecraft.server.level.ServerLevel;

/**
 * Оркестратор источников доски (периодических): опрос животных, первые шаги, чаровальня.
 */
public final class BoardSources {
    private long lastHerdScan = -1;
    private long lastEnchantCheck = -1;

    private BoardSources() {
    }

    public static BoardSources create() {
        return new BoardSources();
    }

    public void tick(ServerLevel level, BoardService service, List<SpiritEntity> spirits, StockView stock, long now) {
        if (now < 0) {
            return;
        }
        FirstStepsRunner.tick(level, service, spirits, now);
        if (now - lastEnchantCheck >= BoardTuning.enchantCheckInterval()) {
            lastEnchantCheck = now;
            EnchantRoomWatch.tick(level, service, spirits, stock, now);
        }
        if (now - lastHerdScan >= BoardTuning.herdScanIntervalTicks()) {
            lastHerdScan = now;
            HerdWatch.tick(level, service, spirits, now);
        }
    }
}
