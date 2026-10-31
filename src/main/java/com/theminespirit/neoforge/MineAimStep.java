package com.theminespirit.neoforge;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.mining.MineAimFlow;
import com.theminespirit.core.mining.MineCellPlan;
import com.theminespirit.core.mining.MineColumn;
import com.theminespirit.core.mining.SafetyView;
import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

final class MineAimStep {
    enum Outcome {
        RUNNING,
        RESUME,
        FAILED
    }

    record Begin(MineAimFlow.Next next, String failure) {}

    private static final Logger LOGGER = LogUtils.getLogger();

    private final SpiritEntity spirit;
    private final Level level;
    private final SafetyView safety;
    private final List<MineColumn> plan;
    private final MineCellAim aim;
    private @Nullable String failure;

    MineAimStep(SpiritEntity spirit, Level level, SafetyView safety, List<MineColumn> plan) {
        this.spirit = Objects.requireNonNull(spirit, "Дух не может быть null: без него некому подходить к клетке");
        this.level = Objects.requireNonNull(level, "Мир не может быть null: по нему ищется стоянка у клетки");
        this.safety = Objects.requireNonNull(safety, "Вид мира не может быть null: по нему проверяется стоянка");
        this.plan = Objects.requireNonNull(plan, "План колонок не может быть null: по нему считается контекст");
        this.aim = new MineCellAim(spirit);
    }

    Begin begin(BlockCoord cell, MineCellPlan.Seen seen) {
        failure = null;
        MineCellPlan.Plan cellPlan = MineCellPlan.of(
                cell,
                feet(),
                safety,
                seen,
                stand -> !aim.tried(stand) && MineCellAim.occupant(level, spirit, stand) == null);
        MineAimFlow.Next next = MineAimFlow.next(cellPlan.kind());
        switch (next) {
            case READY -> {}
            case AIM -> {
                if (cellPlan.kind() == MineCellPlan.Kind.WAIT) {
                    aim.beginWait(cell, cellPlan.reason());
                } else {
                    aim.beginApproach(cell, cellPlan.stands(), cellPlan.reason());
                }
                LOGGER.info(
                        "Шахта: дух {} идёт к клетке {} ({}): {}",
                        spirit.getStringUUID(),
                        cell,
                        cellPlan.kind(),
                        cellPlan.reason());
            }
            case REFUSE -> failure = "к клетке шахты " + cell + " не подойти: " + cellPlan.reason() + context(cell);
        }
        return new Begin(next, failure == null ? "" : failure);
    }

    Outcome tick() {
        return switch (aim.tick()) {
            case RUNNING -> Outcome.RUNNING;
            case RETRY -> Outcome.RESUME;
            case FAILED -> {
                failure = failedMessage();
                yield Outcome.FAILED;
            }
        };
    }

    String failure() {
        return failure == null ? "подход к клетке шахты провалился" : failure;
    }

    String context(BlockCoord cell) {
        return MineCellAim.context(level, spirit, plan, cell);
    }

    @Nullable
    String occupantText(BlockCoord cell) {
        LivingEntity other = MineCellAim.occupant(level, spirit, cell);
        return other == null ? null : MineCellAim.describe(other);
    }

    void reset() {
        aim.reset();
    }

    private String failedMessage() {
        String reason = aim.failure();
        BlockCoord cell = aim.cell();
        String context = cell == null ? "" : context(cell);
        return (reason == null ? "подход к клетке шахты провалился" : reason) + context;
    }

    private BlockCoord feet() {
        BlockPos pos = spirit.blockPosition();
        return new BlockCoord(pos.getX(), pos.getY(), pos.getZ());
    }
}
