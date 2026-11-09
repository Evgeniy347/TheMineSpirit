package com.theminespirit.core.town;

import com.theminespirit.core.observation.BlockCoord;
import java.util.Objects;

public final class StageStands {
    private StageStands() {}

    public static boolean fillStandAllowed(StageSite site, BlockCoord stand, boolean sky) {
        Objects.requireNonNull(site, "Место этапа не может быть null: стоянка сверяется с площадкой");
        Objects.requireNonNull(stand, "Стоянка не может быть null: проверяется её клетка");
        return sky || !site.levelColumn(stand.x(), stand.z());
    }
}
