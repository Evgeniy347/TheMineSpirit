package com.theminespirit.guards;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class QualifiedNameGuardTests {
    private static final Pattern QUALIFIED_TYPE =
            Pattern.compile("\\b(?:java|javax|net|com|org)(?:\\.[a-z_][a-z0-9_]*)+\\.[A-Z]\\w*");
    private static final Pattern STRING_LITERAL = Pattern.compile("\"(?:\\\\.|[^\"\\\\\\n])*\"");
    private static final Pattern LINE_COMMENT = Pattern.compile("//[^\\n]*");

    @Test
    void sources_Scanned_UseImportsInsteadOfQualifiedNames() {
        List<String> offenders = new ArrayList<>();
        collect(SourceFiles.javaUnder(SourceFiles.MAIN), offenders);
        collect(SourceFiles.javaUnder(SourceFiles.TEST), offenders);

        assertEquals(
                List.of(),
                offenders,
                "Полное имя класса в коде (net.minecraft.world.entity.EntityType.getKey(...), "
                        + "com.mojang.brigadier.CommandDispatcher<...>) прячет зависимости файла: по импортам больше "
                        + "не видно, с чем он работает, а строки разрастаются. Импортируй тип. Если два типа с "
                        + "одинаковым именем нужны в одном файле — переименуй свой тип (как BlockPos → BlockCoord) "
                        + "или вынеси работу с чужим типом в отдельный класс. Нарушители (файл:строка тип)");
    }

    @Test
    void qualifiedNamePattern_TypeUsedInCode_IsReported() {
        String sample = "        String type = net.minecraft.world.entity.EntityType.getKey(entity.getType());\n";

        assertEquals(
                List.of("1 net.minecraft.world.entity.EntityType"),
                qualifiedNames(sample),
                "Сторож ослеп: полное имя типа в строке кода должно находиться");
    }

    @Test
    void qualifiedNamePattern_ImportStringAndComment_AreIgnored() {
        String sample = """
                        import net.minecraft.world.entity.EntityType;
                                String hint = "см. net.minecraft.world.entity.EntityType";
                                // net.minecraft.world.entity.EntityType упомянут в комментарии
                        """;

        assertEquals(
                List.of(),
                qualifiedNames(sample),
                "Сторож ложно срабатывает: импорт, строковый литерал и комментарий — не использование в коде");
    }

    private static void collect(Map<Path, String> sources, List<String> offenders) {
        sources.forEach((file, text) -> {
            for (String found : qualifiedNames(text)) {
                offenders.add(file + ":" + found);
            }
        });
    }

    private static List<String> qualifiedNames(String text) {
        List<String> found = new ArrayList<>();
        String[] lines = text.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].strip();
            if (line.startsWith("import ") || line.startsWith("package ")) {
                continue;
            }
            String code = LINE_COMMENT
                    .matcher(STRING_LITERAL.matcher(lines[i]).replaceAll("\"\""))
                    .replaceAll("");
            Matcher matcher = QUALIFIED_TYPE.matcher(code);
            while (matcher.find()) {
                found.add((i + 1) + " " + matcher.group());
            }
        }
        return found;
    }
}
