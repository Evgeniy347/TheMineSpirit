package com.theminespirit.core.intellect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

class ActivityLedgerProperties {
    @Provide
    Arbitrary<List<UUID>> spirits() {
        return Arbitraries.create(UUID::randomUUID)
                .list()
                .ofMinSize(1)
                .ofMaxSize(5)
                .map(list -> {
                    Set<UUID> unique = new LinkedHashSet<>(list);
                    return List.copyOf(unique);
                });
    }

    @Property
    void clear_AfterAssign_NoEntry(@ForAll("spirits") List<UUID> spirits, @ForAll("spirits") List<UUID> cleared) {
        ActivityLedger ledger = new ActivityLedger();
        for (UUID spirit : spirits) {
            ledger.assign(spirit, ActivityId.CHOP, 100, 3.0);
        }
        for (UUID spirit : cleared) {
            ledger.clear(spirit);
        }

        for (UUID spirit : cleared) {
            if (spirits.contains(spirit)) {
                assertTrue(
                        ledger.of(spirit).isEmpty(),
                        "После clear у духа нет записи: иначе выбывший удержит деятельность");
            }
        }
        assertEquals(
                new HashSet<>(ledger.all().stream()
                                .map(ActivityAssignment::spirit)
                                .toList())
                        .size(),
                ledger.all().size(),
                "На духа не больше одной записи: иначе дух получит две работы");
    }
}
