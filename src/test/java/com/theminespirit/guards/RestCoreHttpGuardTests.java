package com.theminespirit.guards;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class RestCoreHttpGuardTests {
    private static final Path REST_CORE = Path.of("src/main/java/com/theminespirit/core/rest");
    private static final Pattern HTTP_IMPORT = Pattern.compile(
            "^import\\s+(static\\s+)?(com\\.sun\\.net\\.httpserver|java\\.net\\.http)\\b", Pattern.MULTILINE);

    @Test
    void restCore_Scanned_ImportsNoJdkHttpTypes() {
        List<String> offenders = new ArrayList<>();
        SourceFiles.javaUnder(REST_CORE).forEach((file, text) -> {
            Matcher matcher = HTTP_IMPORT.matcher(text);
            while (matcher.find()) {
                offenders.add(file + ":" + SourceFiles.lineOf(text, matcher.start()) + " " + matcher.group().strip());
            }
        });

        assertEquals(List.of(), offenders,
                "Пакет core/rest — логика маршрутизации, валидации и сериализации без транспорта: его проверяет чистый "
                        + "JUnit за секунды. Типы com.sun.net.httpserver и java.net.http живут только в neoforge/rest "
                        + "(RestServer): иначе замена HTTP-слоя или тест без сокетов станут невозможны. Перенеси код "
                        + "в адаптер, а в core оставь RestRequest/RestResponse. Нарушители");
    }

    @Test
    void httpImportPattern_ServerAndClientImports_AreDetected() {
        assertTrue(HTTP_IMPORT.matcher("import com.sun.net.httpserver." + "HttpServer;").find(),
                "Сторож ослеп: импорт HttpServer должен находиться");
        assertTrue(HTTP_IMPORT.matcher("import java.net.http." + "HttpClient;").find(),
                "Сторож ослеп: импорт HttpClient должен находиться");
        assertTrue(HTTP_IMPORT.matcher("import static com.sun.net.httpserver." + "HttpExchange.*;").find(),
                "Сторож ослеп: статический импорт должен находиться");
    }

    @Test
    void httpImportPattern_PlainNetImport_IsIgnored() {
        assertFalse(HTTP_IMPORT.matcher("import java.net.URI;").find(),
                "Сторож ложно срабатывает: java.net.URI не HTTP-тип транспорта");
    }

    @Test
    void restCoreDirectory_Scanned_HasSources() {
        assertFalse(SourceFiles.javaUnder(REST_CORE).isEmpty(),
                "Сторож не нашёл исходников core/rest: при переименовании пакета он зеленел бы вхолостую");
    }
}
