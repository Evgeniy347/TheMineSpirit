package com.theminespirit.neoforge.board;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.board.BoardTuning;
import com.theminespirit.core.board.SourceGuard;
import com.theminespirit.core.board.StockView;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;

public final class BoardSources {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final WoolWatch wool = new WoolWatch();
    private final FishingWatch fishing = new FishingWatch();
    private final HiveWatch hive = new HiveWatch();
    private final PetWatch pets = new PetWatch();
    private final IdleWatch idle = new IdleWatch();
    private final GraphSource graph = new GraphSource();
    private long lastHerdScan = -1;
    private long lastEnchantCheck = -1;
    private int herdScans;
    private final SourceGuard guard = new SourceGuard(BoardSources::report);

    private BoardSources() {}

    public static BoardSources create() {
        return new BoardSources();
    }

    public boolean failedRound() {
        return guard.failedRound();
    }

    private static void report(String source, RuntimeException error, boolean repeated) {
        if (repeated) {
            LOGGER.warn(
                    "[incident] источник доски {} снова упал: {}; пропущен до следующей пересборки",
                    source,
                    error.getMessage());
        } else {
            LOGGER.error(
                    "[incident] источник доски {} упал, пропущен до следующей пересборки; тик сервера идёт",
                    source,
                    error);
        }
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
        guard.startRound();
        Set<String> craters = guard.call("crater", () -> CraterWatch.liveCauses(level, service), Set.of());
        Set<String> fences = guard.call("fence", () -> FenceWatch.liveCauses(level, service), Set.of());
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
        guard.run("evergreen", () -> EvergreenWatch.tick(level, service, spirits, now));
        guard.run("idle", () -> idle.tick(service, spirits, now));
        guard.run("fishing", () -> fishing.tick(level, service, spirits, stock, now));
        guard.run("repair", () -> RepairWatch.tick(level, service, spirits, now));
        guard.run("pen", () -> PenBuild.tick(level, service, spirits, now));
        guard.run("warehouse", () -> WarehouseWatch.tick(level, service, spirits, now));
        guard.run("demand", () -> DemandWatch.tick(level, service, spirits, now));
        guard.run(
                "repair_audit",
                () -> SettlementPlotStore.get(level)
                        .site()
                        .ifPresent(site -> RepairAuditWatch.tick(level, service, site, now)));
        guard.run("house_levels", () -> HouseLevels.tick(level, service, spirits, now));
        guard.run("mine_levels", () -> MineLevels.tick(level, service, spirits, now));
        if (now - lastEnchantCheck >= BoardTuning.enchantCheckInterval()) {
            lastEnchantCheck = now;
            guard.run("enchant_room", () -> EnchantRoomWatch.tick(level, service, spirits, stock, now));
        }
        if (now - lastHerdScan >= BoardTuning.herdScanIntervalTicks()) {
            lastHerdScan = now;
            herdScans++;
            guard.run("herd", () -> HerdWatch.tick(level, service, spirits, now));
            guard.run("pets", () -> pets.tick(level, spirits, now));
        }
        live.addAll(guard.call("transfer", () -> TransferWatch.liveCauses(service), Set.of()));
        guard.run("wool", () -> wool.tick(level, service, spirits, stock, now));
        if (wool.live()) {
            live.add(WoolWatch.CAUSE);
        }
        guard.run("hive", () -> hive.tick(level, service, spirits, stock, now));
        if (hive.live()) {
            live.add(HiveWatch.CAUSE);
        }
        live.addAll(guard.call("left_ore", () -> LeftOreWatch.tick(level, service, spirits, now), Set.of()));
        live.addAll(guard.call("rescue", () -> RescueWatch.tick(level, service, spirits, now), Set.of()));
        return live;
    }
}
