package com.theminespirit.neoforge.policy;

import com.theminespirit.core.policy.PolicyExemptions;
import com.theminespirit.core.policy.PolicyId;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.List;
import net.minecraft.world.level.pathfinder.PathType;

public final class PathingConfigurator {
    public static final float BLOCKED = -1.0F;
    public static final float WATER_COST = 16.0F;
    public static final float WATER_COST_EXEMPT = 1.0F;

    public static final List<PathType> BLOCKED_TYPES = List.of(PathType.LAVA, PathType.DAMAGE_FIRE, PathType.DANGER_FIRE,
            PathType.DAMAGE_OTHER, PathType.DANGER_OTHER, PathType.DAMAGE_CAUTIOUS, PathType.DANGER_POWDER_SNOW);

    private PathingConfigurator() {
    }

    public static void apply(SpiritEntity spirit, PolicyExemptions exemptions) {
        for (PathType type : BLOCKED_TYPES) {
            spirit.setPathfindingMalus(type, BLOCKED);
        }
        spirit.setPathfindingMalus(PathType.WATER, exemptions.has(PolicyId.WATER) ? WATER_COST_EXEMPT : WATER_COST);
    }
}
