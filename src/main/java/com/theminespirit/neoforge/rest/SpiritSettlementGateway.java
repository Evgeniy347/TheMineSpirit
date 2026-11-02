package com.theminespirit.neoforge.rest;

import com.theminespirit.core.board.StageView;
import com.theminespirit.core.rest.RestTuning;
import com.theminespirit.core.rest.ServerThreadCall;
import com.theminespirit.core.rest.SettlementChange;
import com.theminespirit.core.rest.SettlementGateway;
import com.theminespirit.core.rest.SettlementStageChange;
import com.theminespirit.neoforge.board.SettlementStageService;
import java.util.Objects;
import net.minecraft.server.MinecraftServer;

public final class SpiritSettlementGateway implements SettlementGateway {
    private final MinecraftServer server;
    private final ServerThreadCall serverCall;

    public SpiritSettlementGateway(MinecraftServer server, ServerThreadCall serverCall) {
        this.server = Objects.requireNonNull(server, "server не может быть null: этап поселения живёт в мире");
        this.serverCall = Objects.requireNonNull(
                serverCall, "serverCall не может быть null: мир читается и меняется только на серверном потоке");
    }

    @Override
    public StageView view() {
        return serverCall.call(
                () -> SettlementStageService.of(server.overworld()).view(), RestTuning.SERVER_CALL_TIMEOUT);
    }

    @Override
    public SettlementStageChange rollback(SettlementChange change) {
        return serverCall.call(
                () -> SettlementStageService.of(server.overworld()).rollback(change), RestTuning.SERVER_CALL_TIMEOUT);
    }
}
