package com.theminespirit.neoforge.board;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class GraphStationCountsSourceTests {
    @Test
    void count_PhysicalStations_UsesRegistryBlockIds() throws IOException {
        String source =
                Files.readString(Path.of("mod/src/main/java/com/theminespirit/neoforge/board/GraphStationCounts.java"));
        String compact = source.replaceAll("\\s+", "");

        assertTrue(
                compact.contains("staticStringid(BlockStatestate)")
                        && compact.contains("BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath()")
                        && compact.contains("Stringid=id(level.getBlockState(at));")
                        && compact.contains("intradius=BoardConfig.current().facts().stationSearchRadius();")
                        && compact.contains("found.merge(id,1,Integer::sum)")
                        && compact.contains("buildings.get(id)==Built.BUILT?found.getOrDefault(id,0):0"),
                "Число станций считается по физическим блокам, а не по одному ledger-флагу: иначе "
                        + "каждый новый верстак не увеличивает capacity и готовый билет переоткрывается. "
                        + "При смене count_PhysicalStations_UsesRegistryBlockIds проверь GraphStationCounts.count "
                        + "и живой graph:station:crafting_table.");
    }
}
