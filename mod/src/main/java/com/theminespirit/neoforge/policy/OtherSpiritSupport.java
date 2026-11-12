package com.theminespirit.neoforge.policy;

import com.theminespirit.neoforge.SpiritEntity;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;

final class OtherSpiritSupport {
    private OtherSpiritSupport() {}

    static Optional<String> carried(SpiritEntity breaker, BlockPos pos) {
        AABB above = new AABB(pos.above()).inflate(0.4, 0, 0.4);
        return breaker.level().getEntitiesOfClass(SpiritEntity.class, above).stream()
                .filter(other -> !other.getUUID().equals(breaker.getUUID()))
                .filter(other -> supports(other.getOnPos(), other.blockPosition(), pos))
                .findFirst()
                .map(other -> "на блоке " + pos.toShortString() + " стоит " + other.displayName()
                        + ": опору под другим духом не ломают — он упадёт; ждать, пока сойдёт");
    }

    static boolean supports(BlockPos onPos, BlockPos feet, BlockPos cell) {
        return onPos.equals(cell) || feet.below().equals(cell);
    }
}
