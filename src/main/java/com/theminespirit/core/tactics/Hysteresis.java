package com.theminespirit.core.tactics;

import java.util.Optional;
import java.util.Set;

public final class Hysteresis {
    private final ScoreWeights weights;

    public Hysteresis(ScoreWeights weights) {
        this.weights = weights;
    }

    public HoldStatus status(TacticsMemory memory, long now, double hpFraction, Set<TacticalOption> candidates) {
        if (memory.previous().isEmpty()) {
            return HoldStatus.NONE;
        }
        if (!candidates.contains(memory.previous().get())) {
            return HoldStatus.RELEASED_EXCLUDED;
        }
        if (now - memory.decidedAt() >= weights.holdTicks()) {
            return HoldStatus.BONUS;
        }
        return memory.hpWindowRef() - hpFraction > weights.hpDropRelease() ? HoldStatus.RELEASED_HP_DROP
                : HoldStatus.HELD;
    }

    public TacticsMemory remember(TacticsMemory memory, Decision decision, long now, double hpFraction) {
        TacticalOption chosen = decision.option();
        boolean changed = memory.previous().map(option -> option != chosen).orElse(true);
        boolean windowOver = now - memory.hpWindowStart() >= weights.holdTicks();
        return new TacticsMemory(Optional.of(chosen), decision.source(), changed ? now : memory.decidedAt(),
                memory.state(), memory.stateSince(), memory.mode(), memory.orderedMode(), memory.anchor(),
                memory.lastSeenTick(), windowOver ? hpFraction : memory.hpWindowRef(),
                windowOver ? now : memory.hpWindowStart());
    }
}
