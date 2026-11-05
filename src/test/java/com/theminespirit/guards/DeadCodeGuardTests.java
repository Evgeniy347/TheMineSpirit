package com.theminespirit.guards;

import static com.theminespirit.guards.DeadCodeParser.TEST_METHOD;
import static com.theminespirit.guards.DeadCodeScanner.deadCode;
import static com.theminespirit.guards.DeadCodeSource.strip;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.theminespirit.guards.DeadCodeModel.Finding;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import org.junit.jupiter.api.Test;

class DeadCodeGuardTests {
    private static final List<DeadCodeExemptions.Exemption> EXCEPTIONS = DeadCodeExemptions.all();

    @Test
    void productionSources_EveryDeclaration_IsReferencedOrEntryPoint() {
        Set<String> knownTests = testMethodNames();
        List<String> missingTests = DeadCodeExemptions.SCALE_PROBES.stream()
                .filter(probe -> !knownTests.contains(probe.test()))
                .map(probe -> probe.key() + " — нет теста " + probe.test())
                .toList();
        assertEquals(
                List.of(),
                missingTests,
                "Проба масштаба ссылается на несуществующий тест: исключение устарело и прикрывает мёртвый код. "
                        + "Верните тест или удалите пробу: " + missingTests);
        List<Finding> findings = deadCode(production(), gameTests());
        List<String> offenders = new ArrayList<>();
        List<String> stale = new ArrayList<>();
        for (Finding finding : findings) {
            if (exempt(finding)) {
                continue;
            }
            offenders.add(finding.describe());
        }
        for (DeadCodeExemptions.Exemption exception : EXCEPTIONS) {
            boolean found = findings.stream().anyMatch(finding -> covered(exception, finding));
            if (!found) {
                stale.add(exception.key() + " — " + exception.reason());
            }
        }

        assertEquals(
                List.of(),
                stale,
                "Исключение сторожа мёртвого кода устарело: объявление используется или удалено, "
                        + "поэтому исключение больше ничего не разрешает. Удалите строку из DeadCodeExemptions.");
        assertEquals(
                List.of(),
                offenders,
                "Production-код недостижим: на класс/метод/поле нет ссылок из другого production-кода "
                        + "(ссылки из src/test/java и gametest не считаются). Цена: код не работает в игре, "
                        + "его тесты проверяют только себя, а правки в нём ничего не меняют. Как надо: удалить "
                        + "мёртвое объявление вместе с тестами, которые проверяют только его; заготовку под "
                        + "открытую задачу — в DeadCodeExemptions.OPEN_TASKS с причиной и ссылкой на задачу. Найдено "
                        + offenders.size() + ": " + String.join("; ", offenders));
    }

    @Test
    void deadCodeScanner_JeiPluginClass_IsEntryPoint() {
        Map<Path, String> sources = new LinkedHashMap<>();
        sources.put(Path.of("Plugin.java"), """
                package sample;

                @JeiPlugin
                public final class Plugin {
                }
                """);

        assertEquals(
                List.of(),
                deadCode(sources).stream().map(Finding::describe).toList(),
                "Класс с @JeiPlugin находит и создаёт JEI по аннотации: ссылок из кода мода на него нет, "
                        + "но он живой — иначе интеграцию с JEI пришлось бы удалить");
    }

    @Test
    void deadCodeScanner_UnreferencedClassMethodAndField_AreFound() {
        Map<Path, String> sources = new LinkedHashMap<>();
        sources.put(Path.of("Live.java"), """
                package sample;

                @Mod
                public class Live {
                    private int unusedField;
                    private void unusedMethod() {}
                    private static class UnusedNested {}
                    private void usedMethod() { System.out.println("UnusedNested"); }
                    public Live() { usedMethod(); }
                }
                """);

        assertEquals(
                List.of(
                        "Live.java:7 class Live#UnusedNested",
                        "Live.java:5 field Live#unusedField",
                        "Live.java:6 method Live#unusedMethod"),
                deadCode(sources).stream().map(Finding::describe).toList(),
                "Сторож ослеп: поле, метод и вложенный класс без ссылок из production-кода обязаны находиться; "
                        + "имя в строке не ссылка, а вызывающий метод — не мёртвый");
    }

    @Test
    void deadCodeScanner_EntryPoints_AreNotReported() {
        Map<Path, String> sources = new LinkedHashMap<>();
        sources.put(Path.of("Entry.java"), """
                package sample;

                import java.util.List;

                @EventBusSubscriber
                public class Entry {
                    @SubscribeEvent
                    public void onEvent(Object event) {}

                    @Override
                    public String toString() { return "entry"; }

                    public static void main(String[] args) { new Point(1, 2); }
                }

                record Point(int x, int y) {}

                @interface Marker {
                    String value();
                }
                """);

        assertEquals(
                List.of(),
                deadCode(sources).stream().map(Finding::describe).toList(),
                "Сторож ложно срабатывает на точках входа: @EventBusSubscriber, @SubscribeEvent, @Override, main, "
                        + "компоненты record и элементы аннотации — не мёртвый код");
    }

    @Test
    void deadCodeScanner_CommentAndStringReferences_DoNotCount() {
        Map<Path, String> sources = new LinkedHashMap<>();
        sources.put(Path.of("Speaks.java"), """
                package sample;

                class Speaks {
                    // DeadWord упомянут только в комментарии
                    String text = "DeadWord";
                }
                """);

        assertEquals(
                List.of("Speaks.java:3 class <top>#Speaks", "Speaks.java:5 field Speaks#text"),
                deadCode(sources).stream().map(Finding::describe).toList(),
                "Сторож ложно считает ссылкой упоминание в комментарии или строке: такой код остаётся мёртвым");
    }

    @Test
    void deadCodeScanner_CodeUsedOnlyByDeadCode_IsFound() {
        Map<Path, String> sources = new LinkedHashMap<>();
        sources.put(Path.of("Orphan.java"), """
                class Orphan {
                    Helper helper = new Helper();
                    void tick() { helper.run(); }
                }
                class Helper {
                    void run() {}
                }
                """);

        assertEquals(
                List.of(
                        "Orphan.java:5 class <top>#Helper",
                        "Orphan.java:1 class <top>#Orphan",
                        "Orphan.java:2 field Orphan#helper",
                        "Orphan.java:6 method Helper#run",
                        "Orphan.java:3 method Orphan#tick"),
                deadCode(sources).stream().map(Finding::describe).toList(),
                "Сторож считает ссылкой вызов из мёртвого кода: класс, поле и метод, на которые ссылаются "
                        + "только недостижимые друг от друга объявления, обязаны находиться. Цена: мёртвая "
                        + "подсистема держит тесты и выглядит живой");
    }

    @Test
    void deadCodeScanner_TransitiveEntryPointChain_IsLive() {
        Map<Path, String> sources = new LinkedHashMap<>();
        sources.put(Path.of("Entry.java"), """
                @Mod
                class Entry {
                    Entry() { start(); }
                    void start() { new Worker().work(); }
                }
                class Worker {
                    void work() { System.out.println("ok"); }
                }
                """);

        assertEquals(
                List.of(),
                deadCode(sources).stream().map(Finding::describe).toList(),
                "Сторож ослеп на достижимости: точка входа @Mod зовёт Worker, а тот — work; это живая цепочка");
    }

    static Set<String> testMethodNames() {
        Set<String> names = new HashSet<>();
        Matcher matcher;
        for (Map<Path, String> sources : List.of(SourceFiles.allTests(), gameTests())) {
            for (String source : sources.values()) {
                matcher = TEST_METHOD.matcher(strip(source));
                while (matcher.find()) {
                    names.add(matcher.group(1));
                }
            }
        }
        return names;
    }

    static Map<Path, String> gameTests() {
        Map<Path, String> sources = new TreeMap<>();
        SourceFiles.allMain().forEach((file, text) -> {
            if (file.startsWith(SourceFiles.GAME_TESTS)) {
                sources.put(file, text);
            }
        });
        return sources;
    }

    static Map<Path, String> production() {
        Map<Path, String> sources = new TreeMap<>();
        SourceFiles.allMain().forEach((file, text) -> {
            if (!file.startsWith(SourceFiles.GAME_TESTS)) {
                sources.put(file, text);
            }
        });
        return sources;
    }

    static boolean exempt(Finding finding) {
        return EXCEPTIONS.stream().anyMatch(exception -> covered(exception, finding));
    }

    static boolean covered(DeadCodeExemptions.Exemption exception, Finding finding) {
        if (exception.key().equals(finding.key())) {
            return true;
        }
        int type = exception.key().indexOf("|class|");
        if (type < 0) {
            return false;
        }
        String file = exception.key().substring(0, type);
        String name = exception.key().substring(exception.key().indexOf('#', type) + 1);
        boolean typeDeclaration = switch (finding.kind()) {
            case "class", "interface", "enum", "record" -> true;
            default -> false;
        };
        return file.equals(finding.file())
                && (name.equals(finding.owner()) || (typeDeclaration && name.equals(finding.name())));
    }
}
