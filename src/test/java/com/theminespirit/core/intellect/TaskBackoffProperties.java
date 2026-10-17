package com.theminespirit.core.intellect;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.LongRange;

class TaskBackoffProperties {
    @Property
    void backoff_NotedFailure_BlockedExactlyWindow(
            @ForAll @LongRange(min = 0, max = 100000) long fail,
            @ForAll @LongRange(min = 0, max = 399) long delta) {
        TaskBackoff backoff = new TaskBackoff();
        UUID spirit = UUID.randomUUID();
        backoff.noteFailure(spirit, "harvest_crop", fail);

        assertEquals(delta < IntellectTuning.loadDefault().failBackoffTicks(),
                backoff.blocked(spirit, "harvest_crop", fail + delta),
                "Блокировка держится ровно окно отката: короче — перезапуск провала, дольше — простой духа");
    }
}
