package com.theminespirit.guards;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class SourceFiles {
    public static final Path MAIN = Path.of(layout().getProperty("main"));
    public static final Path TEST = Path.of(layout().getProperty("test"));
    public static final Path API = Path.of(layout().getProperty("api"));
    public static final Path API_TEST = Path.of(layout().getProperty("api_test"));
    public static final Path RESOURCES = Path.of(layout().getProperty("resources"));
    public static final Path TEST_RESOURCES = Path.of(layout().getProperty("test_resources"));
    public static final Path TEMPLATES = Path.of(layout().getProperty("templates"));
    public static final Path GENERATED_RESOURCES = Path.of(layout().getProperty("generated_resources"));
    public static final Path GAME_TESTS = Path.of(layout().getProperty("gametests"));

    private static final Path SETTINGS = Path.of("settings.gradle");
    private static final Pattern INCLUDE = Pattern.compile("^\\s*include\\s+'(:[^']+)'", Pattern.MULTILINE);
    private static final String PACKAGE_ROOT = layout().getProperty("package_root");
    private static final Path MAIN_PACKAGE = MAIN.resolve(PACKAGE_ROOT);
    private static final Path TEST_PACKAGE = TEST.resolve(PACKAGE_ROOT);
    private static final Path API_PACKAGE = API.resolve(PACKAGE_ROOT);
    private static final Path API_TEST_PACKAGE = API_TEST.resolve(PACKAGE_ROOT);
    private static final List<Path> MODULE_ROOTS = moduleRoots("src/main/java");
    private static final List<Path> MODULE_TEST_ROOTS = moduleRoots("src/test/java");
    private static final List<Path> MODULE_PACKAGES =
            MODULE_ROOTS.stream().map(root -> root.resolve(PACKAGE_ROOT)).toList();
    private static final List<Path> MODULE_TEST_PACKAGES =
            MODULE_TEST_ROOTS.stream().map(root -> root.resolve(PACKAGE_ROOT)).toList();

    private static final Map<Path, Map<Path, String>> CACHE = new ConcurrentHashMap<>();
    private static final Pattern TEXT_BLOCK = Pattern.compile("\"\"\"[ \\t]*\\n(?s:.*?)(?<!\\\\)\"\"\"");

    private SourceFiles() {}

    private static Path layoutPath() {
        return Path.of("config/layout.properties");
    }

    private static Properties layout() {
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(layoutPath())) {
            properties.load(input);
        } catch (IOException e) {
            throw new UncheckedIOException("Сторож не смог прочитать карту путей " + layoutPath(), e);
        }
        require(properties, "package_root");
        require(properties, "main");
        require(properties, "test");
        require(properties, "api");
        require(properties, "api_test");
        require(properties, "resources");
        require(properties, "test_resources");
        require(properties, "templates");
        require(properties, "generated_resources");
        require(properties, "jar_dir");
        require(properties, "gametests");
        return properties;
    }

    private static void require(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("В " + layoutPath() + " нет ключа " + key);
        }
    }

    public static Path path(String logical) {
        String normalized = logical.replace('\\', '/');
        for (Path root : searchPackages()) {
            Path candidate = root.resolve(normalized).normalize();
            if (Files.exists(candidate)) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("Логический путь " + normalized + " не найден ни в "
                + roots(searchPackages().toArray(Path[]::new)));
    }

    private static List<Path> searchPackages() {
        List<Path> all = new ArrayList<>(List.of(MAIN_PACKAGE, TEST_PACKAGE, API_PACKAGE, API_TEST_PACKAGE));
        all.addAll(MODULE_PACKAGES);
        all.addAll(MODULE_TEST_PACKAGES);
        return all;
    }

    private static List<Path> moduleRoots(String sourceSet) {
        String settings;
        try {
            settings = Files.readString(SETTINGS);
        } catch (IOException e) {
            throw new UncheckedIOException("Сторож не смог прочитать список модулей " + SETTINGS, e);
        }
        List<Path> roots = new ArrayList<>();
        Matcher matcher = INCLUDE.matcher(settings);
        while (matcher.find()) {
            String id = matcher.group(1);
            if (":api".equals(id)) {
                continue;
            }
            roots.add(Path.of(id.substring(1).replace(':', '/'), sourceSet));
        }
        return List.copyOf(roots);
    }

    private static String roots(Path... roots) {
        StringBuilder text = new StringBuilder();
        for (Path root : roots) {
            if (text.length() > 0) {
                text.append(", ни в ");
            }
            text.append(display(root));
        }
        return text.toString();
    }

    public static List<Path> mainPackages() {
        List<Path> all = new ArrayList<>(List.of(MAIN_PACKAGE, API_PACKAGE));
        all.addAll(MODULE_PACKAGES);
        return List.copyOf(all);
    }

    public static List<Path> testPackages() {
        List<Path> all = new ArrayList<>(List.of(TEST_PACKAGE, API_TEST_PACKAGE));
        all.addAll(MODULE_TEST_PACKAGES);
        return List.copyOf(all);
    }

    public static List<Path> moduleMainRoots() {
        return MODULE_ROOTS;
    }

    public static List<Path> moduleTestRoots() {
        return MODULE_TEST_ROOTS;
    }

    public static Path apiPackage() {
        return API_PACKAGE;
    }

    public static Path apiTestPackage() {
        return API_TEST_PACKAGE;
    }

    public static Map<Path, String> allMain() {
        Map<Path, String> sources = new TreeMap<>();
        for (Path root : mainPackages()) {
            if (Files.isDirectory(root)) {
                sources.putAll(javaUnder(root));
            }
        }
        return Collections.unmodifiableMap(sources);
    }

    public static Map<Path, String> allTests() {
        Map<Path, String> sources = new TreeMap<>();
        for (Path root : testPackages()) {
            if (Files.isDirectory(root)) {
                sources.putAll(javaUnder(root));
            }
        }
        return Collections.unmodifiableMap(sources);
    }

    public static List<Path> sourceRoots() {
        List<Path> all = new ArrayList<>(List.of(MAIN, TEST, API, API_TEST));
        all.addAll(MODULE_ROOTS);
        all.addAll(MODULE_TEST_ROOTS);
        return List.copyOf(all);
    }

    public static boolean isSourcePath(String relativePath) {
        String normalized = relativePath.replace('\\', '/');
        for (Path root : sourceRoots()) {
            if (normalized.startsWith(root.toString().replace('\\', '/') + "/")) {
                return true;
            }
        }
        return false;
    }

    private static String display(Path path) {
        return path.toString().replace('\\', '/');
    }

    public static String logical(Path file) {
        Path normalized = file.normalize();
        for (Path root : searchPackages()) {
            if (normalized.startsWith(root)) {
                return root.relativize(normalized).toString().replace('\\', '/');
            }
        }
        return normalized.toString().replace('\\', '/');
    }

    public static Path resource(String logical) {
        return RESOURCES.resolve(logical.replace('\\', '/')).normalize();
    }

    public static Path testResource(String logical) {
        return TEST_RESOURCES.resolve(logical.replace('\\', '/')).normalize();
    }

    public static Map<Path, String> javaUnder(Path root) {
        assertTrue(
                Files.isDirectory(root),
                "Сторож не нашёл каталог " + root + ": тесты запускаются из корня проекта (./gradlew test)");
        return CACHE.computeIfAbsent(root, SourceFiles::readJava);
    }

    private static Map<Path, String> readJava(Path root) {
        try (Stream<Path> files = Files.walk(root)) {
            SortedMap<Path, String> sources = new TreeMap<>();
            for (Path file :
                    files.filter(path -> path.toString().endsWith(".java")).toList()) {
                sources.put(file, Files.readString(file));
            }
            return Collections.unmodifiableSortedMap(sources);
        } catch (IOException e) {
            throw new UncheckedIOException("Сторож не смог прочитать исходники в " + root, e);
        }
    }

    public static String withoutTextBlocks(String text) {
        Matcher matcher = TEXT_BLOCK.matcher(text);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String newlines = matcher.group().replaceAll("[^\\n]", "");
            matcher.appendReplacement(out, Matcher.quoteReplacement("\"\"" + newlines));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    public static int lineOf(String text, int offset) {
        int line = 1;
        for (int i = 0; i < offset; i++) {
            if (text.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }
}
