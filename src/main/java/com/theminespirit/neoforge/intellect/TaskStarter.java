package com.theminespirit.neoforge.intellect;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.board.StateChangeLog;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.incident.InventoryText;
import com.theminespirit.core.intellect.BackgroundWork;
import com.theminespirit.core.intellect.IntellectTuning;
import com.theminespirit.core.intellect.TaskAdmission;
import com.theminespirit.core.intellect.TaskBackoff;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.core.task.SpiritReading;
import com.theminespirit.neoforge.IncidentSnapshots;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;

public final class TaskStarter {
    private static final Logger LOGGER = LogUtils.getLogger();

    private record Issued(String script, long generation) {}

    private final TaskBackoff backoff = new TaskBackoff();
    private final Map<UUID, Issued> issued = new HashMap<>();
    private final Map<UUID, RunStatus> observed = new HashMap<>();
    private final StateChangeLog reported = new StateChangeLog();
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
            reported.forget(id.toString());
            return false;
        }
        if (backoff.blocked(id, script, tick)) {
            return false;
        }
        var status = spirit.taskStatus();
        var current = spirit.currentTaskScript();
        long generation = spirit.taskGeneration();
        Issued own = issued.get(id);
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
                    if (status.get() == RunStatus.SUCCEEDED) {
                        reported.forget(id.toString());
                    }
                    return false;
                }
                Issued last = issued.remove(id);
                if (status.isPresent()
                        && status.get() == RunStatus.FAILED
                        && last != null
                        && last.generation() == generation) {
                    backoff.noteFailure(id, last.script(), tick);
                    failures++;
                    logFailure(spirit, id, last.script(), status.orElseThrow());
                    if (last.script().equals(script)) {
                        return false;
                    }
                }
            }
        }
        if (BackgroundWork.preemptible(script)) {
            spirit.startBackgroundTask(task);
        } else {
            spirit.startTask(task);
        }
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

    private void logFailure(SpiritEntity spirit, UUID id, String script, RunStatus status) {
        String reason = spirit.taskFailureReason().orElse("причина не записана");
        String inventory = InventoryText.of(IncidentSnapshots.inventoryOf(spirit));
        if (!reported.changed(id.toString(), script + "|" + reason + "|" + inventory)) {
            return;
        }
        LOGGER.warn(
                "[incident] дух {} ({}) провалил задачу '{}': {}; состояние {}; пауза {} тиков, "
                        + "повтор того же провала подавлен до смены инвентаря; {}",
                spirit.getName().getString(),
                id,
                script,
                reason,
                status,
                IntellectTuning.loadDefault().failBackoffTicks(),
                incident(spirit, script));
    }

    private static String incident(SpiritEntity spirit, String script) {
        return spirit.level() instanceof ServerLevel level
                ? IncidentSnapshots.record(level, spirit, "задача " + script)
                : "-";
    }
}
