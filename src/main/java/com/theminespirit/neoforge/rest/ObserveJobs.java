package com.theminespirit.neoforge.rest;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.observation.ScanTuning;
import com.theminespirit.core.observation.WorldObservation;
import com.theminespirit.core.rest.ApiException;
import com.theminespirit.core.rest.ErrorCode;
import com.theminespirit.core.rest.ObserveRequest;
import com.theminespirit.core.rest.RestTuning;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.WorldScanner;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public final class ObserveJobs {
    private static final AtomicInteger LIVE = new AtomicInteger();

    private final IEventBus bus;
    private final Consumer<ServerTickEvent.Post> tickListener = event -> tick();
    private final List<Job> active = new ArrayList<>();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicInteger tickCalls = new AtomicInteger();
    private volatile int activeCount;
    private volatile int peakActive;
    private volatile int maxReadsPerTick;

    public ObserveJobs(IEventBus bus) {
        this.bus = bus;
        bus.addListener(tickListener);
        LIVE.incrementAndGet();
    }

    public static int liveCount() {
        return LIVE.get();
    }

    public CompletableFuture<WorldObservation> submit(SpiritEntity spirit, String botId, ObserveRequest request) {
        if (active.size() >= RestTuning.MAX_OBSERVES) {
            throw new ApiException(ErrorCode.RATE_LIMITED,
                    "Слишком много снимков одновременно: занято " + active.size() + " из " + RestTuning.MAX_OBSERVES
                            + ", повторите запрос позже",
                    Map.of("active", active.size(), "max", RestTuning.MAX_OBSERVES));
        }
        active.add(new Job(spirit, botId, request));
        activeCount = active.size();
        peakActive = Math.max(peakActive, activeCount);
        return active.get(active.size() - 1).future;
    }

    public int activeCount() {
        return activeCount;
    }

    public int tickCalls() {
        return tickCalls.get();
    }

    public int peakActive() {
        return peakActive;
    }

    public int maxReadsPerTick() {
        return maxReadsPerTick;
    }

    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        LIVE.decrementAndGet();
        bus.unregister(tickListener);
        for (Job job : List.copyOf(active)) {
            job.fail(new ApiException(ErrorCode.INTERNAL,
                    "Сервер останавливается, наблюдение прервано: повторите запрос после перезапуска",
                    Map.of("id", job.botId)));
        }
        active.clear();
        activeCount = 0;
    }

    private void tick() {
        tickCalls.incrementAndGet();
        for (Job job : List.copyOf(active)) {
            if (job.future.isDone()) {
                active.remove(job);
                continue;
            }
            if (job.spirit.isRemoved()) {
                job.fail(new ApiException(ErrorCode.NOT_FOUND,
                        "Дух '" + job.botId + "' исчез во время скана: список живых духов — GET /v1/bots",
                        Map.of("id", job.botId)));
                active.remove(job);
                continue;
            }
            int before = job.session.examinedPositions();
            job.session.step(ScanTuning.SLICES_PER_TICK);
            maxReadsPerTick = Math.max(maxReadsPerTick, job.session.examinedPositions() - before);
            if (job.session.done()) {
                job.complete();
                active.remove(job);
            }
        }
        activeCount = active.size();
    }

    private static final class Job {
        private final SpiritEntity spirit;
        private final String botId;
        private final WorldScanner.Session session;
        private final boolean includeEntities;
        private final CompletableFuture<WorldObservation> future = new CompletableFuture<>();

        Job(SpiritEntity spirit, String botId, ObserveRequest request) {
            this.spirit = spirit;
            this.botId = botId;
            BlockPos position = spirit.blockPosition();
            this.session = new WorldScanner.Session(spirit.level(), botId,
                    new BlockCoord(position.getX(), position.getY(), position.getZ()), request.radius(),
                    request.blockFilter(), spirit.getInventory());
            this.includeEntities = request.includeEntities();
        }

        void complete() {
            WorldObservation observation = session.result().observation();
            future.complete(includeEntities ? observation : withoutEntities(observation));
        }

        void fail(ApiException error) {
            future.completeExceptionally(error);
        }
    }

    private static WorldObservation withoutEntities(WorldObservation observation) {
        return new WorldObservation(observation.botId(), observation.tick(), observation.position(),
                observation.inventory(), observation.blocks(), List.of(), observation.containers(),
                observation.radius());
    }
}
