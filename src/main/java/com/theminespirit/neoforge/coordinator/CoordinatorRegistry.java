package com.theminespirit.neoforge.coordinator;

import com.theminespirit.core.coordinator.CoordinatorTuning;
import com.theminespirit.core.coordinator.SettlementKey;
import com.theminespirit.core.intellect.InvitationAnswer;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

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

    public static boolean answerInvite(ServerLevel level, String expedition, String answer,
            ServerPlayer player, long now) {
        if (level == null || expedition == null || answer == null || player == null) {
            throw new IllegalArgumentException(
                    "Ответ без уровня, похода или игрока не принять: кнопки зовут конкретную команду");
        }
        LevelSettlements settlements = BY_LEVEL.get(level);
        if (settlements == null) {
            return false;
        }
        for (SpiritCoordinator coordinator : settlements.byKey.values().stream().toList()) {
            if ("join".equals(answer)) {
                if (expedition.equals(idOf(coordinator))) {
                    coordinator.intellect().expeditions().accept(player,
                            membersOf(level, coordinator), now);
                    return true;
                }
                continue;
            }
            InvitationAnswer parsed =
                    "wait".equals(answer) ? InvitationAnswer.WAIT_MORE : InvitationAnswer.GO_WITHOUT;
            if (coordinator.intellect().expeditions().onAnswer(expedition, parsed, now)) {
                return true;
            }
        }
        return false;
    }

    private static String idOf(SpiritCoordinator coordinator) {
        return coordinator.intellect().expeditions().expeditionId() == null ? ""
                : coordinator.intellect().expeditions().expeditionId().toString();
    }

    private static List<SpiritEntity> membersOf(ServerLevel level, SpiritCoordinator coordinator) {
        List<SpiritEntity> members = new ArrayList<>();
        for (var snapshot : coordinator.snapshot().spirits()) {
            if (level.getEntity(snapshot.id()) instanceof SpiritEntity spirit && !spirit.isRemoved()) {
                members.add(spirit);
            }
        }
        return members;
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
