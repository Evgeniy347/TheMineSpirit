package com.theminespirit.neoforge;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

public interface SpiritView {
    UUID getUUID();

    BlockPos blockPosition();

    boolean isRemoved();

    Level level();
}
