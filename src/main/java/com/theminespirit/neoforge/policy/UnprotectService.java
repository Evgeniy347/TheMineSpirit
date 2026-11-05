package com.theminespirit.neoforge.policy;

import com.theminespirit.core.observation.BlockBox;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.policy.UnprotectArea;
import com.theminespirit.core.reload.DataTables;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

public final class UnprotectService {
    private static final long MILLIS_PER_SECOND = 1000L;

    private final ServerLevel overworld;

    private UnprotectService(ServerLevel overworld) {
        this.overworld = Objects.requireNonNull(overworld, "overworld не может быть null: данные мира живут в нём");
    }

    public static UnprotectService of(MinecraftServer server) {
        Objects.requireNonNull(server, "server не может быть null: разрешения живут в данных мира");
        return new UnprotectService(server.overworld());
    }

    public UnprotectArea grant(
            String dimension,
            BlockCoord first,
            BlockCoord second,
            String grantedBy,
            String reason,
            long ttlSeconds,
            long nowMillis) {
        if (ttlSeconds < 0) {
            throw new IllegalArgumentException("Срок жизни " + ttlSeconds + " секунд отрицателен: без срока задают 0");
        }
        BlockBox box = box(first, second);
        if (PolicyConfig.zones().intersects(dimension, box)) {
            throw new IllegalArgumentException("Область " + describe(box) + " пересекается с защищённой зоной сервера "
                    + "(policy.protectedZones): защищённую зону снимают правкой конфига, а не разрешением");
        }
        long expiresAt = expiresAt(nowMillis, ttlSeconds);
        UnprotectArea area = new UnprotectArea(
                UUID.randomUUID().toString(), dimension, box, grantedBy, nowMillis, reason, expiresAt);
        return UnprotectAreasData.get(overworld)
                .add(area, DataTables.INSTANCE.protection().current().maxUnprotectVolume(), nowMillis);
    }

    public List<UnprotectArea> list() {
        return UnprotectAreasData.get(overworld).all();
    }

    public boolean remove(String id) {
        return UnprotectAreasData.get(overworld).remove(id);
    }

    public boolean covers(String dimension, BlockCoord pos, long nowMillis) {
        return UnprotectAreasData.get(overworld).covers(dimension, pos, nowMillis);
    }

    private static long expiresAt(long nowMillis, long ttlSeconds) {
        if (ttlSeconds == 0) {
            return 0;
        }
        try {
            return Math.addExact(nowMillis, Math.multiplyExact(ttlSeconds, MILLIS_PER_SECOND));
        } catch (ArithmeticException overflow) {
            throw new IllegalArgumentException(
                    "Срок жизни " + ttlSeconds + " секунд не помещается в дату: уменьшите срок");
        }
    }

    private static BlockBox box(BlockCoord first, BlockCoord second) {
        Objects.requireNonNull(first, "Первый угол области не может быть null: область задаётся двумя углами");
        Objects.requireNonNull(second, "Второй угол области не может быть null: область задаётся двумя углами");
        return new BlockBox(
                new BlockCoord(
                        Math.min(first.x(), second.x()),
                        Math.min(first.y(), second.y()),
                        Math.min(first.z(), second.z())),
                new BlockCoord(
                        Math.max(first.x(), second.x()),
                        Math.max(first.y(), second.y()),
                        Math.max(first.z(), second.z())));
    }

    private static String describe(BlockBox box) {
        return "[" + box.min().x() + " " + box.min().y() + " " + box.min().z() + " — "
                + box.max().x() + " " + box.max().y() + " " + box.max().z() + "]";
    }
}
