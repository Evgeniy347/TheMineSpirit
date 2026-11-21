package com.theminespirit.neoforge.movement;

import com.theminespirit.core.movement.api.BodyTurn;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.entity.Mob;

public final class BodyTurnWatch {
    private static final float MAX_STEP_DEGREES = 40.0F;
    private static final Map<UUID, Float> LAST = new ConcurrentHashMap<>();

    private BodyTurnWatch() {}

    public static void tick(Mob mob) {
        float want = mob.getYRot();
        Float last = LAST.put(mob.getUUID(), want);
        if (last == null) {
            return;
        }
        float limited = BodyTurn.limit(last, want, MAX_STEP_DEGREES);
        if (limited != want) {
            mob.setYRot(limited);
            LAST.put(mob.getUUID(), limited);
        }
    }
}
