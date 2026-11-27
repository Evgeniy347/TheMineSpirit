package com.theminespirit.neoforge;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class DigOutFloorTests {
    @Test
    void place_InsideSettlement_RefusesTemporaryDirt() throws IOException {
        String source = Files.readString(Path.of("mod/src/main/java/com/theminespirit/neoforge/DigOutFloor.java"));
        String compact = source.replaceAll("\\s+", "");

        assertTrue(
                compact.contains("SettlementPlotStore.settlementArea(server)")
                        && compact.contains("area.contains(cell.getX(),cell.getZ())")
                        && compact.contains("впоселениивременныеступенизапрещены"),
                "Внутри поселения dig_out не ставит грунтовые ступени: иначе снова засыпает дверные "
                        + "проёмы и стройплощадки. При смене place_InsideSettlement_RefusesTemporaryDirt "
                        + "проверь DigOutFloor.place и новый склад Evgeniy.");
    }
}
