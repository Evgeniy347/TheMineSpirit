package com.theminespirit.core.mining;

import com.theminespirit.core.observation.BlockCoord;
import java.util.Objects;
import java.util.Optional;

public final class AnchorSpot {
    private AnchorSpot() {}

    public static Optional<BlockCoord> near(BlockCoord anchor, BlockCoord from, SafetyView view) {
        Objects.requireNonNull(anchor, "Якорь не может быть null: без него неизвестно, куда возвращаться");
        Objects.requireNonNull(from, "Точка духа не может быть null: по ней отбираются достижимые стоянки");
        Objects.requireNonNull(view, "Вид мира не может быть null: по нему проверяется стоянка якоря");
        if (MiningSafety.canStand(anchor, view) && StandSpots.walkable(anchor, from)) {
            return Optional.of(anchor);
        }
        return StandSpots.of(anchor, view, from).stream()
                .filter(stand -> StandSpots.walkable(stand, from))
                .findFirst();
    }
}
