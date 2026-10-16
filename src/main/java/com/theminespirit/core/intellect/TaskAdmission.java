package com.theminespirit.core.intellect;

import com.theminespirit.core.engine.RunStatus;
import java.util.Objects;
import java.util.Optional;

public final class TaskAdmission {
    public enum Verdict {
        START,
        SKIP_SAME,
        SKIP_FOREIGN,
        SKIP_BUSY,
        CANCEL_AND_START
    }

    private TaskAdmission() {
    }

    public static Verdict decide(Optional<String> currentScript, RunStatus status, String issuedScript,
            String requestedScript) {
        Objects.requireNonNull(currentScript,
                "Текущий скрипт не может быть null: отсутствие — это Optional.empty()");
        Objects.requireNonNull(requestedScript, "Запрошенный скрипт не может быть null: что именно выдаём");
        if (currentScript.isPresent()) {
            if (currentScript.get().equals(requestedScript)) {
                return Verdict.SKIP_SAME;
            }
            if (currentScript.get().equals(issuedScript)) {
                return Verdict.CANCEL_AND_START;
            }
            return Verdict.SKIP_FOREIGN;
        }
        if (status == RunStatus.RUNNING) {
            return Verdict.SKIP_BUSY;
        }
        return Verdict.START;
    }
}