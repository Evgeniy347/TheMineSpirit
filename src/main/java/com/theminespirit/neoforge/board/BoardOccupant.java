package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.BoardActivities;
import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

final class BoardOccupant {
    private static final String DIG_OUT = "dig_out";

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
            SpiritEntity spirit, boolean ownActivity, @Nullable BoardCase launchedCase, boolean assigned) {
        if (spirit.isRemoved()) {
            return Optional.empty();
        }
        boolean running = spirit.taskStatus().orElse(null) == RunStatus.RUNNING;
        String script = spirit.currentTaskScript().orElse(null);
        String target = spirit.currentPlan().map(BoardActivities::targetOf).orElse(null);
        if (DIG_OUT.equals(script) && running) {
            return Optional.of(new Occupation(BoardCase.ACTIVITY_ESCAPE, script, target));
        }
        if (spirit.tactics().wantsControl() && !assigned) {
            Optional<BoardCase> tactic = BoardActivities.caseOf(spirit.tactics().action());
            if (tactic.isPresent()) {
                return Optional.of(new Occupation(tactic.get(), script, target));
            }
        }
        if (running && ownActivity && launchedCase != null) {
            return Optional.of(new Occupation(launchedCase, script, target));
        }
        if (assigned) {
            return Optional.empty();
        }
        if (running) {
            BoardCase card = BoardActivities.caseOfScript(script).orElse(BoardCase.ACTIVITY_EXTERNAL);
            return Optional.of(new Occupation(card, script, target));
        }
        if (spirit.isDefenceAssigned()) {
            return Optional.of(new Occupation(BoardCase.ACTIVITY_DEFEND, script, target));
        }
        return Optional.empty();
    }
}
