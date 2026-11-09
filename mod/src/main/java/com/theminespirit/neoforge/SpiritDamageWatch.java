package com.theminespirit.neoforge;

import com.theminespirit.core.board.graph.GraphFacts;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public final class SpiritDamageWatch {
    private record Hit(float amount, String source, long at) {}

    private static final Map<UUID, Hit> HITS = new HashMap<>();

    private SpiritDamageWatch() {}

    public static void note(UUID spirit, String source, float amount, long now, long windowTicks) {
        Objects.requireNonNull(spirit, "id духа не может быть null: урон записывается конкретному духу");
        Objects.requireNonNull(source, "источник урона не может быть null: без него свежий урон не назвать");
        if (windowTicks <= 0) {
            throw new IllegalArgumentException(
                    "Окно урона " + windowTicks + " тиков: оно положительно и приходит из формулы жизни графа");
        }
        if (amount <= 0) {
            return;
        }
        HITS.entrySet()
                .removeIf(entry ->
                        now < entry.getValue().at() || now - entry.getValue().at() > windowTicks);
        Hit before = HITS.get(spirit);
        float total = before == null || now - before.at() > windowTicks ? amount : before.amount() + amount;
        HITS.put(spirit, new Hit(total, source, now));
    }

    public static GraphFacts.@Nullable Hurt recent(UUID spirit, long now, long windowTicks) {
        Objects.requireNonNull(spirit, "id духа не может быть null: свежий урон ищется для конкретного духа");
        Hit hit = HITS.get(spirit);
        if (hit == null || now < hit.at() || now - hit.at() > windowTicks) {
            return null;
        }
        return new GraphFacts.Hurt(hit.amount(), hit.source(), (int) (now - hit.at()));
    }
}
