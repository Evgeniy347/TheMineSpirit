package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.BoardActivities;
import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

final class BoardOccupant {
    private BoardOccupant() {}

    record Occupation(
            BoardCase boardCase,
            @Nullable String script,
            @Nullable String target) {
        Occupation {
            Objects.requireNonNull(
                    boardCase, "Кейс занятия не может быть null: карточка доски обязана называть вид действия");
        }
    }

    static Optional<Occupation> of(
            SpiritEntity spirit,
            boolean ownActivity,
            @Nullable BoardCase launchedCase,
            @Nullable String launchedScript,
            @Nullable String launchedTarget,
            boolean assigned) {
        if (spirit.isRemoved()) {
            return Optional.empty();
        }
        Optional<TaskNode> plan = spirit.currentPlan();
        boolean running = spirit.taskStatus().orElse(null) == RunStatus.RUNNING
                || spirit.currentTaskScript().isPresent();
        String script = ownActivity && launchedScript != null
                ? launchedScript
                : BoardActivities.cardScript(spirit.currentTaskScript().orElse(null), plan.orElse(null));
        String target = ownActivity && launchedTarget != null
                ? launchedTarget
                : plan.map(BoardActivities::targetOf).orElse(null);
        return new BoardActivities.CardFacts(
                        spirit.isRemoved(),
                        running,
                        spirit.tactics().wantsControl(),
                        spirit.tactics().action(),
                        ownActivity,
                        launchedCase,
                        assigned,
                        spirit.isDefenceAssigned(),
                        script)
                .card()
                .map(card -> new Occupation(card, script, target));
    }
}
