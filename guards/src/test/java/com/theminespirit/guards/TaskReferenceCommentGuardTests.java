package com.theminespirit.guards;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class TaskReferenceCommentGuardTests {
    private static final Pattern STRING_LITERAL = Pattern.compile("\"(?:\\\\.|[^\"\\\\\\n])*\"");
    private static final Pattern COMMENT = Pattern.compile("//[^\\n]*|/\\*(?s:.*?)\\*/");
    private static final Pattern TASK_REFERENCE =
            Pattern.compile("(?iU)\\b(?:" + "чеклист" + "\\w*|" + "tasks" + "/|" + "todo" + "\\.md)");

    @Test
    void productionSources_Comments_DoNotReferenceTaskFiles() {
        List<String> offenders = new ArrayList<>();
        collect(SourceFiles.allMain(), offenders);

        assertEquals(
                List.of(),
                offenders,
                "Комментарий в production-коде ссылается на чеклист или todo.md (\"// "
                        + "чеклист 11: radius 1..32\"). "
                        + "Чеклисты удаляются после приёмки — ссылка протухает, а пересказ числа дублирует константу. "
                        + "Как надо: удалить комментарий; смысл несёт имя константы, причина числа — сообщение теста "
                        + "(docs/CONVENTIONS.md → «Комментарии»). Нарушители (файл:строка)");
    }

    @Test
    void taskReferencePattern_LineAndBlockComments_AreReported() {
        String sample = "    // " + "чеклист 11: chop_tree.radius 1..32\n"
                + "    int a = 1; /* " + "tasks" + "/12: radius */\n"
                + "    // см. " + "todo" + ".md\n";

        assertEquals(
                List.of(1, 2, 3),
                taskReferenceLines(sample),
                "Сторож ослеп: ссылка на чеклист, tasks/ и todo.md в комментарии должна находиться");
    }

    @Test
    void taskReferencePattern_StringLiteralAndPlainComment_AreIgnored() {
        String sample = "    String path = \"" + "tasks" + "/12.md\";\n"
                + "    // Ванильные жилы меньше, крупные медные — около 20 блоков\n";

        assertEquals(
                List.of(),
                taskReferenceLines(sample),
                "Сторож ложно срабатывает: строковый литерал и обычный комментарий без ссылки на задачу — не нарушение");
    }

    private static void collect(Map<Path, String> sources, List<String> offenders) {
        sources.forEach((file, text) -> {
            for (int line : taskReferenceLines(text)) {
                offenders.add(file + ":" + line);
            }
        });
    }

    private static List<Integer> taskReferenceLines(String text) {
        String code = STRING_LITERAL.matcher(text).replaceAll("\"\"");
        List<Integer> lines = new ArrayList<>();
        Matcher comment = COMMENT.matcher(code);
        while (comment.find()) {
            if (TASK_REFERENCE.matcher(comment.group()).find()) {
                lines.add(SourceFiles.lineOf(code, comment.start()));
            }
        }
        return lines;
    }
}
