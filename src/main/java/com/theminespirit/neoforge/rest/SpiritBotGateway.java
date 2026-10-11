package com.theminespirit.neoforge.rest;

import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.observation.WorldObservation;
import com.theminespirit.core.rest.ApiException;
import com.theminespirit.core.rest.BotGateway;
import com.theminespirit.core.rest.BotInfo;
import com.theminespirit.core.rest.BotState;
import com.theminespirit.core.rest.ErrorCode;
import com.theminespirit.core.rest.ObserveRequest;
import com.theminespirit.core.rest.RestTuning;
import com.theminespirit.core.rest.ServerThreadCall;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.WorldScanner;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;

public final class SpiritBotGateway implements BotGateway {
    private static final String DEFAULT_NAME = "spirit";

    private final SpiritIndex index;
    private final ServerThreadCall serverCall;

    public SpiritBotGateway(MinecraftServer server, SpiritIndex index) {
        this.index = index;
        this.serverCall = new ServerThreadCall(server);
    }

    public ServerThreadCall serverCall() {
        return serverCall;
    }

    @Override
    public List<BotInfo> bots() {
        return serverCall.call(() -> index.snapshot().stream().map(SpiritBotGateway::info).toList(),
                RestTuning.SERVER_CALL_TIMEOUT);
    }

    @Override
    public Optional<BotInfo> bot(String id) {
        return serverCall.call(() -> parse(id).flatMap(index::find).map(SpiritBotGateway::info),
                RestTuning.SERVER_CALL_TIMEOUT);
    }

    @Override
    public WorldObservation observe(String id, ObserveRequest request) {
        return serverCall.call(() -> observeNow(id, request), RestTuning.OBSERVE_TIMEOUT);
    }

    private WorldObservation observeNow(String id, ObserveRequest request) {
        SpiritEntity spirit = parse(id).flatMap(index::find).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND,
                "Дух '" + id + "' не найден: список живых духов — GET /v1/bots", Map.of("id", id)));
        if (!spirit.isAlive()) {
            throw new ApiException(ErrorCode.CONFLICT,
                    "Дух '" + id + "' мёртв: наблюдение недоступно, проверьте state в GET /v1/bots/" + id,
                    Map.of("id", id));
        }
        BlockPos position = spirit.blockPosition();
        WorldObservation observation = WorldScanner.observe(spirit.level(), id,
                new BlockCoord(position.getX(), position.getY(), position.getZ()), request.radius(),
                spirit.getInventory()).observation();
        if (request.includeEntities()) {
            return observation;
        }
        return new WorldObservation(observation.botId(), observation.tick(), observation.position(),
                observation.inventory(), observation.blocks(), List.of(), observation.containers(),
                observation.radius());
    }

    private static Optional<UUID> parse(String id) {
        try {
            return Optional.of(UUID.fromString(id));
        } catch (IllegalArgumentException notUuid) {
            return Optional.empty();
        }
    }

    private static BotInfo info(SpiritEntity spirit) {
        BlockPos position = spirit.blockPosition();
        return new BotInfo(spirit.getUUID().toString(), name(spirit), state(spirit), position.getX(), position.getY(),
                position.getZ(), spirit.level().dimension().location().toString(), null);
    }

    private static String name(SpiritEntity spirit) {
        return spirit.hasCustomName() ? spirit.getCustomName().getString() : DEFAULT_NAME;
    }

    private static BotState state(SpiritEntity spirit) {
        if (!spirit.isAlive()) {
            return BotState.DEAD;
        }
        return spirit.taskStatus().filter(status -> status == RunStatus.RUNNING).isPresent()
                ? BotState.BUSY : BotState.IDLE;
    }
}
