package com.theminespirit.neoforge.board;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class BoardSupplyDeskTests {
    @Test
    void gateCenters_StockScan_DoesNotBuildWalkableComponent() throws IOException {
        String desk =
                Files.readString(Path.of("mod/src/main/java/com/theminespirit/neoforge/board/BoardSupplyDesk.java"));
        String home = Files.readString(Path.of("mod/src/main/java/com/theminespirit/neoforge/board/HomePoint.java"));
        String centers = desk.substring(
                desk.indexOf("private List<BlockCoord> gateCenters"), desk.indexOf("private boolean hasActiveSupply"));
        Matcher method = Pattern.compile(
                        "public static Optional<BlockCoord> center\\([^)]*\\) \\{(.*?)\\n    }", Pattern.DOTALL)
                .matcher(home);
        assertTrue(
                method.find(),
                "Метод HomePoint.center отсутствует: иначе stock не имеет дешёвого центра и снова строит "
                        + "BFS проходимости. При смене gateCenters_StockScan_DoesNotBuildWalkableComponent проверь "
                        + "HomePoint.center и BoardSupplyDesk.gateCenters.");
        String center = method.group(1);

        assertTrue(
                centers.contains("HomePoint.center(level, spirits)"),
                "Скан запасов использует дешёвый центр дома: иначе stock снова обходит компоненту проходимости "
                        + "и задерживает сервер на секунды. При смене gateCenters_StockScan_DoesNotBuildWalkableComponent "
                        + "проверь BoardSupplyDesk.gateCenters и живый startup stock.");
        assertFalse(
                center.contains("HomeStand") || center.contains("RetreatPath"),
                "Дешёвый центр не ищет стоянку и не строит BFS: иначе холодный старт снова попадает в stock. "
                        + "При смене gateCenters_StockScan_DoesNotBuildWalkableComponent проверь HomePoint.center.");
        String reader =
                Files.readString(Path.of("mod/src/main/java/com/theminespirit/neoforge/board/StockReader.java"));
        assertTrue(
                reader.contains("board.stock.scan") && reader.contains("board.stock.containers"),
                "Фазы stock разделены замерами: иначе следующий холодный старт снова покажет только общие "
                        + "2519 мс без виновного участка. При смене gateCenters_StockScan_DoesNotBuildWalkableComponent "
                        + "проверь StockReader.collect и PerfReport.");
        assertTrue(
                reader.contains("ContainerAccess.allowed(level.getBlockState")
                        && !reader.contains("ContainerAccess.allowedBlocks()"),
                "Скан проверяет только найденный блок: иначе построение полного набора реестра снова "
                        + "займёт 1841 мс в холодном тике. При смене gateCenters_StockScan_DoesNotBuildWalkableComponent "
                        + "проверь StockReader.blocks и ContainerAccess.allowed.");
        String report = Files.readString(Path.of("mod/src/main/java/com/theminespirit/neoforge/board/PerfReport.java"));
        assertTrue(
                report.contains("if (lines.isEmpty())") && !report.contains("boolean first"),
                "Первое окно производительности не выбрасывается: иначе холодный stock снова скроется "
                        + "из отчёта. При смене gateCenters_StockScan_DoesNotBuildWalkableComponent проверь "
                        + "PerfReport.tick и startup stock.");
    }
}
