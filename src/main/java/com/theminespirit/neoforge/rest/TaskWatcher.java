package com.theminespirit.neoforge.rest;

import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.engine.ScriptResult;
import com.theminespirit.core.task.SpiritReading;
import com.theminespirit.core.task.TaskReconciler;
import com.theminespirit.core.task.TaskRecord;
import com.theminespirit.core.task.TaskRegistry;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public final class TaskWatcher {
    private final IEventBus bus;
    private final SpiritIndex index;
    private final TaskRegistry registry;
    private final TaskReconciler reconciler;
    private final Consumer<ServerTickEvent.Post> tickListener = event -> tick();
    private final AtomicBoolean closed = new AtomicBoolean();
    private volatile int lastTickPolls;
    private volatile long totalPolls;

    public TaskWatcher(IEventBus bus, SpiritIndex index, TaskRegistry registry) {
        this.bus = bus;
        this.index = index;
        this.registry = registry;
        this.reconciler = new TaskReconciler(registry);
        bus.addListener(tickListener);
    }

    public int lastTickPolls() {
        return lastTickPolls;
    }

    public long totalPolls() {
        return totalPolls;
    }

    public void close() {
        if (closed.compareAndSet(false, true)) {
            bus.unregister(tickListener);
        }
    }

    private void tick() {
        List<TaskRecord> active = registry.watchableRecords();
        lastTickPolls = active.size();
        totalPolls += active.size();
        for (TaskRecord record : active) {
            reconciler.reconcile(record, read(record.bot()));
        }
        reconciler.forgetUnobserved(active);
    }

    private SpiritReading read(String bot) {
        Optional<SpiritEntity> found = parse(bot).flatMap(index::find);
        if (found.isEmpty()) {
            return SpiritReading.absent();
        }
        SpiritEntity spirit = found.get();
        if (!spirit.isAlive()) {
            return SpiritReading.dead();
        }
        RunStatus status = spirit.taskStatus().orElse(null);
        if (status == RunStatus.FAILED) {
            return SpiritReading.failed(spirit.taskFailureReason().orElse(null), spirit.taskFailedAt().orElse(null))
                    .withGeneration(spirit.taskGeneration());
        }
        Object result = spirit.taskResult().map(ScriptResult::jsonValue).orElse(null);
        return SpiritReading.living(status, spirit.taskProgress().orElse(null),
                        spirit.taskFailureReason().orElse(null), result)
                .withGeneration(spirit.taskGeneration());
    }

    private static Optional<UUID> parse(String id) {
        try {
            return Optional.of(UUID.fromString(id));
        } catch (IllegalArgumentException notUuid) {
            return Optional.empty();
        }
    }
}
