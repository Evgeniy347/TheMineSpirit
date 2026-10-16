package com.theminespirit.neoforge.intellect;

import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.intellect.TaskBackoff;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class TaskStarter {
    private final TaskBackoff backoff = new TaskBackoff();
    private final Map<UUID, String> issued = new HashMap<>();
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
        var current = spirit.currentTaskScript();
        if (current.isPresent()) {
            if (current.get().equals(script)) {
                return false;
            }
            if (!current.get().equals(issued.get(id))) {
                return false;
            }
            spirit.cancelTask();
        } else {
            var status = spirit.taskStatus();
            String last = issued.remove(id);
            if (status.isPresent() && status.get() == RunStatus.FAILED && last != null) {
                backoff.noteFailure(id, last, tick);
                failures++;
                if (last.equals(script)) {
                    return false;
                }
            }
        }
        spirit.startTask(task);
        issued.put(id, script);
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
