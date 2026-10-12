package com.theminespirit.neoforge.rest;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.observation.ScanRadius;
import com.theminespirit.core.observation.WorldObservation;
import com.theminespirit.core.rest.ApiException;
import com.theminespirit.core.rest.ErrorCode;
import com.theminespirit.core.rest.PlanningService;
import com.theminespirit.core.rest.RestTuning;
import com.theminespirit.core.rest.ServerThreadCall;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.core.rest.TaskGateway;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.WorldScanner;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;

public final class SpiritTaskGateway implements TaskGateway {
    private final SpiritIndex index;
    private final ServerThreadCall serverCall;
    private final PlanningService planning;

    public SpiritTaskGateway(SpiritIndex index, ServerThreadCall serverCall, PlanningService planning) {
        this.index = index;
        this.serverCall = serverCall;
        this.planning = planning;
    }

    @Override
    public void start(String bot, TaskNode plan) {
        serverCall.call(() -> {
            SpiritEntity spirit = living(bot);
            rejectIfBusy(spirit, bot);
            spirit.startTask(plan);
            return null;
        }, RestTuning.SERVER_CALL_TIMEOUT);
    }

    @Override
    public void startGoal(String bot, String goal, Map<String, Object> params) {
        serverCall.call(() -> {
            SpiritEntity spirit = living(bot);
            rejectIfBusy(spirit, bot);
            WorldObservation observation = spirit.observation().orElseGet(() -> minimal(bot, spirit));
            spirit.startTask(planning.planGoal(goal, params, observation));
            return null;
        }, RestTuning.SERVER_CALL_TIMEOUT);
    }

    @Override
    public void cancel(String bot, String taskId) {
        serverCall.call(() -> {
            Optional<SpiritEntity> spirit = parse(bot).flatMap(index::find);
            spirit.ifPresent(SpiritEntity::cancelTask);
            return null;
        }, RestTuning.SERVER_CALL_TIMEOUT);
    }

    public ServerThreadCall serverCall() {
        return serverCall;
    }

    private static void rejectIfBusy(SpiritEntity spirit, String bot) {
        if (spirit.taskStatus().filter(status -> status == RunStatus.RUNNING).isPresent()) {
            throw new ApiException(ErrorCode.CONFLICT,
                    "Дух '" + bot + "' уже выполняет задачу, поставленную вне REST (команда или планировщик "
                            + "ИИ): отмените её или дождитесь завершения, вытеснения нет",
                    Map.of("id", bot));
        }
    }

    private SpiritEntity living(String bot) {
        SpiritEntity spirit = parse(bot).flatMap(index::find).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND,
                "Дух '" + bot + "' не найден: список живых духов — GET /v1/bots", Map.of("id", bot)));
        if (!spirit.isAlive()) {
            throw new ApiException(ErrorCode.CONFLICT, "Дух '" + bot + "' мёртв: его нельзя поставить в задачу",
                    Map.of("id", bot));
        }
        return spirit;
    }

    private static WorldObservation minimal(String bot, SpiritEntity spirit) {
        BlockPos position = spirit.blockPosition();
        return new WorldObservation(bot, spirit.level().getGameTime(),
                new BlockCoord(position.getX(), position.getY(), position.getZ()),
                WorldScanner.inventoryEntries(spirit.getInventory()), List.of(), List.of(), List.of(),
                ScanRadius.DEFAULT.value());
    }

    private static Optional<UUID> parse(String id) {
        try {
            return Optional.of(UUID.fromString(id));
        } catch (IllegalArgumentException notUuid) {
            return Optional.empty();
        }
    }
}
