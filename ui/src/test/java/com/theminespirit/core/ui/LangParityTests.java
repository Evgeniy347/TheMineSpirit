package com.theminespirit.core.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class LangParityTests {
    private static final Pattern KEY = Pattern.compile("^\\s*\"([^\"]+)\"\\s*:", Pattern.MULTILINE);
    private static final List<String> PREFIXES = List.of(
            "theminespirit.shell.",
            "theminespirit.board.",
            "theminespirit.map.",
            "theminespirit.mine.",
            "theminespirit.craft.",
            "theminespirit.order.",
            "theminespirit.tree.");

    @Test
    void lang_AllNamespaces_AreEqualInBothLocales() throws IOException {
        Set<String> ru = keys("ru_ru");
        Set<String> en = keys("en_us");
        for (String prefix : PREFIXES) {
            Set<String> ruKeys = filter(ru, prefix);
            Set<String> enKeys = filter(en, prefix);
            assertEquals(
                    ruKeys,
                    enKeys,
                    "Ключи " + prefix + " расходятся между ru_ru и en_us: иначе одна локаль покажет сырой ключ. "
                            + "При смене lang_AllNamespaces_AreEqualInBothLocales проверь это утверждение.");
        }
    }

    private static Set<String> filter(Set<String> keys, String prefix) {
        Set<String> filtered = new TreeSet<>();
        for (String key : keys) {
            if (key.startsWith(prefix)) {
                filtered.add(key);
            }
        }
        return filtered;
    }

    private static Set<String> keys(String lang) throws IOException {
        try (InputStream in =
                LangParityTests.class.getResourceAsStream("/assets/theminespirit/lang/" + lang + ".json")) {
            if (in == null) {
                throw new IOException(
                        "Нет lang/" + lang + ".json на тестовом classpath: без файла паритет не проверить");
            }
            Matcher matcher = KEY.matcher(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            Set<String> keys = new TreeSet<>();
            while (matcher.find()) {
                keys.add(matcher.group(1));
            }
            return keys;
        }
    }
}
