package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.BoardTuning;
import com.theminespirit.core.board.StockView;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.server.level.ServerLevel;

public final class BoardSources {
    private final WoolWatch wool = new WoolWatch();
    private final FishingWatch fishing = new FishingWatch();
    private final HiveWatch hive = new HiveWatch();
    private long lastHerdScan = -1;
    private long lastEnchantCheck = -1;
    private int herdScans;

    private BoardSources() {}

    public static BoardSources create() {
        return new BoardSources();
    }

    public int herdScans() {
        return herdScans;
    }

    public Set<String> tick(
            ServerLevel level, BoardService service, List<SpiritEntity> spirits, StockView stock, long now) {
        if (now < 0) {
            return Set.of();
        }
        Set<String> live = new HashSet<>();
        FirstStepsRunner.tick(level, service, spirits, stock, now);
        ToolStockWatch.tick(level, service, spirits, stock, now);
        EvergreenWatch.tick(level, service, spirits, now);
        FoodWatch.tick(level, service, spirits, stock, now);
        fishing.tick(level, service, spirits, stock, now);
        RepairWatch.tick(level, service, spirits, now);
        PenBuild.tick(level, service, spirits, now);
        WarehouseWatch.tick(level, service, spirits, now);
        MineLevels.tick(level, service, spirits, now);
        if (now - lastEnchantCheck >= BoardTuning.enchantCheckInterval()) {
            lastEnchantCheck = now;
            EnchantRoomWatch.tick(level, service, spirits, stock, now);
        }
        if (now - lastHerdScan >= BoardTuning.herdScanIntervalTicks()) {
            lastHerdScan = now;
            herdScans++;
            HerdWatch.tick(level, service, spirits, now);
        }
        live.addAll(TransferWatch.liveCauses(service));
        wool.tick(level, service, spirits, stock, now);
        if (wool.live()) {
            live.add(WoolWatch.CAUSE);
        }
        hive.tick(level, service, spirits, stock, now);
        if (hive.live()) {
            live.add(HiveWatch.CAUSE);
        }
        live.addAll(CraterWatch.liveCauses(level, service));
        live.addAll(FenceWatch.liveCauses(level, service));
        live.addAll(LeftOreWatch.tick(level, service, spirits, now));
        return live;
    }
}
