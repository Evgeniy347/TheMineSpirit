package com.theminespirit.neoforge.rest;

import com.theminespirit.core.board.Board;
import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardHeld;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.observation.WorldObservation;
import com.theminespirit.core.rest.ApiException;
import com.theminespirit.core.rest.BotBoardTask;
import com.theminespirit.core.rest.BotGateway;
import com.theminespirit.core.rest.BotInfo;
import com.theminespirit.core.rest.BotState;
import com.theminespirit.core.rest.CurrentTask;
import com.theminespirit.core.rest.ErrorCode;
import com.theminespirit.core.rest.ObserveRequest;
import com.theminespirit.core.rest.RestTuning;
import com.theminespirit.core.rest.ServerThreadCall;
import com.theminespirit.core.tactics.adapter.DecisionRecord;
import com.theminespirit.core.task.TaskRecord;
import com.theminespirit.core.task.TaskRegistry;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.board.BoardRegistry;
import com.theminespirit.neoforge.board.BoardService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.IEventBus;
import org.jspecify.annotations.Nullable;

public final class SpiritBotGateway implements BotGateway {
    private final MinecraftServer server;
    private final SpiritIndex index;
    private final ServerThreadCall serverCall;
    private final ObserveJobs jobs;
    private final TaskRegistry tasks;
    private final TaskWatcher watcher;
    private final Map<UUID, PositionSeen> positions = new HashMap<>();
    private final Map<UUID, WorkSeen> work = new HashMap<>();

    private record PositionSeen(BlockPos pos, long tick) {}

    private record WorkSeen(
            BotState state,
            @Nullable String activity,
            @Nullable String script,
            long tick) {}

    public SpiritBotGateway(MinecraftServer server, SpiritIndex index, IEventBus bus, TaskRegistry tasks) {
        this.server = server;
        this.index = index;
        this.serverCall = new ServerThreadCall(server);
        this.jobs = new ObserveJobs(bus);
        this.tasks = Objects.requireNonNull(tasks, "tasks не может быть null: current_task живёт в реестре задач");
        this.watcher = new TaskWatcher(bus, index, tasks);
    }

    public TaskRegistry taskRegistry() {
        return tasks;
    }

    public SpiritIndex index() {
        return index;
    }

    public MinecraftServer server() {
        return server;
    }

    public ServerThreadCall serverCall() {
        return serverCall;
    }

    public void stop() {
        serverCall.stop();
        jobs.close();
        watcher.close();
        tasks.closeAll();
    }

    @Override
    public List<BotInfo> bots() {
        return serverCall.call(
                () -> index.snapshot().stream().map(this::info).toList(), RestTuning.SERVER_CALL_TIMEOUT);
    }

    @Override
    public Optional<BotInfo> bot(String id) {
        return serverCall.call(() -> parse(id).flatMap(index::find).map(this::info), RestTuning.SERVER_CALL_TIMEOUT);
    }

    @Override
    public WorldObservation observe(String id, ObserveRequest request) {
        CompletableFuture<WorldObservation> future =
                serverCall.call(() -> startObserve(id, request), RestTuning.SERVER_CALL_TIMEOUT);
        return serverCall.await(future, RestTuning.OBSERVE_TIMEOUT);
    }

    @Override
    public List<DecisionRecord> decisions(String id, int limit) {
        return serverCall.call(
                () -> parse(id)
                        .flatMap(index::find)
                        .orElseThrow(() -> new ApiException(
                                ErrorCode.NOT_FOUND,
                                "Дух '" + id + "' не найден: список живых духов — GET /v1/bots",
                                Map.of("id", id)))
                        .tactics()
                        .decisions(limit),
                RestTuning.SERVER_CALL_TIMEOUT);
    }

    private CompletableFuture<WorldObservation> startObserve(String id, ObserveRequest request) {
        SpiritEntity spirit = parse(id)
                .flatMap(index::find)
                .orElseThrow(() -> new ApiException(
                        ErrorCode.NOT_FOUND,
                        "Дух '" + id + "' не найден: список живых духов — GET /v1/bots",
                        Map.of("id", id)));
        if (!spirit.isAlive()) {
            throw new ApiException(
                    ErrorCode.CONFLICT,
                    "Дух '" + id + "' мёртв: наблюдение недоступно, проверьте state в GET /v1/bots/" + id,
                    Map.of("id", id));
        }
        return jobs.submit(spirit, id, request);
    }

    private static Optional<UUID> parse(String id) {
        try {
            return Optional.of(UUID.fromString(id));
        } catch (IllegalArgumentException notUuid) {
            return Optional.empty();
        }
    }

    private BotInfo info(SpiritEntity spirit) {
        BlockPos position = spirit.blockPosition();
        String id = spirit.getUUID().toString();
        BotBoardTask boardTask = boardTask(spirit);
        String activity = activity(spirit);
        String script = spirit.currentTaskScript().orElse(null);
        String currentTask = CurrentTask.of(
                tasks.activeForBot(id).map(TaskRecord::taskId).orElse(null),
                boardTask == null ? null : boardTask.id(),
                activity,
                script);
        BotState state = state(spirit, boardTask, activity);
        long now = spirit.level().getGameTime();
        return new BotInfo(
                id,
                spirit.displayName(),
                state,
                position.getX(),
                position.getY(),
                position.getZ(),
                spirit.level().dimension().location().toString(),
                currentTask,
                spirit.hunger().satietyFraction(),
                boardTask,
                now,
                positionTick(spirit, position),
                activity,
                script,
                stateSince(spirit.getUUID(), state, activity, script, now));
    }

    private static @Nullable BotBoardTask boardTask(SpiritEntity spirit) {
        if (!(spirit.level() instanceof ServerLevel level)) {
            return null;
        }
        Optional<BoardService> service = BoardRegistry.peek(level);
        if (service.isEmpty()) {
            return null;
        }
        Board board = service.get().board();
        Optional<BoardTicket> held = BoardHeld.of(board, spirit.getUUID());
        if (held.isEmpty()) {
            return null;
        }
        BoardTicket ticket = held.get();
        String status = board.statusOf(ticket.id()).map(Enum::name).orElse("PENDING");
        return new BotBoardTask(ticket.id(), ticket.boardCase().name(), status);
    }

    private long positionTick(SpiritEntity spirit, BlockPos position) {
        long now = spirit.level().getGameTime();
        PositionSeen seen = positions.get(spirit.getUUID());
        if (seen == null || !seen.pos().equals(position)) {
            positions.put(spirit.getUUID(), new PositionSeen(position.immutable(), now));
            return now;
        }
        return seen.tick();
    }

    private long stateSince(UUID id, BotState state, @Nullable String activity, @Nullable String script, long now) {
        WorkSeen seen = work.get(id);
        if (seen == null
                || seen.state() != state
                || !Objects.equals(seen.activity(), activity)
                || !Objects.equals(seen.script(), script)) {
            work.put(id, new WorkSeen(state, activity, script, now));
            return now;
        }
        return seen.tick();
    }

    private static @Nullable String activity(SpiritEntity spirit) {
        if (spirit.level() instanceof ServerLevel level) {
            Optional<BoardService> service = BoardRegistry.peek(level);
            if (service.isPresent()) {
                Optional<BoardCase> occupation = service.get().board().occupation(spirit.getUUID());
                if (occupation.isPresent()) {
                    return occupation.get().name();
                }
            }
        }
        return spirit.currentActivity().orElse(null);
    }

    private static BotState state(SpiritEntity spirit, @Nullable BotBoardTask boardTask, @Nullable String activity) {
        if (!spirit.isAlive()) {
            return BotState.DEAD;
        }
        boolean running = spirit.taskStatus()
                .filter(status -> status == RunStatus.RUNNING)
                .isPresent();
        return running || boardTask != null || activity != null ? BotState.BUSY : BotState.IDLE;
    }
}
