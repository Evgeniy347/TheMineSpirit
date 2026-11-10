package com.theminespirit.neoforge.board;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.board.route.RouteTrail;
import com.theminespirit.core.board.route.RouteTrailCodec;
import net.minecraft.nbt.CompoundTag;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

final class RouteTrailNbt {
    private static final Logger LOGGER = LogUtils.getLogger();

    private RouteTrailNbt() {}

    static void write(CompoundTag tag, String cellsKey, String blockKey, RouteTrail trail) {
        tag.putIntArray(cellsKey, RouteTrailCodec.encode(trail));
        tag.putString(blockKey, trail.block());
    }

    static @Nullable RouteTrail read(CompoundTag tag, String cellsKey, String blockKey) {
        if (!tag.contains(cellsKey)) {
            return null;
        }
        int[] packed = tag.getIntArray(cellsKey);
        RouteTrail trail =
                RouteTrailCodec.decode(packed, tag.getString(blockKey)).orElse(null);
        if (trail == null) {
            LOGGER.error(
                    "[route] клетки тропы {} длиной {}: ожидалось по три числа на клетку", cellsKey, packed.length);
        }
        return trail;
    }
}
