package com.theminespirit.core.movement;

import com.theminespirit.core.observation.BlockCoord;
import java.util.Objects;

public final class GateCrossing {
    public enum Phase {
        OPENED,
        INSIDE,
        BEYOND
    }

    private final BlockCoord gate;
    private final BlockCoord from;
    private Phase phase = Phase.OPENED;

    public GateCrossing(BlockCoord gate, BlockCoord from) {
        this.gate = Objects.requireNonNull(gate, "Клетка створки не может быть null: проход считается через неё");
        this.from = Objects.requireNonNull(from, "Клетка духа при открытии не может быть null: по ней ясна сторона");
    }

    public Phase observe(BlockCoord feet) {
        Objects.requireNonNull(feet, "Клетка ног духа не может быть null: проход проверяется по позиции");
        if (inGate(feet)) {
            phase = Phase.INSIDE;
        } else if (phase != Phase.OPENED) {
            phase = across(feet) ? Phase.BEYOND : Phase.OPENED;
        }
        return phase;
    }

    public boolean closable() {
        return phase == Phase.BEYOND;
    }

    private boolean inGate(BlockCoord feet) {
        return feet.x() == gate.x() && feet.z() == gate.z() && Math.abs(feet.y() - gate.y()) <= 1;
    }

    private boolean across(BlockCoord feet) {
        long dot = (long) (feet.x() - gate.x()) * (from.x() - gate.x())
                + (long) (feet.z() - gate.z()) * (from.z() - gate.z());
        return dot <= 0;
    }
}
