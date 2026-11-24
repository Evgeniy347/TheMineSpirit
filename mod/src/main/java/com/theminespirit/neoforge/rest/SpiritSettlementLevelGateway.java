package com.theminespirit.neoforge.rest;

import com.theminespirit.core.rest.RestTuning;
import com.theminespirit.core.rest.ServerThreadCall;
import com.theminespirit.core.rest.SettlementLevelChange;
import com.theminespirit.core.rest.SettlementLevelChangeResult;
import com.theminespirit.core.rest.SettlementLevelGateway;
import com.theminespirit.core.rest.SettlementLevelPreview;
import com.theminespirit.core.rest.SettlementLevelView;
import com.theminespirit.neoforge.board.SettlementLevelService;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.server.MinecraftServer;

public final class SpiritSettlementLevelGateway implements SettlementLevelGateway {
    private final MinecraftServer server;
    private final ServerThreadCall serverCall;

    public SpiritSettlementLevelGateway(MinecraftServer server, ServerThreadCall serverCall) {
        this.server = Objects.requireNonNull(server, "server не может быть null: уровень живёт в мире");
        this.serverCall = Objects.requireNonNull(
                serverCall, "serverCall не может быть null: уровень читается и меняется на серверном потоке");
    }

    @Override
    public Optional<SettlementLevelView> view() {
        return serverCall.call(
                () -> SettlementLevelService.of(server.overworld()).view(), RestTuning.SERVER_CALL_TIMEOUT);
    }

    @Override
    public SettlementLevelPreview preview() {
        return serverCall.call(
                () -> SettlementLevelService.of(server.overworld()).preview(), RestTuning.SERVER_CALL_TIMEOUT);
    }

    @Override
    public SettlementLevelChangeResult set(SettlementLevelChange change) {
        return serverCall.call(
                () -> SettlementLevelService.of(server.overworld()).set(change), RestTuning.SERVER_CALL_TIMEOUT);
    }
}
