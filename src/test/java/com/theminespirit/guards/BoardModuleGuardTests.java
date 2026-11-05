package com.theminespirit.guards;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class BoardModuleGuardTests {
    private static final Pattern RELOAD_IMPORT = Pattern.compile(
            "^\\s*import\\s+(?:static\\s+)?com\\.theminespirit\\.core\\.reload\\.([\\w*]+)\\s*;", Pattern.MULTILINE);
    private static final Pattern IMPORT = Pattern.compile(
            "^\\s*import\\s+(?:static\\s+)?com\\.theminespirit\\.(core\\.[\\w.]+)\\s*;", Pattern.MULTILINE);

    @Test
    void boardSources_ReloadImplementation_NotImported() {
        List<String> found = new ArrayList<>();
        SourceFiles.allMain().forEach((file, text) -> {
            String logical = SourceFiles.logical(file);
            if (!logical.startsWith("core/board/")) {
                return;
            }
            Matcher matcher = RELOAD_IMPORT.matcher(text);
            while (matcher.find()) {
                String contract = matcher.group(1);
                if (contract.contains("*") || isApiContract(contract)) {
                    continue;
                }
                found.add(logical + ": " + matcher.group().trim());
            }
        });

        assertEquals(
                List.of(),
                found,
                "Доска не должна зависеть от загрузчика таблиц (core.reload): иначе :board не собрать без корня, "
                        + "а схема таблицы живёт не у владельца. Как надо: владелец таблицы отдаёт значение через "
                        + "свой current()/useSource(), а reload только связывает его в DataTableBindings. "
                        + "Нарушители:");
    }

    private static boolean isApiContract(String name) {
        try {
            return SourceFiles.path("core/reload/" + name + ".java").normalize().startsWith(SourceFiles.API);
        } catch (IllegalArgumentException absent) {
            return false;
        }
    }

    @Test
    void boardSources_HusbandryAndMining_OnlyApiFacts() {
        List<String> found = new ArrayList<>();
        SourceFiles.allMain().forEach((file, text) -> {
            String logical = SourceFiles.logical(file);
            if (!logical.startsWith("core/board/")) {
                return;
            }
            Matcher matcher = IMPORT.matcher(text);
            while (matcher.find()) {
                String name = matcher.group(1);
                if (!name.startsWith("core.husbandry.") && !name.startsWith("core.mining.")) {
                    continue;
                }
                Path target = SourceFiles.path(name.replace('.', '/') + ".java").normalize();
                if (!target.startsWith(SourceFiles.API)) {
                    found.add(logical + ": " + name + " → " + target);
                }
            }
        });

        assertEquals(
                List.of(),
                found,
                "Доска знает о скоте и шахте только факты из :api: иначе :board тянет механику и не собирается сам. "
                        + "Как надо: словарь/факт (вид питомца, правила руды) перенеси в api/src/main/java с тем же "
                        + "пакетом, реализацию механики оставь у владельца. Нарушители (файл → класс):");
    }
}
