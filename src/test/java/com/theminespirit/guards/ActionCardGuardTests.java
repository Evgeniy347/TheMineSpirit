package com.theminespirit.guards;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.theminespirit.core.board.BoardActivities;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class ActionCardGuardTests {
    private static final Path NEOFORGE = SourceFiles.MAIN.resolve("com/theminespirit/neoforge");
    private static final Pattern REGISTERED = Pattern.compile("\\.put\\(\\s*\"([a-z][a-z_]*)\"\\s*,\\s*spirit\\s*->");
    private static final String RULE = "Каждый скрипт духа обязан иметь кейс карточки доски (BoardActivities): "
            + "иначе действие идёт без видимой карточки, и владелец не знает, чем занят дух. "
            + "Как надо: добавь скрипт в таблицу BoardActivities.scriptCases() с кейсом ACTIVITY_*.";

    @Test
    void spiritScripts_EveryRegisteredScript_HasBoardCard() {
        Set<String> scripts = registered();
        assertEquals(List.of(), uncovered(scripts), RULE + " Найдено скриптов: " + scripts.size() + ". Без карточки:");
    }

    @Test
    void uncovered_NewScriptWithoutCard_IsReported() {
        assertEquals(
                List.of("new_script"),
                uncovered(Set.of("new_script")),
                "Сторож ослеп: новый скрипт без кейса карточки обязан попасть в нарушители");
    }

    @Test
    void uncovered_MappedScript_IsAccepted() {
        assertEquals(
                List.of(),
                uncovered(Set.of("chop_tree")),
                "Сторож ложно срабатывает: у рубки есть карточка ACTIVITY_CHOP");
    }

    private static List<String> uncovered(Set<String> scripts) {
        List<String> problems = new ArrayList<>();
        for (String script : new TreeSet<>(scripts)) {
            if (BoardActivities.caseOfScript(script).isEmpty()) {
                problems.add(script);
            }
        }
        return List.copyOf(problems);
    }

    private static Set<String> registered() {
        Set<String> scripts = new HashSet<>();
        for (String text : SourceFiles.javaUnder(NEOFORGE).values()) {
            Matcher matcher = REGISTERED.matcher(text);
            while (matcher.find()) {
                scripts.add(matcher.group(1));
            }
        }
        return scripts;
    }
}
