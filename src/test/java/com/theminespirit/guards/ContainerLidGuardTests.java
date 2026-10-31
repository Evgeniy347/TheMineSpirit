package com.theminespirit.guards;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class ContainerLidGuardTests {
    private static final Path LID = Path.of("src/main/java/com/theminespirit/neoforge/inventory/ContainerLid.java");
    private static final Path CELLS = Path.of("src/main/java/com/theminespirit/neoforge/inventory/ContainerCells.java");
    private static final Pattern CHEST_TYPE_READ = Pattern.compile("getValue\\(\\s*ChestBlock\\.TYPE\\s*\\)");

    @Test
    void containerLid_ChestTypeReadsOutsideHelper_AreAbsent() throws IOException {
        assertEquals(
                List.of(),
                lines(CHEST_TYPE_READ, Files.readString(LID, StandardCharsets.UTF_8)),
                "ContainerLid читает ChestBlock.TYPE только через ContainerCells.chestType, где есть проверка "
                        + "instanceof ChestBlock и hasProperty(TYPE). Прямое чтение на клетке air с BlockEntity "
                        + "сундука бросает IllegalArgumentException в тике и валит сервер (observe10, A1)");
    }

    @Test
    void containerCells_OnlyChestTypeRead_IsGuarded() throws IOException {
        String source = Files.readString(CELLS, StandardCharsets.UTF_8);
        List<Integer> reads = lines(CHEST_TYPE_READ, source);

        assertEquals(
                1,
                reads.size(),
                "свойство ChestBlock.TYPE читается в одном месте — ContainerCells.chestType: второе чтение "
                        + "легко останется без проверки и снова уронит тик на воздухе с BlockEntity");
        assertTrue(
                source.contains("state.getBlock() instanceof ChestBlock"),
                "перед getValue(ChestBlock.TYPE) обязательна проверка instanceof ChestBlock: у воздуха "
                        + "свойства type нет");
        assertTrue(
                source.contains("state.hasProperty(ChestBlock.TYPE)"),
                "перед getValue(ChestBlock.TYPE) обязательна проверка hasProperty(TYPE): без неё воздух "
                        + "с BlockEntity сундука снова даст исключение");
    }

    @Test
    void chestTypeReadPattern_UncheckedSample_IsReported() {
        String sample = "        ChestType type = state.getValue(ChestBlock.TYPE);\n";

        assertEquals(
                List.of(1),
                lines(CHEST_TYPE_READ, sample),
                "сторож ослеп: прямое чтение ChestBlock.TYPE в образце обязано находиться, иначе он пропустит "
                        + "возврат старого падения в ContainerLid");
    }

    private static List<Integer> lines(Pattern pattern, String source) {
        List<Integer> found = new ArrayList<>();
        Matcher matcher = pattern.matcher(source);
        while (matcher.find()) {
            found.add(SourceFiles.lineOf(source, matcher.start()));
        }
        return found;
    }
}
