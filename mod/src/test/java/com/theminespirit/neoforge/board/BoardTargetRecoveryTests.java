package com.theminespirit.neoforge.board;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class BoardTargetRecoveryTests {
    @Test
    void dispatch_MissingGraphTarget_CancelsInsteadOfHomeFallback() throws IOException {
        String source =
                Files.readString(Path.of("mod/src/main/java/com/theminespirit/neoforge/board/BoardDispatchDesk.java"));
        String compact = source.replaceAll("\\s+", "");
        int missing = compact.indexOf("TicketTargetbase=targets.get(ticket.id());");
        int cancel = compact.indexOf("board.cancel(ticket.id());", missing);
        int fallback = compact.indexOf("TicketTarget.at(batch.settlementHome().orElseThrow())", cancel);

        assertTrue(
                missing >= 0
                        && cancel > missing
                        && fallback > cancel
                        && compact.contains("ticket.id().startsWith(GraphTickets.PREFIX)")
                        && compact.contains("ticket.id().startsWith(\"demolish_chest:\")"),
                "Graph-билет без восстановленной цели снимается для пересоздания, а не выполняется у дома: "
                        + "иначе снос сундука уходит не туда и свободные духи снова простаивают. При смене "
                        + "dispatch_MissingGraphTarget_CancelsInsteadOfHomeFallback проверь BoardDispatchDesk.issueTicket.");
    }
}
