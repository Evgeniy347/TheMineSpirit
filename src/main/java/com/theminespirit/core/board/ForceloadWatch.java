package com.theminespirit.core.board;

import com.theminespirit.core.observation.BlockCoord;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.LongPredicate;
import org.jspecify.annotations.Nullable;

public final class ForceloadWatch {
    private final Map<UUID, String> warned = new HashMap<>();

    public Optional<String> check(
            UUID spirit,
            String name,
            BlockCoord position,
            @Nullable String taskId,
            @Nullable BlockCoord goal,
            LongPredicate forced) {
        Objects.requireNonNull(spirit, "Дух не может быть null: предупреждение относится к конкретному духу");
        Objects.requireNonNull(name, "Имя духа не может быть null: без него предупреждение не найти в логе");
        Objects.requireNonNull(position, "Позиция духа не может быть null: по ней видно, вышел ли он за чанки");
        Objects.requireNonNull(forced, "Проверка чанка не может быть null: иначе не отличить помеченный чанк");
        long chunkX = position.x() >> 4;
        long chunkZ = position.z() >> 4;
        boolean positionOutside = !forced.test(chunkKey(chunkX, chunkZ));
        boolean goalOutside = goal != null && !forced.test(chunkKey(goal.x() >> 4, goal.z() >> 4));
        if (!positionOutside && !goalOutside) {
            warned.remove(spirit);
            return Optional.empty();
        }
        String key = positionOutside + "|" + goalOutside + "|" + taskId + "|" + chunkX + "|" + chunkZ;
        if (key.equals(warned.put(spirit, key))) {
            return Optional.empty();
        }
        StringBuilder text = new StringBuilder();
        if (positionOutside) {
            text.append("дух ")
                    .append(name)
                    .append(" в ")
                    .append(at(position))
                    .append(" вне forceload (чанк [")
                    .append(chunkX)
                    .append(", ")
                    .append(chunkZ)
                    .append("])");
        }
        if (goalOutside && goal != null) {
            if (!text.isEmpty()) {
                text.append("; ");
            }
            text.append("цель задания ")
                    .append(taskId == null ? "-" : taskId)
                    .append(" в ")
                    .append(at(goal))
                    .append(" вне forceload (чанк [")
                    .append(goal.x() >> 4)
                    .append(", ")
                    .append(goal.z() >> 4)
                    .append("])");
        }
        return Optional.of(text.toString());
    }

    private static String at(BlockCoord point) {
        return "(" + point.x() + ", " + point.y() + ", " + point.z() + ")";
    }

    private static long chunkKey(long chunkX, long chunkZ) {
        return (chunkX & 0xFFFFFFFFL) | ((chunkZ & 0xFFFFFFFFL) << 32);
    }
}
