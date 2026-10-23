package com.theminespirit.core.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardStatus;
import com.theminespirit.core.board.BuildingLevels;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class BoardLangTests {
    private static final Pattern KEY = Pattern.compile("^\\s*\"([^\"]+)\"\\s*:", Pattern.MULTILINE);
    private static final String PREFIX = "theminespirit.board.";

    private static Set<String> keys(String lang) throws IOException {
        try (InputStream in =
                BoardLangTests.class.getResourceAsStream("/assets/theminespirit/lang/" + lang + ".json")) {
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
    void langFiles_EveryCaseStatusAndGroup_HaveTranslation() throws IOException {
        List<String> expected = new ArrayList<>();
        for (BoardCase boardCase : BoardCase.values()) {
            expected.add(PREFIX + "case." + boardCase.name().toLowerCase(Locale.ROOT));
        }
        for (BoardStatus status : BoardStatus.values()) {
            expected.add(PREFIX + "status." + status.name().toLowerCase(Locale.ROOT));
        }
        for (BoardTileGroup group : BoardTileGroup.values()) {
            expected.add(PREFIX + "group." + group.name().toLowerCase(Locale.ROOT));
        }
        for (int stage = 1; stage <= BuildingLevels.maxLevel(BoardCase.BUILD_HOUSE); stage++) {
            expected.add(new BoardStage(stage).nameKey());
        }
        for (String lang : List.of("ru_ru", "en_us")) {
            List<String> missing = new ArrayList<>(expected);
            missing.removeAll(keys(lang));
            assertEquals(
                    List.of(),
                    missing,
                    "Новый кейс, статус, группа или этап поселения доски без перевода в " + lang
                            + ": игрок увидит на экране доски имя константы. Добавьте ключ в "
                            + "src/main/resources/assets/theminespirit/lang/" + lang + ".json");
        }
    }

    @Test
    void langFiles_RuAndEn_HaveSameKeys() throws IOException {
        assertEquals(
                keys("ru_ru"),
                keys("en_us"),
                "Ключи ru_ru и en_us разошлись: игрок с другим языком увидит сырой ключ вместо текста");
    }
}
