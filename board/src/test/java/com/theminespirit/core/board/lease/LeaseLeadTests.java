package com.theminespirit.core.board.lease;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardTicket;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LeaseLeadTests {
    private static final UUID LEADER = UUID.fromString("caf11c6f-9a9a-408d-92ed-71726c762c19");
    private static final UUID CARRIER = UUID.fromString("c2e586fc-4461-4abf-a8cc-e7959440d374");

    private static BoardTicket transfer() {
        return new BoardTicket(
                "transfer:repair:build_orchard:stick:" + LEADER,
                BoardCase.REPAIR_BUILDING,
                Optional.of(LEADER),
                10,
                0,
                1,
                "always",
                List.of(),
                Map.of(),
                List.of(),
                List.of(CARRIER));
    }

    @Test
    void leads_LeadingSpirit_OwnsTicket() {
        assertTrue(LeaseSupervisor.leads(transfer(), LEADER), "Ведущий тикета — его владелец");
    }

    @Test
    void leads_ExecutorOfTransfer_IsNotOwner() {
        assertFalse(
                LeaseSupervisor.leads(transfer(), CARRIER),
                "Носильщик переноса — участник, а не второй владелец: иначе аренда видит тикет "
                        + "«закреплённым за двумя духами» и роняет сервер живым исключением (сервер упал "
                        + "17.11 15:26 на transfer:repair:build_orchard:stick)");
    }
}
