package com.theminespirit.neoforge.rest;

import com.theminespirit.core.board.route.QuarryCandidate;
import com.theminespirit.core.board.route.QuarryStatus;
import com.theminespirit.core.board.route.RouteResource;
import com.theminespirit.core.rest.QuarryGateway;
import com.theminespirit.core.rest.QuarryInstall;
import com.theminespirit.core.rest.QuarryQuery;
import com.theminespirit.core.rest.RestTuning;
import com.theminespirit.core.rest.ServerThreadCall;
import com.theminespirit.neoforge.board.QuarryService;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

public final class SpiritQuarryGateway implements QuarryGateway {
    private static final Duration SEARCH_TIMEOUT = Duration.ofSeconds(30);

    private final MinecraftServer server;
    private final ServerThreadCall serverCall;

    public SpiritQuarryGateway(MinecraftServer server, ServerThreadCall serverCall) {
        this.server = Objects.requireNonNull(server, "server не может быть null: места добычи живут в мире");
        this.serverCall = Objects.requireNonNull(
                serverCall, "serverCall не может быть null: мир читается только на серверном потоке");
    }

    @Override
    public List<QuarryStatus> statuses() {
        return serverCall.call(() -> QuarryService.statuses(level()), RestTuning.SERVER_CALL_TIMEOUT);
    }

    @Override
    public List<QuarryCandidate> search(QuarryQuery query) {
        return serverCall.call(
                () -> QuarryService.search(
                        level(), query.resource(), query.base().orElse(null), query.min(), query.max()),
                SEARCH_TIMEOUT);
    }

    @Override
    public QuarryStatus install(QuarryInstall install) {
        return serverCall.call(() -> QuarryService.install(level(), install.site()), RestTuning.SERVER_CALL_TIMEOUT);
    }

    @Override
    public QuarryStatus remove(RouteResource resource) {
        return serverCall.call(() -> QuarryService.remove(level(), resource), RestTuning.SERVER_CALL_TIMEOUT);
    }

    private ServerLevel level() {
        return server.overworld();
    }
}
