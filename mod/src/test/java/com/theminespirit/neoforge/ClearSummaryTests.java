package com.theminespirit.neoforge;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.building.BuildBox;
import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import org.junit.jupiter.api.Test;

class ClearSummaryTests {
    private static final BuildBox BOX = BuildBox.of(-182, 66, -524, -182, 66, -524);
    private static final ClearSummary.Refusals NONE = new ClearSummary.Refusals(0, 0, 0, 0);

    @Test
    void of_LeftoverCell_NamesReasonAndRepeatCount() {
        ClearSummary.Leftover fence =
                new ClearSummary.Leftover(new BlockCoord(-182, 66, -524), "oak_fence", "без стоянки", 3);

        String summary = ClearSummary.of(BOX, new ClearSummary.Tally(0, 1, 0, 1, 0, 0, 0, 0, NONE), List.of(fence));

        assertTrue(
                summary.contains("(-182, 66, -524) oak_fence — без стоянки, повтор 3"),
                "Каждый невзятый блок обязан нести причину и счётчик повторов: без них «без стоянки: 1» "
                        + "не говорит, какой забор не даётся и сколько раз его уже пробовали");
    }

    @Test
    void of_ExcludedCell_ReportsBlacklistedCount() {
        String summary = ClearSummary.of(BOX, new ClearSummary.Tally(0, 1, 0, 0, 0, 0, 0, 1, NONE), List.of());

        assertTrue(
                summary.contains("исключено недостижимых: 1"),
                "Исключённые клетки считаются отдельно: по счётчику видно, что уборка перестала их трогать");
    }
}
