package com.theminespirit.neoforge;

import com.theminespirit.core.board.graph.GraphFacts;
import com.theminespirit.core.board.graph.LifeThreats;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

public final class SpiritDamageWatch {
    private record Hit(float amount, String source, long at) {}

    private static final Map<UUID, Hit> HITS = new HashMap<>();

    private SpiritDamageWatch() {}

    public static void note(UUID spirit, DamageSource source, float amount, long now) {
        if (amount <= 0) {
            return;
        }
        HITS.entrySet()
                .removeIf(entry ->
                        now < entry.getValue().at() || now - entry.getValue().at() > LifeThreats.DAMAGE_WINDOW_TICKS);
        Entity attacker = source.getEntity();
        String from = attacker == null
                ? source.getMsgId()
                : BuiltInRegistries.ENTITY_TYPE.getKey(attacker.getType()).toString();
        Hit before = HITS.get(spirit);
        float total = before == null || now - before.at() > LifeThreats.DAMAGE_WINDOW_TICKS
                ? amount
                : before.amount() + amount;
        HITS.put(spirit, new Hit(total, from, now));
    }

    public static GraphFacts.@Nullable Hurt recent(UUID spirit, long now) {
        Hit hit = HITS.get(spirit);
        if (hit == null || now < hit.at() || now - hit.at() > LifeThreats.DAMAGE_WINDOW_TICKS) {
            return null;
        }
        return new GraphFacts.Hurt(hit.amount(), hit.source(), (int) (now - hit.at()));
    }
}
