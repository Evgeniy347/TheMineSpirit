package com.theminespirit.guards;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.script.CoreScripts;
import com.theminespirit.core.script.ParamSpec;
import com.theminespirit.core.script.ScriptCatalog;
import com.theminespirit.core.script.ScriptSpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class ApiCatalogGuardTests {
    private static final Path API_DOC = Path.of("docs/API.md");
    private static final String CATALOG_HEADING = "### GET /v1/capabilities";
    private static final Pattern BACKTICKED = Pattern.compile("`([^`\\n]+)`");
    private static final Pattern SCRIPT_ID = Pattern.compile("[a-z][a-z0-9_]*");
    private static final Set<String> RESULT_WORDS = Set.of("done", "error", "have", "missing", "null", "result");

    @Test
    void catalog_EveryScript_MentionedInApiDoc() throws IOException {
        List<String> missing = missingScripts(CoreScripts.defaultCatalog().all(), readApiDoc());

        assertEquals(List.of(), missing,
                "Скрипт каталога не упомянут в docs/API.md обратными кавычками: контракт и каталог разошлись, "
                        + "и клиент по документу не узнает скрипт, который /v1/capabilities уже отдаёт. "
                        + "Допишите строку с id и параметрами из ScriptSpec (docs/API.md → «GET /v1/capabilities»). "
                        + "Правило: каталог и docs/API.md не расходятся. Не упомянуты");
    }

    @Test
    void catalogSection_ScriptMissingFromCatalog_IsReported() throws IOException {
        String doc = readApiDoc();
        List<String> unknown = unknownScriptMentions(catalogSection(doc), idSet(CoreScripts.defaultCatalog()),
                knownNonScriptWords(CoreScripts.defaultCatalog()));

        assertEquals(List.of(), unknown,
                "В разделе каталога docs/API.md обещан скрипт, которого нет в ScriptSpec: дух его не выполнит, "
                        + "а /v1/capabilities не отдаст — документ врёт клиенту. Уберите устаревшее упоминание "
                        + "или добавьте скрипт в CoreScripts. Параметры, значения enum и слова итога задачи "
                        + "(result/error/done/have/missing/null) скриптами не считаются. "
                        + "Правило: docs/API.md → «GET /v1/capabilities». Лишние упоминания");
    }

    @Test
    void missingScripts_ScriptAbsentFromDoc_IsReported() {
        List<ScriptSpec> catalog = List.of(spec("scan"), spec("feed"));

        assertEquals(List.of("feed"), missingScripts(catalog, "тут только `scan`"),
                "Сторож ослеп: id каталога, которого нет в документе в обратных кавычках, обязан попасть в пропажу");
    }

    @Test
    void missingScripts_AllMentioned_ReportsNothing() {
        List<ScriptSpec> catalog = List.of(spec("scan"), spec("feed"));

        assertEquals(List.of(), missingScripts(catalog, "тут `scan` и `feed`"),
                "Сторож ложно срабатывает: упомянутые в кавычках скрипты — не пропажа");
    }

    @Test
    void unknownScriptMentions_IdMissingFromCatalog_IsReported() {
        String section = CATALOG_HEADING + "\nСкрипты: `scan`, `dig_descent`.\n";

        assertEquals(List.of("dig_descent"), unknownScriptMentions(section, Set.of("scan"), Set.of()),
                "Сторож ослеп: упоминание скрипта, которого нет в каталоге, обязано попасть в лишние");
    }

    @Test
    void unknownScriptMentions_ParamsAndEnums_NotReported() {
        String section = CATALOG_HEADING + "\n`scan` (`radius`, `filter`), режим `loop`.\n";

        assertEquals(List.of(), unknownScriptMentions(section, Set.of("scan"), Set.of("radius", "filter", "loop")),
                "Сторож ложно срабатывает: параметры и значения enum — не имена скриптов");
    }

    @Test
    void catalogSection_ApiDoc_IsFound() {
        String section = catalogSection("### GET /v1/capabilities\nСкрипты.\n### GET /v1/bots\n");

        assertTrue(section.contains("Скрипты"),
                "Сторож ослеп: раздел каталога обязан находиться по заголовку и содержать текст до следующего заголовка");
        assertTrue(!section.contains("/v1/bots"),
                "Сторож слеп к границе: раздел каталога обязан кончаться на следующем заголовке `### `");
    }

    private static String readApiDoc() throws IOException {
        assertTrue(Files.isRegularFile(API_DOC),
                "Сторож не нашёл " + API_DOC + ": тесты запускаются из корня проекта (./gradlew test)");
        return Files.readString(API_DOC, StandardCharsets.UTF_8);
    }

    private static List<String> missingScripts(List<ScriptSpec> catalog, String doc) {
        Set<String> mentioned = new TreeSet<>(tokens(doc));
        List<String> missing = new ArrayList<>();
        for (ScriptSpec script : catalog) {
            if (!mentioned.contains(script.id())) {
                missing.add(script.id());
            }
        }
        return missing;
    }

    private static List<String> unknownScriptMentions(String section, Set<String> catalogIds,
            Set<String> nonScriptWords) {
        List<String> unknown = new ArrayList<>();
        for (String token : new TreeSet<>(tokens(section))) {
            if (!catalogIds.contains(token) && !nonScriptWords.contains(token)) {
                unknown.add(token);
            }
        }
        return unknown;
    }

    private static Set<String> idSet(ScriptCatalog catalog) {
        Set<String> ids = new TreeSet<>();
        catalog.all().forEach(script -> ids.add(script.id()));
        return ids;
    }

    private static Set<String> knownNonScriptWords(ScriptCatalog catalog) {
        Set<String> words = new TreeSet<>(RESULT_WORDS);
        for (ScriptSpec script : catalog.all()) {
            for (ParamSpec param : script.params()) {
                words.add(param.name());
                if (param.values() != null) {
                    words.addAll(param.values());
                }
            }
        }
        return words;
    }

    private static String catalogSection(String doc) {
        int start = doc.indexOf(CATALOG_HEADING);
        assertTrue(start >= 0,
                "В docs/API.md нет раздела «" + CATALOG_HEADING + "»: заголовок переименован — поправь сторожа");
        int next = doc.indexOf("\n### ", start + CATALOG_HEADING.length());
        return next < 0 ? doc.substring(start) : doc.substring(start, next);
    }

    private static List<String> tokens(String text) {
        List<String> tokens = new ArrayList<>();
        Matcher matcher = BACKTICKED.matcher(text);
        while (matcher.find()) {
            String token = matcher.group(1);
            if (SCRIPT_ID.matcher(token).matches()) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    private static ScriptSpec spec(String id) {
        return new ScriptSpec(id, "образец", List.of(), List.of(), List.of());
    }
}
