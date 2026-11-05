package com.theminespirit.guards;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class DeadCodeGuardTests {
    private static final Pattern IMPORT = Pattern.compile("(?m)^[ \\t]*(?:import|package)\\s[^;]*;");
    private static final Pattern TYPE =
            Pattern.compile("(?<![\\w$.])(@)?(class|interface|enum|record)\\s+([A-Za-z_$][\\w$]*)");
    private static final String ANNOTATION = "@[A-Za-z_$][\\w$.]*(?:\\s*\\([^()]*\\))?\\s*";
    private static final String MODIFIER =
            "(?:public|protected|private|static|final|abstract|synchronized|native|default|strictfp|sealed|"
                    + "non-sealed|transient|volatile)\\s+";
    private static final String PREFIX = "(?:(?:" + ANNOTATION + ")|(?:" + MODIFIER + "))*";
    private static final Pattern METHOD = Pattern.compile(
            "(?:^|[;{}])\\s*" + PREFIX
                    + "(?:<[^;{}()]*>\\s*)?"
                    + "([A-Za-z_$][\\w$.]*(?:\\s*<[^{};()]*>)?(?:\\s*\\[\\])*)\\s+"
                    + "([A-Za-z_$][\\w$]*)\\s*\\(",
            Pattern.MULTILINE);
    private static final Pattern TEST_METHOD = Pattern.compile("\\bvoid\\s+([A-Za-z_$][\\w$]*)\\s*\\(");
    private static final Pattern FIELD = Pattern.compile(
            "(?:^|[;{}])\\s*" + PREFIX
                    + "([A-Za-z_$][\\w$.]*(?:\\s*<[^{};()]*>)?(?:\\s*\\[\\])*)\\s+"
                    + "([A-Za-z_$][\\w$]*)\\s*(?==|;)",
            Pattern.MULTILINE);
    private static final Set<String> RESERVED = Set.of(
            "package",
            "import",
            "new",
            "return",
            "throw",
            "else",
            "if",
            "for",
            "while",
            "switch",
            "case",
            "do",
            "try",
            "catch",
            "finally",
            "instanceof",
            "assert",
            "yield",
            "class",
            "interface",
            "enum",
            "record",
            "extends",
            "implements",
            "permits",
            "throws",
            "this",
            "super");

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

    private static Set<String> testMethodNames() {
        Set<String> names = new HashSet<>();
        Matcher matcher;
        for (Map<Path, String> sources : List.of(SourceFiles.javaUnder(SourceFiles.TEST), gameTests())) {
            for (String source : sources.values()) {
                matcher = TEST_METHOD.matcher(strip(source));
                while (matcher.find()) {
                    names.add(matcher.group(1));
                }
            }
        }
        return names;
    }

    private static Map<Path, String> gameTests() {
        Map<Path, String> sources = new TreeMap<>();
        SourceFiles.javaUnder(SourceFiles.MAIN).forEach((file, text) -> {
            if (file.startsWith(SourceFiles.GAME_TESTS)) {
                sources.put(file, text);
            }
        });
        return sources;
    }

    private static Map<Path, String> production() {
        Map<Path, String> sources = new TreeMap<>();
        SourceFiles.javaUnder(SourceFiles.MAIN).forEach((file, text) -> {
            if (!file.startsWith(SourceFiles.GAME_TESTS)) {
                sources.put(file, text);
            }
        });
        return sources;
    }

    private static List<Finding> deadCode(Map<Path, String> sources) {
        return deadCode(sources, Map.of());
    }

    private static List<Finding> deadCode(Map<Path, String> production, Map<Path, String> entrySources) {
        Map<Path, Parsed> parsed = new TreeMap<>();
        Map<String, List<Declaration>> byName = new HashMap<>();
        for (Map.Entry<Path, String> entry : production.entrySet()) {
            addParsed(parsed, byName, entry, false);
        }
        for (Map.Entry<Path, String> entry : entrySources.entrySet()) {
            addParsed(parsed, byName, entry, true);
        }
        Set<String> liveTypes = liveTypes(parsed, byName);
        Map<String, Integer> references = references(parsed, byName, liveTypes, entrySources.keySet());
        Map<String, Integer> declarations = new HashMap<>();
        for (Map.Entry<Path, Parsed> entry : parsed.entrySet()) {
            if (!production.containsKey(entry.getKey())) {
                continue;
            }
            for (Declaration declaration : entry.getValue().declarations()) {
                if (!isType(declaration)) {
                    declarations.merge(memberKey(declaration), 1, Integer::sum);
                }
            }
        }
        List<Finding> findings = new ArrayList<>();
        for (Map.Entry<Path, Parsed> entry : parsed.entrySet()) {
            if (!production.containsKey(entry.getKey())) {
                continue;
            }
            for (Declaration declaration : entry.getValue().declarations()) {
                if (declaration.entry()) {
                    continue;
                }
                boolean dead;
                if (isType(declaration)) {
                    dead = !liveTypes.contains(declaration.name());
                } else {
                    int used = references.getOrDefault(memberKey(declaration), 0);
                    int declared = declarations.getOrDefault(memberKey(declaration), 0);
                    dead = !liveTypes.contains(declaration.owner()) || used == declared;
                }
                if (dead) {
                    findings.add(new Finding(
                            declaration.file(),
                            declaration.line(),
                            declaration.kind(),
                            declaration.owner(),
                            declaration.name()));
                }
            }
        }
        findings.sort((first, second) -> first.key().compareTo(second.key()));
        return findings;
    }

    private static void addParsed(
            Map<Path, Parsed> parsed,
            Map<String, List<Declaration>> byName,
            Map.Entry<Path, String> entry,
            boolean everyDeclarationIsEntry) {
        Parsed file = parse(entry.getKey(), entry.getValue());
        if (everyDeclarationIsEntry) {
            List<Declaration> entries = file.declarations().stream()
                    .map(declaration -> new Declaration(
                            declaration.file(),
                            declaration.line(),
                            declaration.kind(),
                            declaration.owner(),
                            declaration.name(),
                            declaration.offset(),
                            declaration.end(),
                            true))
                    .toList();
            file = new Parsed(file.tokens(), entries, file.ignored(), file.importedTypes(), file.staticImports());
        }
        parsed.put(entry.getKey(), file);
        for (Declaration declaration : file.declarations()) {
            byName.computeIfAbsent(declaration.name(), name -> new ArrayList<>())
                    .add(declaration);
        }
    }

    private static Set<String> liveTypes(Map<Path, Parsed> parsed, Map<String, List<Declaration>> byName) {
        Map<String, Set<String>> edges = new HashMap<>();
        Map<String, Declaration> types = new HashMap<>();
        Set<String> roots = new HashSet<>();
        Set<String> entryOwners = new HashSet<>();
        parsed.values().stream()
                .flatMap(file -> file.declarations().stream())
                .filter(declaration -> declaration.entry() && !isType(declaration) && "main".equals(declaration.name()))
                .forEach(declaration -> entryOwners.add(declaration.owner()));
        for (Parsed file : parsed.values()) {
            List<Declaration> fileTypes = file.declarations().stream()
                    .filter(DeadCodeGuardTests::isType)
                    .toList();
            Set<Integer> declarationOffsets = new HashSet<>();
            file.declarations().forEach(declaration -> declarationOffsets.add(declaration.offset()));
            for (Declaration type : fileTypes) {
                types.put(key(type), type);
                if (type.entry() || entryOwners.contains(type.name())) {
                    roots.add(key(type));
                }
                for (Token token : file.tokens()) {
                    if (token.offset() <= type.offset()
                            || token.offset() >= type.end()
                            || !token.identifier()
                            || file.ignored()[token.offset()]
                            || declarationOffsets.contains(token.offset())) {
                        continue;
                    }
                    for (Declaration candidate : byName.getOrDefault(token.text(), List.of())) {
                        if (isType(candidate)) {
                            edge(edges, type, candidate);
                        }
                    }
                }
                for (String imported : file.importedTypes()) {
                    for (Declaration candidate : byName.getOrDefault(imported, List.of())) {
                        if (isType(candidate)) {
                            edge(edges, type, candidate);
                        }
                    }
                }
            }
        }
        Set<String> reached = new HashSet<>();
        Deque<String> queue = new ArrayDeque<>();
        for (String root : roots) {
            if (reached.add(root)) {
                queue.add(root);
            }
        }
        while (!queue.isEmpty()) {
            String current = queue.removeFirst();
            for (String next : edges.getOrDefault(current, Set.of())) {
                if (reached.add(next)) {
                    queue.add(next);
                }
            }
        }
        Set<String> live = new HashSet<>();
        for (String key : reached) {
            Declaration type = types.get(key);
            if (type != null) {
                live.add(type.name());
            }
        }
        return live;
    }

    private static Map<String, Integer> references(
            Map<Path, Parsed> parsed,
            Map<String, List<Declaration>> byName,
            Set<String> liveTypes,
            Set<Path> entrySources) {
        Map<String, Integer> references = new HashMap<>();
        for (Map.Entry<Path, Parsed> entry : parsed.entrySet()) {
            Parsed file = entry.getValue();
            boolean allLive = entrySources.contains(entry.getKey());
            List<Declaration> types = file.declarations().stream()
                    .filter(DeadCodeGuardTests::isType)
                    .sorted((first, second) -> Integer.compare(first.offset(), second.offset()))
                    .toList();
            Map<Integer, Declaration> members = new HashMap<>();
            file.declarations().stream()
                    .filter(declaration -> !isType(declaration))
                    .forEach(declaration -> members.put(declaration.offset(), declaration));
            Deque<Declaration> stack = new ArrayDeque<>();
            int next = 0;
            List<Token> tokens = file.tokens();
            for (int index = 0; index < tokens.size(); index++) {
                Token token = tokens.get(index);
                while (next < types.size() && types.get(next).offset() <= token.offset()) {
                    stack.push(types.get(next));
                    next++;
                }
                while (!stack.isEmpty() && stack.peek().end() <= token.offset()) {
                    stack.pop();
                }
                if (!token.identifier() || file.ignored()[token.offset()] || stack.isEmpty()) {
                    continue;
                }
                Declaration type = stack.peek();
                if (!allLive && !liveTypes.contains(type.name())) {
                    continue;
                }
                Declaration declaration = members.get(token.offset());
                if (declaration != null) {
                    references.merge(memberKey(declaration), 1, Integer::sum);
                    continue;
                }
                for (String owner : memberOwners(file, tokens, index, type, byName)) {
                    references.merge(owner + "#" + token.text(), 1, Integer::sum);
                }
            }
        }
        return references;
    }

    private static List<String> memberOwners(
            Parsed file, List<Token> tokens, int index, Declaration type, Map<String, List<Declaration>> byName) {
        Token token = tokens.get(index);
        boolean qualified = index > 1 && ".".equals(tokens.get(index - 1).text());
        if (qualified) {
            Token qualifier = tokens.get(index - 2);
            if (qualifier.identifier() && isTypeName(qualifier.text(), byName)) {
                return List.of(qualifier.text());
            }
        } else if (hasMember(byName, type.name(), token.text())) {
            return List.of(type.name());
        }
        String imported = file.staticImports().get(token.text());
        if (imported != null) {
            return List.of(imported);
        }
        return byName.getOrDefault(token.text(), List.of()).stream()
                .filter(declaration -> !isType(declaration))
                .map(Declaration::owner)
                .distinct()
                .toList();
    }

    private static boolean hasMember(Map<String, List<Declaration>> byName, String owner, String name) {
        return byName.getOrDefault(name, List.of()).stream()
                .anyMatch(declaration ->
                        !isType(declaration) && declaration.owner().equals(owner));
    }

    private static boolean isTypeName(String name, Map<String, List<Declaration>> byName) {
        return byName.getOrDefault(name, List.of()).stream().anyMatch(DeadCodeGuardTests::isType);
    }

    private static String memberKey(Declaration declaration) {
        return declaration.owner() + "#" + declaration.name();
    }

    private static void edge(Map<String, Set<String>> edges, Declaration from, Declaration to) {
        if (!key(from).equals(key(to))) {
            edges.computeIfAbsent(key(from), unused -> new HashSet<>()).add(key(to));
        }
    }

    private static boolean isType(Declaration declaration) {
        return switch (declaration.kind()) {
            case "class", "interface", "enum", "record" -> true;
            default -> false;
        };
    }

    private static String key(Declaration declaration) {
        return declaration.file() + "|" + declaration.kind() + "|" + declaration.owner() + "#" + declaration.name();
    }

    private static boolean exempt(Finding finding) {
        return EXCEPTIONS.stream().anyMatch(exception -> covered(exception, finding));
    }

    private static boolean covered(DeadCodeExemptions.Exemption exception, Finding finding) {
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

    private static Parsed parse(Path file, String raw) {
        String code = strip(raw);
        List<Token> tokens = tokenize(code);
        boolean[] ignored = new boolean[code.length()];
        Set<String> importedTypes = new HashSet<>();
        Map<String, String> staticImports = new HashMap<>();
        Matcher imports = IMPORT.matcher(code);
        while (imports.find()) {
            String statement = imports.group();
            if (statement.trim().startsWith("import static ")) {
                String name = statement
                        .substring(statement.indexOf("import static ") + "import static ".length())
                        .replace(";", "")
                        .trim();
                int lastDot = name.lastIndexOf('.');
                int typeDot = lastDot < 0 ? -1 : name.lastIndexOf('.', lastDot - 1);
                if (typeDot > 0) {
                    String type = name.substring(typeDot + 1, lastDot);
                    String member = name.substring(lastDot + 1);
                    importedTypes.add(type);
                    if (!"*".equals(member)) {
                        staticImports.put(member, type);
                    }
                }
            } else if (statement.trim().startsWith("import ")) {
                String name = statement
                        .substring(statement.indexOf("import ") + "import ".length())
                        .replace(";", "")
                        .trim();
                if (!name.endsWith(".*")) {
                    importedTypes.add(name.substring(name.lastIndexOf('.') + 1));
                }
            }
            for (int offset = imports.start(); offset < imports.end(); offset++) {
                ignored[offset] = true;
            }
        }
        List<int[]> recordHeaders = recordHeaders(tokens);
        Frame[] frames = frames(tokens);
        List<Declaration> declarations = new ArrayList<>();
        Set<Integer> declarationsAt = new HashSet<>();
        String path = file.toString().replace('\\', '/');

        Matcher types = TYPE.matcher(code);
        while (types.find()) {
            Token token = tokenAt(tokens, types.start(3));
            if (token == null) {
                continue;
            }
            Frame frame = frames[indexOf(tokens, token)];
            String keyword = types.group(2);
            String owner = frame == null ? "<top>" : frame.name();
            boolean annotation = "@".equals(types.group(1)) && "interface".equals(keyword);
            if (annotation) {
                continue;
            }
            declarations.add(declaration(
                    path,
                    code,
                    tokens,
                    indexOf(tokens, token),
                    keyword,
                    owner,
                    types.group(3),
                    entryPointType(code, types.start())));
            declarationsAt.add(token.offset());
        }
        collectMembers(METHOD, code, tokens, frames, recordHeaders, declarations, declarationsAt, path, true);
        collectMembers(FIELD, code, tokens, frames, recordHeaders, declarations, declarationsAt, path, false);
        return new Parsed(tokens, declarations, ignored, importedTypes, staticImports);
    }

    private static void collectMembers(
            Pattern pattern,
            String code,
            List<Token> tokens,
            Frame[] frames,
            List<int[]> recordHeaders,
            List<Declaration> declarations,
            Set<Integer> declarationsAt,
            String path,
            boolean method) {
        Matcher matcher = pattern.matcher(code);
        while (matcher.find()) {
            String type = matcher.group(1);
            String name = matcher.group(2);
            Token token = tokenAt(tokens, matcher.start(2));
            if (token == null || declarationsAt.contains(token.offset()) || inRanges(recordHeaders, token.offset())) {
                continue;
            }
            Frame frame = frames[indexOf(tokens, token)];
            if (frame == null || frame.kind() != Kind.TYPE || frame.annotation()) {
                continue;
            }
            if (frame.isEnum() && (frame.enumConstantsEnd() < 0 || token.offset() < frame.enumConstantsEnd())) {
                continue;
            }
            if (RESERVED.contains(firstWord(type))) {
                continue;
            }
            if (method && name.equals(frame.name())) {
                continue;
            }
            if (!method && "serialVersionUID".equals(name)) {
                continue;
            }
            String text = matcher.group();
            boolean entry = text.contains("@Override")
                    || text.contains("@SubscribeEvent")
                    || text.contains("@JsonProperty")
                    || text.contains("@GameTest")
                    || (method && "main".equals(name) && "void".equals(type.trim()));
            declarations.add(declaration(
                    path,
                    code,
                    tokens,
                    indexOf(tokens, token),
                    method ? "method" : "field",
                    frame.name(),
                    name,
                    entry));
            declarationsAt.add(token.offset());
        }
    }

    private static boolean entryPointType(String code, int offset) {
        int start = Math.max(
                Math.max(
                        code.lastIndexOf(';', offset),
                        Math.max(code.lastIndexOf('{', offset), code.lastIndexOf('}', offset))),
                0);
        String before = code.substring(start, offset);
        return before.contains("@Mod")
                || before.contains("@EventBusSubscriber")
                || before.contains("@JeiPlugin")
                || before.contains("@GameTestHolder");
    }

    private static Declaration declaration(
            String path,
            String code,
            List<Token> tokens,
            int index,
            String kind,
            String owner,
            String name,
            boolean entry) {
        int offset = tokens.get(index).offset();
        return new Declaration(
                path,
                SourceFiles.lineOf(code, offset),
                kind,
                owner,
                name,
                offset,
                declarationEnd(tokens, index),
                entry);
    }

    private static int declarationEnd(List<Token> tokens, int start) {
        int parentheses = 0;
        for (int index = start; index < tokens.size(); index++) {
            String text = tokens.get(index).text();
            if ("(".equals(text)) {
                parentheses++;
            } else if (")".equals(text)) {
                parentheses--;
            } else if ("{".equals(text) && parentheses == 0) {
                int depth = 0;
                for (int inner = index; inner < tokens.size(); inner++) {
                    String nested = tokens.get(inner).text();
                    if ("{".equals(nested)) {
                        depth++;
                    } else if ("}".equals(nested)) {
                        depth--;
                        if (depth == 0) {
                            return tokens.get(inner).offset() + 1;
                        }
                    }
                }
                return tokens.get(tokens.size() - 1).offset() + 1;
            } else if (";".equals(text) && parentheses == 0) {
                return tokens.get(index).offset() + 1;
            }
        }
        return tokens.get(tokens.size() - 1).offset() + 1;
    }

    private static boolean inRanges(List<int[]> ranges, int offset) {
        return ranges.stream().anyMatch(range -> offset >= range[0] && offset < range[1]);
    }

    private static String firstWord(String type) {
        int end = 0;
        while (end < type.length() && Character.isJavaIdentifierPart(type.charAt(end))) {
            end++;
        }
        return type.substring(0, end);
    }

    private static List<int[]> recordHeaders(List<Token> tokens) {
        List<int[]> headers = new ArrayList<>();
        for (int index = 0; index + 2 < tokens.size(); index++) {
            Token keyword = tokens.get(index);
            if (!"record".equals(keyword.text())
                    || !tokens.get(index + 1).identifier()
                    || !"(".equals(tokens.get(index + 2).text())) {
                continue;
            }
            int depth = 0;
            for (int inner = index + 2; inner < tokens.size(); inner++) {
                String text = tokens.get(inner).text();
                if ("(".equals(text)) {
                    depth++;
                } else if (")".equals(text)) {
                    depth--;
                    if (depth == 0) {
                        headers.add(new int[] {
                            tokens.get(index + 2).offset(), tokens.get(inner).offset() + 1
                        });
                        break;
                    }
                }
            }
        }
        return headers;
    }

    private static Frame[] frames(List<Token> tokens) {
        Frame[] frames = new Frame[tokens.size()];
        Deque<Frame> stack = new ArrayDeque<>();
        String pendingName = null;
        boolean pendingEnum = false;
        boolean pendingAnnotation = false;
        for (int index = 0; index < tokens.size(); index++) {
            Token token = tokens.get(index);
            frames[index] = stack.peek();
            if (token.identifier()
                    && isTypeKeyword(token.text())
                    && (index == 0 || !".".equals(tokens.get(index - 1).text()))) {
                if (index + 1 < tokens.size() && tokens.get(index + 1).identifier()) {
                    pendingName = tokens.get(index + 1).text();
                    pendingEnum = "enum".equals(token.text());
                    pendingAnnotation = "interface".equals(token.text())
                            && index > 0
                            && "@".equals(tokens.get(index - 1).text());
                }
            } else if ("{".equals(token.text()) && pendingName != null) {
                stack.push(new Frame(Kind.TYPE, pendingName, pendingEnum, pendingAnnotation));
                pendingName = null;
            } else if ("{".equals(token.text())) {
                stack.push(new Frame(Kind.BLOCK, "", false, false));
            } else if ("}".equals(token.text())) {
                if (!stack.isEmpty()) {
                    stack.pop();
                }
            } else if (";".equals(token.text())
                    && !stack.isEmpty()
                    && stack.peek().kind() == Kind.TYPE
                    && stack.peek().isEnum()
                    && stack.peek().enumConstantsEnd() < 0) {
                stack.push(stack.pop().withEnumConstantsEnd(token.offset()));
            }
        }
        return frames;
    }

    private static boolean isTypeKeyword(String text) {
        return "class".equals(text) || "interface".equals(text) || "enum".equals(text) || "record".equals(text);
    }

    private static String strip(String source) {
        char[] chars = source.toCharArray();
        int index = 0;
        while (index < chars.length) {
            char current = chars[index];
            if (current == '/' && index + 1 < chars.length && chars[index + 1] == '/') {
                index = blank(chars, index, lineEnd(chars, index));
            } else if (current == '/' && index + 1 < chars.length && chars[index + 1] == '*') {
                int end = source.indexOf("*/", index + 2);
                index = blank(chars, index, end < 0 ? chars.length : end + 2);
            } else if (current == '"' && source.startsWith("\"\"\"", index)) {
                int end = source.indexOf("\"\"\"", index + 3);
                index = blank(chars, index, end < 0 ? chars.length : end + 3);
            } else if (current == '"' || current == '\'') {
                int cursor = index + 1;
                while (cursor < chars.length && chars[cursor] != current) {
                    if (chars[cursor] == '\\') {
                        cursor++;
                    }
                    cursor++;
                }
                index = blank(chars, index, Math.min(chars.length, cursor + 1));
            } else {
                index++;
            }
        }
        return new String(chars);
    }

    private static int lineEnd(char[] chars, int start) {
        int end = start;
        while (end < chars.length && chars[end] != '\n') {
            end++;
        }
        return end;
    }

    private static int blank(char[] chars, int start, int end) {
        for (int index = start; index < end; index++) {
            if (chars[index] != '\n') {
                chars[index] = ' ';
            }
        }
        return end;
    }

    private static List<Token> tokenize(String code) {
        List<Token> tokens = new ArrayList<>();
        int index = 0;
        while (index < code.length()) {
            char current = code.charAt(index);
            if (Character.isJavaIdentifierStart(current)) {
                int end = index + 1;
                while (end < code.length() && Character.isJavaIdentifierPart(code.charAt(end))) {
                    end++;
                }
                tokens.add(new Token(code.substring(index, end), index, true));
                index = end;
            } else if (Character.isDigit(current)) {
                int end = index + 1;
                while (end < code.length()
                        && (Character.isLetterOrDigit(code.charAt(end))
                                || code.charAt(end) == '.'
                                || code.charAt(end) == '_')) {
                    end++;
                }
                index = end;
            } else if (Character.isWhitespace(current)) {
                index++;
            } else {
                tokens.add(new Token(String.valueOf(current), index, false));
                index++;
            }
        }
        return tokens;
    }

    private static Token tokenAt(List<Token> tokens, int offset) {
        int low = 0;
        int high = tokens.size() - 1;
        while (low <= high) {
            int middle = (low + high) >>> 1;
            Token token = tokens.get(middle);
            if (token.offset() < offset) {
                low = middle + 1;
            } else if (token.offset() > offset) {
                high = middle - 1;
            } else {
                return token;
            }
        }
        return null;
    }

    private static int indexOf(List<Token> tokens, Token token) {
        int low = 0;
        int high = tokens.size() - 1;
        while (low <= high) {
            int middle = (low + high) >>> 1;
            Token current = tokens.get(middle);
            if (current.offset() < token.offset()) {
                low = middle + 1;
            } else if (current.offset() > token.offset()) {
                high = middle - 1;
            } else {
                return middle;
            }
        }
        return -1;
    }

    private enum Kind {
        TYPE,
        BLOCK
    }

    private record Frame(Kind kind, String name, boolean isEnum, boolean annotation, int enumConstantsEnd) {
        Frame(Kind kind, String name, boolean isEnum, boolean annotation) {
            this(kind, name, isEnum, annotation, -1);
        }

        Frame withEnumConstantsEnd(int offset) {
            return new Frame(kind, name, isEnum, annotation, offset);
        }
    }

    private record Token(String text, int offset, boolean identifier) {}

    private record Declaration(
            String file, int line, String kind, String owner, String name, int offset, int end, boolean entry) {}

    private record Finding(String file, int line, String kind, String owner, String name) {
        String key() {
            return file + "|" + kind + "|" + owner + "#" + name;
        }

        String describe() {
            return file + ":" + line + " " + kind + " " + owner + "#" + name;
        }
    }

    private static final class Parsed {
        private final List<Token> tokens;
        private final List<Declaration> declarations;
        private final boolean[] ignored;
        private final Set<String> importedTypes;
        private final Map<String, String> staticImports;

        private Parsed(
                List<Token> tokens,
                List<Declaration> declarations,
                boolean[] ignored,
                Set<String> importedTypes,
                Map<String, String> staticImports) {
            this.tokens = tokens;
            this.declarations = declarations;
            this.ignored = ignored;
            this.importedTypes = importedTypes;
            this.staticImports = staticImports;
        }

        private List<Token> tokens() {
            return tokens;
        }

        private List<Declaration> declarations() {
            return declarations;
        }

        private boolean[] ignored() {
            return ignored;
        }

        private Set<String> importedTypes() {
            return importedTypes;
        }

        private Map<String, String> staticImports() {
            return staticImports;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Parsed other
                    && Objects.equals(tokens, other.tokens)
                    && Objects.equals(declarations, other.declarations)
                    && Objects.equals(importedTypes, other.importedTypes)
                    && Objects.equals(staticImports, other.staticImports)
                    && Arrays.equals(ignored, other.ignored);
        }

        @Override
        public int hashCode() {
            return 31 * Objects.hash(tokens, declarations, importedTypes, staticImports) + Arrays.hashCode(ignored);
        }

        @Override
        public String toString() {
            return "Parsed[" + "tokens=" + tokens + ", " + "declarations=" + declarations + ", " + "ignored="
                    + Arrays.toString(ignored) + ", importedTypes=" + importedTypes + ", staticImports="
                    + staticImports + "]";
        }
    }
}
