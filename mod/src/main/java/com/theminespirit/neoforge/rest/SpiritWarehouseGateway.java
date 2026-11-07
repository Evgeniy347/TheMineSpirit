package com.theminespirit.neoforge.rest;

import com.theminespirit.core.rest.RestTuning;
import com.theminespirit.core.rest.ServerThreadCall;
import com.theminespirit.core.rest.WarehouseChange;
import com.theminespirit.core.rest.WarehouseDecision;
import com.theminespirit.core.rest.WarehouseGateway;
import com.theminespirit.core.rest.WarehouseView;
import com.theminespirit.neoforge.board.SettlementWarehouseService;
import java.util.Objects;
import net.minecraft.server.MinecraftServer;

public final class SpiritWarehouseGateway implements WarehouseGateway {
    private final MinecraftServer server;
    private final ServerThreadCall serverCall;

    public SpiritWarehouseGateway(MinecraftServer server, ServerThreadCall serverCall) {
        this.server = Objects.requireNonNull(server, "server не может быть null: склад живёт в мире");
        this.serverCall = Objects.requireNonNull(
                serverCall, "serverCall не может быть null: мир читается и меняется только на серверном потоке");
    }

    @Override
    public WarehouseView view() {
        return serverCall.call(
                () -> SettlementWarehouseService.of(server.overworld()).view(), RestTuning.SERVER_CALL_TIMEOUT);
    }

    @Override
    public WarehouseChange decide(WarehouseDecision decision) {
        return serverCall.call(
                () -> SettlementWarehouseService.of(server.overworld()).decide(decision),
                RestTuning.SERVER_CALL_TIMEOUT);
    }
}
