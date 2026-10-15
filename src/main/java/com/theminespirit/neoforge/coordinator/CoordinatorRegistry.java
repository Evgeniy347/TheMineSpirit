package com.theminespirit.neoforge.coordinator;

import com.theminespirit.core.coordinator.CoordinatorTuning;
import com.theminespirit.core.coordinator.SettlementKey;
import net.minecraft.server.level.ServerLevel;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class CoordinatorRegistry {
    private static final Map<ServerLevel, LevelSettlements> BY_LEVEL = new ConcurrentHashMap<>();

    private CoordinatorRegistry() {
    }

    public static SpiritCoordinator get(ServerLevel level, SettlementKey key) {
        if (level == null) {
            throw new IllegalArgumentException("level не может быть null: реестр без уровня не отдаёт координатора");
        }
        if (key == null) {
            throw new IllegalArgumentException(
                    "key не может быть null: координатор ведёт своё поселение, а не весь уровень");
        }
        return BY_LEVEL.computeIfAbsent(level, ignored -> new LevelSettlements()).coordinator(level, key);
    }

    public static void remove(ServerLevel level, SettlementKey key) {
        if (level == null || key == null) {
            return;
        }
        LevelSettlements settlements = BY_LEVEL.get(level);
        if (settlements != null) {
            settlements.byKey.remove(key);
        }
    }

    public static boolean beginSnapshotPeriod(ServerLevel level, long tick) {
        if (level == null) {
            throw new IllegalArgumentException("level не может быть null: период снимка идёт по уровню");
        }
        LevelSettlements settlements = BY_LEVEL.computeIfAbsent(level, ignored -> new LevelSettlements());
        if (settlements.lastPeriodTick != -1
                && (tick - settlements.lastPeriodTick) < CoordinatorTuning.SNAPSHOT_INTERVAL_TICKS) {
            return false;
        }
        settlements.lastPeriodTick = tick;
        return true;
    }

    public static void pruneIdle(ServerLevel level, long tick) {
        if (level == null) {
            return;
        }
        LevelSettlements settlements = BY_LEVEL.get(level);
        if (settlements == null) {
            return;
        }
        for (SettlementKey key : settlements.byKey.keySet().stream().toList()) {
            SpiritCoordinator coordinator = settlements.byKey.get(key);
            if (coordinator != null && coordinator.lastTick() >= 0
                    && (tick - coordinator.lastTick()) > CoordinatorTuning.SNAPSHOT_STALE_TICKS) {
                settlements.byKey.remove(key);
            }
        }
    }

    public static void clear() {
        BY_LEVEL.clear();
    }

    public static int size() {
        int total = 0;
        for (LevelSettlements settlements : BY_LEVEL.values()) {
            total += settlements.byKey.size();
        }
        return total;
    }

    private static final class LevelSettlements {
        private long lastPeriodTick = -1;
        private final Map<SettlementKey, SpiritCoordinator> byKey = new ConcurrentHashMap<>();

        private SpiritCoordinator coordinator(ServerLevel level, SettlementKey key) {
            return byKey.computeIfAbsent(key, ignored -> new SpiritCoordinator(level, key));
        }
    }
}
