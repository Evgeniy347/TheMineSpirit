package com.theminespirit.neoforge;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.theminespirit.core.mining.BreakSkipTally;
import org.junit.jupiter.api.Test;

class BreakSkipsTests {
    @Test
    void supportsSelf_RefusalTallied_MapsToSelfSupportReason() {
        assertEquals(
                BreakSkipTally.Reason.SUPPORTS_SELF,
                BreakSkips.reason(BlockBreaker.RefuseReason.SUPPORTS_SELF),
                "Отказ «опора под духом» должен попадать в счётчик своей причиной: иначе расчистка "
                        + "покажет ложные «вне досягаемости». При смене "
                        + "supportsSelf_RefusalTallied_MapsToSelfSupportReason проверь маппинг причин отказа.");
    }
}
