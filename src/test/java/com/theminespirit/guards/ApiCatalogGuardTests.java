package com.theminespirit.guards;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.rest.IdempotencyCache;
import com.theminespirit.core.rest.PolicyGateway;
import com.theminespirit.core.rest.PolicyRoutes;
import com.theminespirit.core.rest.Route;
import com.theminespirit.core.rest.UnprotectRequest;
import com.theminespirit.core.policy.UnprotectArea;
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
    private static final Path API_DIR = Path.of("docs/api");
    private static final String CATALOG_HEADING = "### GET /v1/capabilities";
    private static final Pattern BACKTICKED = Pattern.compile("`([^`\\n]+)`");
    private static final Pattern SCRIPT_ID = Pattern.compile("[a-z][a-z0-9_]*");
    private static final Set<String> RESULT_WORDS = Set.of("done", "error", "have", "missing", "null", "result");

    @Test
    void catalog_EveryScript_MentionedInApiDoc() throws IOException {
        List<String> missing = missingScripts(CoreScripts.defaultCatalog().all(), readApiDoc());

        assertEquals(List.of(), missing,
                "Скрипт каталога не упомянут в docs/api/README.md обратными кавычками: контракт и каталог разошлись, "
                        + "и клиент по документу не узнает скрипт, который /v1/capabilities уже отдаёт. "
                        + "Допишите строку с id и параметрами из ScriptSpec (docs/api/README.md → «GET /v1/capabilities»). "
                        + "Правило: каталог и docs/api/README.md не расходятся. Не упомянуты");
    }

    @Test
    void catalogSection_ScriptMissingFromCatalog_IsReported() throws IOException {
        String doc = readApiDoc();
        List<String> unknown = unknownScriptMentions(catalogSection(doc), idSet(CoreScripts.defaultCatalog()),
                knownNonScriptWords(CoreScripts.defaultCatalog()));

        assertEquals(List.of(), unknown,
                "В разделе каталога docs/api/README.md обещан скрипт, которого нет в ScriptSpec: дух его не выполнит, "
                        + "а /v1/capabilities не отдаст — документ врёт клиенту. Уберите устаревшее упоминание "
                        + "или добавьте скрипт в CoreScripts. Параметры, значения enum и слова итога задачи "
                        + "(result/error/done/have/missing/null) скриптами не считаются. "
                        + "Правило: docs/api/README.md → «GET /v1/capabilities». Лишние упоминания");
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

    @Test
    void policyRoutes_DeclaredInController_AreDocumentedInApiDoc() throws IOException {
        List<String> missing = undocumentedRoutes(readApiDoc(), new PolicyRoutes(new NoPolicyGateway(),
                new IdempotencyCache()).routes());

        assertEquals(List.of(), missing,
                "Маршрут /v1/policy/* объявлен в PolicyRoutes, но не описан в docs/api/README.md: клиент по документу "
                        + "не узнает конечную точку снятия защиты построек игрока. Допишите строку таблицы "
                        + "«Конечные точки» и раздел с телом запроса. Не описаны");
    }

    @Test
    void undocumentedRoutes_MissingPath_IsReported() {
        List<Route> routes = List.of(new Route("GET", "/v1/policy/unprotect", (request, params) -> null));

        assertEquals(List.of("GET /v1/policy/unprotect"), undocumentedRoutes("тут нет ничего", routes),
                "Сторож ослеп: маршрут, которого нет в тексте документа, обязан попасть в пропажу");
    }

    @Test
    void undocumentedRoutes_MethodAndPathPresent_ReportsNothing() {
        List<Route> routes = List.of(new Route("POST", "/v1/policy/unprotect", (request, params) -> null));

        assertEquals(List.of(), undocumentedRoutes("POST /v1/policy/unprotect", routes),
                "Сторож ложно срабатывает: метод и путь, найденные в тексте, — не пропажа");
    }

    private static List<String> undocumentedRoutes(String doc, List<Route> routes) {
        List<String> missing = new ArrayList<>();
        for (Route route : routes) {
            if (!doc.contains(route.method() + " " + route.pattern()) && !doc.contains(route.pattern())) {
                missing.add(route.method() + " " + route.pattern());
            }
        }
        return missing;
    }

    private static final class NoPolicyGateway implements PolicyGateway {
        @Override
        public List<UnprotectArea> areas() {
            return List.of();
        }

        @Override
        public UnprotectArea grant(UnprotectRequest request) {
            throw new UnsupportedOperationException("сторожу не нужна выдача");
        }

        @Override
        public UnprotectArea revoke(String id) {
            throw new UnsupportedOperationException("сторожу не нужно снятие");
        }
    }

    private static String readApiDoc() throws IOException {
        assertTrue(Files.isDirectory(API_DIR),
                "Сторож не нашёл " + API_DIR + ": тесты запускаются из корня проекта (./gradlew test)");
        StringBuilder text = new StringBuilder();
        try (var files = Files.list(API_DIR)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".md")).sorted().toList()) {
                text.append(Files.readString(file, StandardCharsets.UTF_8)).append('\n');
            }
        }
        return text.toString();
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
                "В docs/api/README.md нет раздела «" + CATALOG_HEADING + "»: заголовок переименован — поправь сторожа");
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
