package com.theminespirit.neoforge;

import com.theminespirit.core.mining.MineColumn;
import com.theminespirit.core.mining.MineStepStands;
import com.theminespirit.core.mining.SafetyView;
import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

final class MineStepWalker {
    enum State {
        MOVING,
        ARRIVED,
        VISIBLE,
        FAILED
    }

    private static final double ARRIVAL = 0.6;

    private final Approach approach;
    private final SafetyView safety;
    private final List<BlockCoord> stands = new ArrayList<>();
    private int choice;
    private boolean started;
    private String refusal = "";

    MineStepWalker(Approach approach, SafetyView safety) {
        this.approach = Objects.requireNonNull(approach, "Подход не может быть null: без него дух не дойдёт");
        this.safety = Objects.requireNonNull(safety, "Вид мира не может быть null: по нему выбирается стоянка");
    }

    State tick(MineColumn column, @Nullable BlockCoord parentStand, BlockCoord feet) {
        if (!started) {
            MineStepStands.Choice picked = MineStepStands.of(column, parentStand, feet, safety);
            if (!picked.found()) {
                refusal = picked.reason();
                return MineStepStands.visibleFrom(column, feet, safety) ? State.VISIBLE : State.FAILED;
            }
            stands.clear();
            stands.addAll(picked.stands());
            choice = 0;
            begin();
        }
        return switch (approach.tick()) {
            case MOVING -> State.MOVING;
            case ARRIVED -> {
                reset();
                yield State.ARRIVED;
            }
            case STUCK, TIMEOUT -> next(column, feet);
        };
    }

    String refusal() {
        return refusal;
    }

    void reset() {
        stands.clear();
        started = false;
        choice = 0;
    }

    private State next(MineColumn column, BlockCoord feet) {
        BlockCoord tried = stands.get(choice);
        String reason = approach.refusal() == null ? "путь не найден" : approach.refusal();
        refusal = tried + ": " + reason;
        started = false;
        choice++;
        if (choice < stands.size()) {
            begin();
            return State.MOVING;
        }
        reset();
        return MineStepStands.visibleFrom(column, feet, safety) ? State.VISIBLE : State.FAILED;
    }

    private void begin() {
        BlockCoord stand = stands.get(choice);
        approach.begin(new Vec3(stand.x() + 0.5, stand.y(), stand.z() + 0.5), ARRIVAL);
        started = true;
    }
}
