package com.theminespirit.neoforge.rest;

import com.theminespirit.core.rest.RestTuning;
import com.theminespirit.core.rest.ServerThreadCall;
import com.theminespirit.core.rest.TownGateway;
import com.theminespirit.neoforge.board.TownMapService;
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
    public String planJson() {
        return serverCall.call(() -> TownMapService.of(server.overworld()).planJson(), RestTuning.SERVER_CALL_TIMEOUT);
    }

    @Override
    public byte[] terrainPng(int x1, int z1, int x2, int z2, int scale) {
        return serverCall.call(
                () -> TownMapService.of(server.overworld()).terrainPng(x1, z1, x2, z2, scale),
                RestTuning.SERVER_CALL_TIMEOUT);
    }

    @Override
    public byte[] previewPng(String body) {
        return serverCall.call(
                () -> TownMapService.of(server.overworld()).previewPng(body), RestTuning.SERVER_CALL_TIMEOUT);
    }

    @Override
    public String applyJson(String body) {
        return serverCall.call(() -> TownMapService.of(server.overworld()).apply(body), RestTuning.SERVER_CALL_TIMEOUT);
    }
}
