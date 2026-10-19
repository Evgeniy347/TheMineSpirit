package com.theminespirit.neoforge.intellect;

import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.intellect.TaskAdmission;
import com.theminespirit.core.intellect.TaskBackoff;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.core.task.SpiritReading;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class TaskStarter {
    private record Issued(String script, long generation) {}

    private final TaskBackoff backoff = new TaskBackoff();
    private final Map<UUID, Issued> issued = new HashMap<>();
    private final Map<UUID, RunStatus> observed = new HashMap<>();
    private int starts;
    private int failures;

    public boolean start(SpiritEntity spirit, String script, TaskNode task, long tick) {
        if (spirit == null) {
            throw new IllegalArgumentException("Задача без духа не запускается: приказ адресован конкретному духу");
        }
        if (script == null || script.isBlank()) {
            throw new IllegalArgumentException(
                    "Задача без скрипта не запускается: откат и учёт ведутся по имени скрипта");
        }
        if (task == null) {
            throw new IllegalArgumentException("Пустой план не запускается: духу нечего исполнять");
        }
        if (tick < 0) {
            throw new IllegalArgumentException("Запуск в тике " + tick + ": время не может быть отрицательным");
        }
        UUID id = spirit.getUUID();
        if (spirit.isRemoved()) {
            issued.remove(id);
            return false;
        }
        if (backoff.blocked(id, script, tick)) {
            return false;
        }
        var status = spirit.taskStatus();
        var current = spirit.currentTaskScript();
        long generation = spirit.taskGeneration();
        Issued own = issued.get(id);
        // Запущенная задача (в т.ч. чужая, ещё не назвавшая скрипт) не перебивается: иначе занятие,
        // выданное в том же серверном тике, затирает задачу и её итог (флак GameTest 16.10).
        var admission = TaskAdmission.decide(
                current,
                status.orElse(null),
                own == null ? null : own.script(),
                own == null ? SpiritReading.UNKNOWN_GENERATION : own.generation(),
                generation,
                script);
        switch (admission) {
            case SKIP_SAME, SKIP_BUSY -> {
                return false;
            }
            case SKIP_FOREIGN -> {
                if (own != null && own.generation() != generation) {
                    issued.remove(id);
                }
                return false;
            }
            case CANCEL_AND_START -> spirit.cancelTask();
            case START -> {
                RunStatus previous = observed.put(id, status.orElse(null));
                if (status.isPresent() && status.get() != RunStatus.RUNNING && previous != status.get()) {
                    // Первый тик терминального исхода: не выдаём новое занятие, иначе итог (успех или
                    // причина падения, их читает наблюдатель, в т.ч. GameTest) затирается в том же
                    // серверном тике. Следующий тик — обычный откат.
                    return false;
                }
                Issued last = issued.remove(id);
                if (status.isPresent() && status.get() == RunStatus.FAILED && last != null) {
                    backoff.noteFailure(id, last.script(), tick);
                    failures++;
                    if (last.script().equals(script)) {
                        return false;
                    }
                }
            }
        }
        spirit.startTask(task);
        issued.put(id, new Issued(script, spirit.taskGeneration()));
        starts++;
        return true;
    }

    public int starts() {
        return starts;
    }

    public int failures() {
        return failures;
    }
}
