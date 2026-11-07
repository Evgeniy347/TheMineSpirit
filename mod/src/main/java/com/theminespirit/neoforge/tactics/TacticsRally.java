package com.theminespirit.neoforge.tactics;

import com.theminespirit.core.coordinator.HelpCall;
import com.theminespirit.core.tactics.RallyTuning;
import com.theminespirit.core.tactics.Situation;
import com.theminespirit.core.tactics.TacticalOption;
import com.theminespirit.core.tactics.TacticalOutcome;
import com.theminespirit.core.tactics.TacticsConfig;
import com.theminespirit.core.tactics.adapter.HelpCallGate;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

final class TacticsRally {
    private final SpiritEntity spirit;
    private final HelpCallGate gate = new HelpCallGate();
    private Optional<HelpCall> call = Optional.empty();
    private int calls;

    TacticsRally(SpiritEntity spirit) {
        this.spirit = spirit;
    }

    void update(long now, TacticalOutcome next, Situation situation) {
        RallyTuning tuning = TacticsConfig.loadDefault().rules().retreat().rally();
        boolean shouldCall =
                next.decision() == TacticalOption.RETREAT && situation.enemies().size() >= tuning.callMinEnemies();
        call = shouldCall
                ? Optional.of(new HelpCall(spirit.getUUID(), situation.self().pos(), now))
                : Optional.empty();
        if (!shouldCall || !gate.ready(now, tuning.callIntervalTicks())) {
            return;
        }
        ServerPlayer player = playerNear(tuning.callRadius());
        if (player == null) {
            return;
        }
        BlockPos at = spirit.blockPosition();
        player.sendSystemMessage(Component.literal("нужна помощь у " + at.getX() + " " + at.getY() + " " + at.getZ()));
        gate.called(now);
        calls++;
    }

    Optional<HelpCall> call() {
        if (call.isEmpty()) {
            return Optional.empty();
        }
        long now = spirit.level().getGameTime();
        RallyTuning tuning = TacticsConfig.loadDefault().rules().retreat().rally();
        return now - call.get().raisedAt() <= tuning.callIntervalTicks() ? call : Optional.empty();
    }

    int calls() {
        return calls;
    }

    private ServerPlayer playerNear(int radius) {
        return spirit.level().getNearestPlayer(spirit, radius) instanceof ServerPlayer player ? player : null;
    }
}
