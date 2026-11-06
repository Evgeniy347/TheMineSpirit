package com.theminespirit.neoforge.rest;

import com.theminespirit.core.board.graph.GraphDecisions;
import com.theminespirit.core.rest.GraphGateway;
import com.theminespirit.core.rest.RestTuning;
import com.theminespirit.core.rest.ServerThreadCall;
import com.theminespirit.neoforge.board.BoardRegistry;
import java.util.Objects;
import net.minecraft.server.MinecraftServer;

public final class SpiritGraphGateway implements GraphGateway {
    private final MinecraftServer server;
    private final ServerThreadCall serverCall;

    public SpiritGraphGateway(MinecraftServer server) {
        this.server = Objects.requireNonNull(server, "server не может быть null: объяснение решений живёт на сервере");
        this.serverCall = new ServerThreadCall(server);
    }

    public void stop() {
        serverCall.stop();
    }

    @Override
    public GraphDecisions decisions() {
        return serverCall.call(
                () -> BoardRegistry.get(server.overworld()).sources.graphDecisions(), RestTuning.SERVER_CALL_TIMEOUT);
    }
}
