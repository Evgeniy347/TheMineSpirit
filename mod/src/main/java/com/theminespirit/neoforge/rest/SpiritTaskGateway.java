package com.theminespirit.neoforge.rest;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.board.RestTickets;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.observation.ScanRadius;
import com.theminespirit.core.observation.WorldObservation;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.core.rest.ApiException;
import com.theminespirit.core.rest.ErrorCode;
import com.theminespirit.core.rest.PlanningService;
import com.theminespirit.core.rest.RestTuning;
import com.theminespirit.core.rest.ServerThreadCall;
import com.theminespirit.core.rest.TaskGateway;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.WorldScanner;
import com.theminespirit.neoforge.board.BoardRegistry;
import com.theminespirit.neoforge.board.BoardService;
import com.theminespirit.neoforge.board.ExternalTickets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public final class SpiritTaskGateway implements TaskGateway {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final SpiritIndex index;
    private final ServerThreadCall serverCall;
    private final PlanningService planning;

    public SpiritTaskGateway(SpiritIndex index, ServerThreadCall serverCall, PlanningService planning) {
        this.index = index;
        this.serverCall = serverCall;
        this.planning = planning;
    }

    @Override
    public long start(String bot, TaskNode plan) {
        return start(bot, null, null, plan);
    }

    @Override
    public long start(String bot, @Nullable String taskId, @Nullable String title, TaskNode plan) {
        return serverCall.call(
                () -> {
                    SpiritEntity spirit = living(bot);
                    rejectIfBusy(spirit, bot);
                    spirit.startTask(plan);
                    attachBoard(spirit, taskId, title, plan);
                    return spirit.taskGeneration();
                },
                RestTuning.SERVER_CALL_TIMEOUT);
    }

    @Override
    public long startGoal(String bot, String goal, Map<String, Object> params) {
        return startGoal(bot, null, null, goal, params);
    }

    @Override
    public long startGoal(
            String bot, @Nullable String taskId, @Nullable String title, String goal, Map<String, Object> params) {
        return serverCall.call(
                () -> {
                    SpiritEntity spirit = living(bot);
                    rejectIfBusy(spirit, bot);
                    WorldObservation observation = spirit.observation().orElseGet(() -> minimal(bot, spirit));
                    TaskNode plan = planning.planGoal(goal, params, observation);
                    spirit.startTask(plan);
                    attachBoard(spirit, taskId, title == null ? goal : title, plan);
                    return spirit.taskGeneration();
                },
                RestTuning.SERVER_CALL_TIMEOUT);
    }

    @Override
    public void cancelIfPresent(String bot, String taskId) {
        serverCall.call(
                () -> {
                    Optional<SpiritEntity> spirit = parse(bot).flatMap(index::find);
                    spirit.ifPresent(SpiritEntity::cancelTask);
                    return null;
                },
                RestTuning.SERVER_CALL_TIMEOUT);
    }

    public ServerThreadCall serverCall() {
        return serverCall;
    }

    private static void rejectIfBusy(SpiritEntity spirit, String bot) {
        Optional<String> refusal = ExternalTaskStart.refusal(spirit.taskStatus().orElse(null), boardAssigned(spirit));
        if (refusal.isPresent()) {
            throw new ApiException(
                    ErrorCode.CONFLICT, "Дух '" + bot + "': " + refusal.orElseThrow(), Map.of("id", bot));
        }
    }

    private static boolean boardAssigned(SpiritEntity spirit) {
        if (!(spirit.level() instanceof ServerLevel level)) {
            return false;
        }
        return BoardRegistry.peek(level)
                .map(service -> !service.board().assignedTo(spirit.getUUID()).isEmpty())
                .orElse(false);
    }

    private SpiritEntity living(String bot) {
        SpiritEntity spirit = parse(bot)
                .flatMap(index::find)
                .orElseThrow(() -> new ApiException(
                        ErrorCode.NOT_FOUND,
                        "Дух '" + bot + "' не найден: список живых духов — GET /v1/bots",
                        Map.of("id", bot)));
        if (!spirit.isAlive()) {
            throw new ApiException(
                    ErrorCode.CONFLICT, "Дух '" + bot + "' мёртв: его нельзя поставить в задачу", Map.of("id", bot));
        }
        return spirit;
    }

    private static WorldObservation minimal(String bot, SpiritEntity spirit) {
        BlockPos position = spirit.blockPosition();
        return new WorldObservation(
                bot,
                spirit.level().getGameTime(),
                new BlockCoord(position.getX(), position.getY(), position.getZ()),
                WorldScanner.inventoryEntries(spirit.getInventory()),
                List.of(),
                List.of(),
                List.of(),
                ScanRadius.DEFAULT.value());
    }

    private static void attachBoard(
            SpiritEntity spirit, @Nullable String taskId, @Nullable String title, TaskNode plan) {
        if (taskId == null || !(spirit.level() instanceof ServerLevel level)) {
            return;
        }
        Optional<BoardService> service = BoardRegistry.peek(level);
        if (service.isEmpty()) {
            return;
        }
        try {
            ExternalTickets.attach(
                    service.get(),
                    RestTickets.idFor(taskId),
                    title,
                    spirit.getUUID(),
                    new BlockCoord(
                            spirit.blockPosition().getX(),
                            spirit.blockPosition().getY(),
                            spirit.blockPosition().getZ()),
                    plan,
                    spirit.taskGeneration(),
                    level.getGameTime());
        } catch (RuntimeException failure) {
            LOGGER.warn("[board] REST-задача {} не показана на доске: {}", taskId, failure.toString());
        }
    }

    private static Optional<UUID> parse(String id) {
        try {
            return Optional.of(UUID.fromString(id));
        } catch (IllegalArgumentException notUuid) {
            return Optional.empty();
        }
    }
}
