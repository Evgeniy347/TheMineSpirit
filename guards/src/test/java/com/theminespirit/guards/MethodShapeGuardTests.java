package com.theminespirit.guards;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import javax.lang.model.element.Modifier;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;

class MethodShapeGuardTests {
    private static final int MAX_BOOLEAN_PARAMETERS = 3;
    private static final Map<String, String> EXCEPTIONS = Map.ofEntries();

    @Test
    void productionMethods_ThreeBooleanFlags_AreAbsent() {
        List<String> found = new ArrayList<>();
        for (Path root : SourceFiles.mainPackages()) {
            if (Files.isDirectory(root)) {
                found.addAll(violations(root, EXCEPTIONS));
            }
        }

        assertEquals(
                List.of(),
                found,
                "У метода 3+ boolean-параметра: по вызову не видно, что означают флаги. Как надо: флаги одного "
                        + "смысла — enum/тип. Внешний контракт (override Minecraft/NeoForge) — только в EXCEPTIONS "
                        + "с причиной; длинный список параметров (8+) ловит Checkstyle ParameterNumber. "
                        + "Нарушители (файл:строка)");
    }

    @Test
    void methodShapePattern_ThreeBooleanFlags_AreReported() {
        String sample = """
                        class Sample {
                            boolean flags(boolean first, boolean second, boolean third) { return first; }
                            void narrow(boolean first, boolean second, int value) { }
                        }
                        """;

        assertEquals(
                List.of("2 Sample.flags"),
                violationsOf(sample),
                "Сторож ослеп: 3 boolean-параметра обязаны находиться, 2 boolean — нет");
    }

    @Test
    void methodShapePattern_AbsoluteWindowsPath_IsReportedWithoutCrash() {
        String sample = "class Sample { void flags(boolean a, boolean b, boolean c) { } }\n";

        assertEquals(
                List.of("1 Sample.flags"),
                violationsOf(sample, URI.create("file:///F:/repo/mod/src/main/java/Sample.java")),
                "Сторож обязан назвать нарушителя и на абсолютном пути Windows, а не падать на Path.of");
    }

    @Test
    void methodShapePattern_RecordCanonicalConstructor_IsClean() {
        String sample = "record Flags(boolean north, boolean south, boolean east) { }\n";

        assertEquals(
                List.of(),
                violationsOf(sample),
                "Сторож ложно срабатывает: канонический конструктор record — это форма данных, а не метод");
    }

    @Test
    void methodShapePattern_RecordDelegatingConstructor_IsClean() {
        String sample = """
                        record Flags(boolean north, boolean south, boolean east, boolean west) {
                            Flags(boolean north, boolean south, boolean east) {
                                this(north, south, east, false);
                            }
                        }
                        """;

        assertEquals(
                List.of(),
                violationsOf(sample),
                "Сторож ложно срабатывает: вторичный конструктор record только подставляет умолчания "
                        + "в канонический и не задаёт новую форму");
    }

    private static List<String> violations(Path root, Map<String, String> exceptions) {
        List<Path> files;
        try (Stream<Path> walk = Files.walk(root)) {
            files = walk.filter(path -> path.toString().endsWith(".java")).toList();
        } catch (IOException error) {
            throw new UncheckedIOException("Сторож не смог прочитать исходники в " + root, error);
        }
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        try (StandardJavaFileManager manager = compiler.getStandardFileManager(null, null, null)) {
            JavacTask task = (JavacTask) compiler.getTask(
                    null, manager, null, List.of("-proc:none"), null, manager.getJavaFileObjectsFromPaths(files));
            List<Violation> found = inspect(task, exceptions);
            return found.stream()
                    .map(violation -> violation.file() + ":" + violation.line() + " " + violation.owner()
                            + "." + violation.name() + " params=" + violation.params()
                            + " boolean=" + violation.booleans())
                    .toList();
        } catch (IOException error) {
            throw new UncheckedIOException("Сторож не смог разобрать production-исходники", error);
        }
    }

    private static List<String> violationsOf(String source) {
        return violationsOf(source, URI.create("string:///Sample.java"));
    }

    private static List<String> violationsOf(String source, URI uri) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        JavaFileObject file = new SimpleJavaFileObject(uri, JavaFileObject.Kind.SOURCE) {
            @Override
            public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                return source;
            }
        };
        JavacTask task = (JavacTask) compiler.getTask(null, null, null, List.of("-proc:none"), null, List.of(file));
        try {
            return inspect(task, Map.of()).stream()
                    .map(violation -> violation.line() + " " + violation.owner() + "." + violation.name())
                    .toList();
        } catch (IOException error) {
            throw new UncheckedIOException("Сторож не смог разобрать подложный исходник", error);
        }
    }

    private static List<Violation> inspect(JavacTask task, Map<String, String> exceptions) throws IOException {
        SourcePositions positions = Trees.instance(task).getSourcePositions();
        List<Violation> violations = new ArrayList<>();
        List<String> seen = new ArrayList<>();
        for (CompilationUnitTree unit : task.parse()) {
            URI uri = unit.getSourceFile().toUri();
            String file = "file".equals(uri.getScheme()) ? Path.of(uri).toString() : uri.getPath();
            new TreePathScanner<Void, Void>() {
                private final Deque<ClassTree> owners = new ArrayDeque<>();

                @Override
                public Void visitClass(ClassTree node, Void unused) {
                    owners.push(node);
                    try {
                        return super.visitClass(node, null);
                    } finally {
                        owners.pop();
                    }
                }

                @Override
                public Void visitMethod(MethodTree node, Void unused) {
                    ClassTree owner = owners.peek();
                    if (owner == null) {
                        return super.visitMethod(node, null);
                    }
                    int parameters = node.getParameters().size();
                    int booleans = 0;
                    List<String> names = new ArrayList<>();
                    for (VariableTree parameter : node.getParameters()) {
                        names.add(parameter.getName().toString());
                        if ("boolean".equals(parameter.getType().toString())) {
                            booleans++;
                        }
                    }
                    boolean constructor = node.getReturnType() == null;
                    boolean record = owner.getKind() == Tree.Kind.RECORD;
                    boolean canonical = constructor && record && names.equals(componentNames(owner));
                    boolean delegates = constructor && record && delegatesToCanonical(node);
                    boolean tooManyFlags = booleans >= MAX_BOOLEAN_PARAMETERS;
                    if (tooManyFlags && !canonical && !delegates) {
                        String key = key(node, owner, file);
                        seen.add(key);
                        if (!exceptions.containsKey(key)) {
                            long line = unit.getLineMap().getLineNumber(positions.getStartPosition(unit, node));
                            violations.add(new Violation(
                                    file,
                                    line,
                                    owner.getSimpleName().toString(),
                                    node.getName().toString(),
                                    parameters,
                                    booleans));
                        }
                    }
                    return super.visitMethod(node, null);
                }
            }.scan(unit, null);
        }
        for (String exception : exceptions.keySet()) {
            assertTrue(
                    seen.contains(exception),
                    "Устаревшее исключение метода " + exception + ": нарушение "
                            + "исчезло или сигнатура изменилась, причина в EXCEPTIONS больше не нужна");
        }
        return violations;
    }

    private record Violation(String file, long line, String owner, String name, int params, int booleans) {}

    private static String key(MethodTree node, ClassTree owner, String file) {
        return SourceFiles.logical(Path.of(file)) + "#" + owner.getSimpleName() + "." + node.getName() + "/"
                + node.getParameters().size();
    }

    private static List<String> componentNames(ClassTree record) {
        List<String> names = new ArrayList<>();
        for (Tree member : record.getMembers()) {
            if (member instanceof VariableTree field
                    && !field.getModifiers().getFlags().contains(Modifier.STATIC)) {
                names.add(field.getName().toString());
            }
        }
        return names;
    }

    private static boolean delegatesToCanonical(MethodTree constructor) {
        if (constructor.getBody() == null
                || constructor.getBody().getStatements().isEmpty()) {
            return false;
        }
        return constructor.getBody().getStatements().get(0).toString().startsWith("this(");
    }
}
