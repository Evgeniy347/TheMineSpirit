package com.theminespirit.neoforge.rest;

import com.theminespirit.core.rest.RestTuning;
import com.theminespirit.core.rest.ServerThreadCall;
import com.theminespirit.core.rest.TownGateway;
import com.theminespirit.neoforge.board.TownEditService;
import java.util.Objects;
import net.minecraft.server.MinecraftServer;

public final class SpiritTownGateway implements TownGateway {
    private final MinecraftServer server;
    private final ServerThreadCall serverCall;

    public SpiritTownGateway(MinecraftServer server, ServerThreadCall serverCall) {
        this.server = Objects.requireNonNull(server, "server не может быть null: карта поселения живёт в мире");
        this.serverCall = Objects.requireNonNull(
                serverCall, "serverCall не может быть null: карта читается и меняется на серверном потоке");
    }

    @Override
    public String viewJson() {
        return serverCall.call(() -> TownEditService.of(server.overworld()).view(), RestTuning.SERVER_CALL_TIMEOUT);
    }

    @Override
    public String editJson(String body, boolean dryRun) {
        return serverCall.call(
                () -> TownEditService.of(server.overworld()).edit(body, dryRun), RestTuning.SERVER_CALL_TIMEOUT);
    }
}
