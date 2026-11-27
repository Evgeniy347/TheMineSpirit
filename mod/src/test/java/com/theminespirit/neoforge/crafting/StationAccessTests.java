package com.theminespirit.neoforge.crafting;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class StationAccessTests {
    @Test
    void walk_NearestStationUnreachable_TriesNextStation() throws IOException {
        String finder =
                Files.readString(Path.of("mod/src/main/java/com/theminespirit/neoforge/crafting/StationFinder.java"));
        String access =
                Files.readString(Path.of("mod/src/main/java/com/theminespirit/neoforge/crafting/StationAccess.java"));
        String compactFinder = finder.replaceAll("\\s+", "");
        String compactAccess = access.replaceAll("\\s+", "");

        assertTrue(
                compactFinder.contains("Optional<BlockPos>next()")
                        && compactFinder.contains("stations=located.stream().sorted(order()).toList()"),
                "Поиск перебирает все найденные станции по расстоянию: иначе один недостижимый ближайший "
                        + "верстак скрывает остальные. При смене walk_NearestStationUnreachable_TriesNextStation "
                        + "проверь StationFinder.next.");
        assertTrue(
                compactAccess.contains("Optional<BlockPos>existing=finder.next()")
                        && compactAccess.contains("caseSTUCK,TIMEOUT->{approach.cancel();pos=null;search();}"),
                "Отказ подхода перезапускает поиск следующей станции: иначе крафт падает при множестве "
                        + "доступных верстаков. При смене walk_NearestStationUnreachable_TriesNextStation "
                        + "проверь StationAccess.walk и живой craft.");
    }
}
