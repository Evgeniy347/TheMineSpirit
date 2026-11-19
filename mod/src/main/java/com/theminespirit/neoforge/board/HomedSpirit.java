package com.theminespirit.neoforge.board;

import com.theminespirit.core.observation.BlockCoord;
import java.util.Optional;
import net.minecraft.core.BlockPos;

public interface HomedSpirit {
    Optional<BlockCoord> home();

    boolean isRemoved();

    BlockPos blockPosition();
}
