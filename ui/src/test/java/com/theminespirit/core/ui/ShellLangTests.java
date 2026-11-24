package com.theminespirit.core.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class ShellLangTests {
    private static final Pattern KEY = Pattern.compile("^\\s*\"([^\"]+)\"\\s*:", Pattern.MULTILINE);

    private static Set<String> keys(String lang) throws IOException {
        try (InputStream in =
                ShellLangTests.class.getResourceAsStream("/assets/theminespirit/lang/" + lang + ".json")) {
            if (in == null) {
                return Set.of();
            }
            Matcher matcher = KEY.matcher(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            Set<String> keys = new TreeSet<>();
            while (matcher.find()) {
                keys.add(matcher.group(1));
            }
            return keys;
        }
    }

    @Test
    void langFiles_ShellTexts_HaveTranslations() throws IOException {
        List<String> expected = List.of(
                "theminespirit.shell.waiting",
                "theminespirit.shell.updated",
                "theminespirit.shell.hint.board",
                "theminespirit.shell.hint.plan",
                "theminespirit.shell.hint.mine",
                "theminespirit.shell.hint.craft",
                "theminespirit.shell.hint.tree");
        for (String lang : List.of("ru_ru", "en_us")) {
            List<String> missing = new ArrayList<>(expected);
            missing.removeAll(keys(lang));
            assertEquals(
                    List.of(),
                    missing,
                    "Текст каркаса экранов без перевода в " + lang
                            + ": шапка или подвал покажет сырой ключ. Добавьте ключ в lang/" + lang + ".json"
                            + " Иначе поломка пройдёт незамеченной и сломает экран поселения при следующей правке. При смене langFiles_ShellTexts_HaveTranslations проверь это утверждение.");
        }
    }
}
