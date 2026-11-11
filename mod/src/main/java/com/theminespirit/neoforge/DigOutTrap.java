package com.theminespirit.neoforge;

import com.theminespirit.core.building.BuildPit;
import net.minecraft.world.level.Level;

public final class DigOutTrap {
    private DigOutTrap() {}

    public static boolean trapped(Level level, SpiritEntity spirit) {
        return BuildPit.trapped(DigOutScript.sealed(level, spirit.blockPosition()), DigOutScript.embedded(spirit));
    }
}
