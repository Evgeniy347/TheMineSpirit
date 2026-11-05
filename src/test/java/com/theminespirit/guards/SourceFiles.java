package com.theminespirit.guards;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
    public static final Path MECH_MINING = Path.of(layout().getProperty("mech_mining"));
    public static final Path MECH_MINING_TEST = Path.of(layout().getProperty("mech_mining_test"));
    public static final Path MECH_MOVEMENT = Path.of(layout().getProperty("mech_movement"));
    public static final Path MECH_MOVEMENT_TEST = Path.of(layout().getProperty("mech_movement_test"));
    public static final Path MECH_INVENTORY = Path.of(layout().getProperty("mech_inventory"));
    public static final Path MECH_INVENTORY_TEST = Path.of(layout().getProperty("mech_inventory_test"));
    public static final Path RESOURCES = Path.of(layout().getProperty("resources"));
    public static final Path TEST_RESOURCES = Path.of(layout().getProperty("test_resources"));
    public static final Path TEMPLATES = Path.of(layout().getProperty("templates"));
    public static final Path GENERATED_RESOURCES = Path.of(layout().getProperty("generated_resources"));
    public static final Path GAME_TESTS = MAIN.resolve(layout().getProperty("gametests"));

    private static final String PACKAGE_ROOT = layout().getProperty("package_root");
    private static final Path MAIN_PACKAGE = MAIN.resolve(PACKAGE_ROOT);
    private static final Path TEST_PACKAGE = TEST.resolve(PACKAGE_ROOT);
    private static final Path API_PACKAGE = API.resolve(PACKAGE_ROOT);
    private static final Path API_TEST_PACKAGE = API_TEST.resolve(PACKAGE_ROOT);
    public static final Path MECH_MINING_PACKAGE = MECH_MINING.resolve(PACKAGE_ROOT);
    public static final Path MECH_MINING_TEST_PACKAGE = MECH_MINING_TEST.resolve(PACKAGE_ROOT);
    public static final Path MECH_MOVEMENT_PACKAGE = MECH_MOVEMENT.resolve(PACKAGE_ROOT);
    public static final Path MECH_MOVEMENT_TEST_PACKAGE = MECH_MOVEMENT_TEST.resolve(PACKAGE_ROOT);
    public static final Path MECH_INVENTORY_PACKAGE = MECH_INVENTORY.resolve(PACKAGE_ROOT);
    public static final Path MECH_INVENTORY_TEST_PACKAGE = MECH_INVENTORY_TEST.resolve(PACKAGE_ROOT);
    private static final List<Path> MAIN_ROOTS =
            List.of(MAIN_PACKAGE, API_PACKAGE, MECH_MINING_PACKAGE, MECH_MOVEMENT_PACKAGE, MECH_INVENTORY_PACKAGE);
    private static final List<Path> TEST_ROOTS = List.of(
            TEST_PACKAGE,
            API_TEST_PACKAGE,
            MECH_MINING_TEST_PACKAGE,
            MECH_MOVEMENT_TEST_PACKAGE,
            MECH_INVENTORY_TEST_PACKAGE);
    private static final List<Path> ALL_PACKAGE_ROOTS =
            Stream.concat(MAIN_ROOTS.stream(), TEST_ROOTS.stream()).toList();

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
        require(properties, "mech_mining");
        require(properties, "mech_mining_test");
        require(properties, "mech_movement");
        require(properties, "mech_movement_test");
        require(properties, "mech_inventory");
        require(properties, "mech_inventory_test");
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
        for (Path root : ALL_PACKAGE_ROOTS) {
            Path candidate = root.resolve(normalized).normalize();
            if (Files.exists(candidate)) {
                return candidate;
            }
        }
        throw new IllegalArgumentException(
                "Логический путь " + normalized + " не найден ни в " + roots(ALL_PACKAGE_ROOTS.toArray(new Path[0])));
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
        return MAIN_ROOTS;
    }

    public static List<Path> testPackages() {
        return TEST_ROOTS;
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
        return List.of(
                MAIN,
                TEST,
                API,
                API_TEST,
                MECH_MINING,
                MECH_MINING_TEST,
                MECH_MOVEMENT,
                MECH_MOVEMENT_TEST,
                MECH_INVENTORY,
                MECH_INVENTORY_TEST);
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
        for (Path root : ALL_PACKAGE_ROOTS) {
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
