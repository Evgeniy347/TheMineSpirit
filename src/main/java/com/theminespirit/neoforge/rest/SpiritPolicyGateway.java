package com.theminespirit.neoforge.rest;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.policy.UnprotectArea;
import com.theminespirit.core.rest.PolicyGateway;
import com.theminespirit.core.rest.RestTuning;
import com.theminespirit.core.rest.ServerThreadCall;
import com.theminespirit.core.rest.UnprotectRequest;
import com.theminespirit.neoforge.policy.UnprotectService;
import java.util.List;
import java.util.Objects;
import net.minecraft.server.MinecraftServer;

/**
 * Разрешения на снятие защиты построек игрока для REST: список, выдача и снятие.
 * Данные мира читаются и меняются только в серверном потоке через {@link ServerThreadCall}.
 */
public final class SpiritPolicyGateway implements PolicyGateway {
    private final MinecraftServer server;
    private final ServerThreadCall serverCall;

    public SpiritPolicyGateway(MinecraftServer server) {
        this.server = Objects.requireNonNull(server, "server не может быть null: разрешения живут в данных мира");
        this.serverCall = new ServerThreadCall(server);
    }

    public void stop() {
        serverCall.stop();
    }

    @Override
    public List<UnprotectArea> areas() {
        return serverCall.call(() -> UnprotectService.of(server).list(), RestTuning.SERVER_CALL_TIMEOUT);
    }

    @Override
    public UnprotectArea grant(UnprotectRequest request) {
        return serverCall.call(() -> grantNow(request), RestTuning.SERVER_CALL_TIMEOUT);
    }

    @Override
    public UnprotectArea revoke(String id) {
        return serverCall.call(() -> revokeNow(id), RestTuning.SERVER_CALL_TIMEOUT);
    }

    private UnprotectArea grantNow(UnprotectRequest request) {
        UnprotectService service = UnprotectService.of(server);
        BlockCoord min = request.min();
        BlockCoord max = request.max();
        return service.grant(
                request.dimension(),
                min,
                max,
                request.grantedBy(),
                request.reason(),
                request.ttlSeconds(),
                System.currentTimeMillis());
    }

    private UnprotectArea revokeNow(String id) {
        UnprotectService service = UnprotectService.of(server);
        UnprotectArea target = service.list().stream()
                .filter(area -> area.id().equals(id))
                .findFirst()
                .orElse(null);
        if (target == null || !service.remove(id)) {
            return null;
        }
        return target;
    }
}
