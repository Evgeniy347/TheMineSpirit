package com.theminespirit.core.board.audit;

import com.theminespirit.core.observation.BlockCoord;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

public final class AuditFill {

    public interface Reader {
        @Nullable
        String blockAt(BlockCoord cell);
    }

    private AuditFill() {}

    public static int fill(ChunkCache cache, int budget, Reader world, long tick) {
        Objects.requireNonNull(cache, "Кэш чанка не может быть null: наполнять не во что");
        Objects.requireNonNull(world, "Чтец мира не может быть null: читать нечем");
        int spent = 0;
        while (spent < budget && !cache.scanned()) {
            BlockCoord cell = cache.cellAt(cache.cursor());
            String standing = world.blockAt(cell);
            if (standing == null) {
                cache.markUnknown(cell);
            } else {
                cache.put(cell, standing);
                if (BlockEquivalence.solid(standing)) {
                    cache.putSurface(cell.x() & 15, cell.z() & 15, cell.y());
                }
            }
            cache.advance(1);
            spent++;
        }
        if (cache.scanned() && cache.filledTick() < 0) {
            cache.filled(tick);
        }
        return spent;
    }
}
