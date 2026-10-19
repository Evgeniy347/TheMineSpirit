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
    private static final int MAX_PARAMETERS = 8;
    private static final int MAX_BOOLEAN_PARAMETERS = 3;
    private static final Map<String, String> EXCEPTIONS = Map.ofEntries();

    @Test
    void productionMethods_TooManyParametersOrBooleanFlags_AreAbsent() {
        List<String> found = violations(SourceFiles.MAIN, EXCEPTIONS);

        assertEquals(
                List.of(),
                found,
                "У метода 3+ boolean-параметра или 8+ параметров: по вызову не видно, что означают флаги, "
                        + "а длинный список легко перепутать местами. Как надо: флаги одного смысла — "
                        + "enum/тип, длинный список — объект-параметр. Внешний контракт (override "
                        + "Minecraft/NeoForge) — только в EXCEPTIONS с причиной. Нарушители (файл:строка)");
    }

    @Test
    void methodShapePattern_BooleanFlagsAndLongList_AreReported() {
        String sample = """
                        class Sample {
                            boolean flags(boolean first, boolean second, boolean third) { return first; }
                            void wide(int a, int b, int c, int d, int e, int f, int g, int h) { }
                            void narrow(boolean first, boolean second, int value) { }
                        }
                        """;

        assertEquals(
                List.of("2 Sample.flags", "3 Sample.wide"),
                violationsOf(sample),
                "Сторож ослеп: 3 boolean-параметра и 8 параметров обязаны находиться, 2 boolean — нет");
    }

    @Test
    void methodShapePattern_RecordCanonicalConstructor_IsClean() {
        String sample = "record Point(int x, int y, int z, int level, int kind, int meta, int light, int sky) { }\n";

        assertEquals(
                List.of(),
                violationsOf(sample),
                "Сторож ложно срабатывает: канонический конструктор record — это форма данных, а не метод");
    }

    @Test
    void methodShapePattern_RecordDelegatingConstructor_IsClean() {
        String sample = """
                        record Point(int x, int y, int z, int level, int kind, int meta, int light, int sky) {
                            Point(int x, int y, int z) {
                                this(x, y, z, 0, 0, 0, 15, 15);
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
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        JavaFileObject file =
                new SimpleJavaFileObject(URI.create("string:///Sample.java"), JavaFileObject.Kind.SOURCE) {
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
            String file = unit.getSourceFile().toUri().getPath();
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
                    boolean tooWide = parameters >= MAX_PARAMETERS;
                    boolean tooManyFlags = booleans >= MAX_BOOLEAN_PARAMETERS;
                    if ((tooWide || tooManyFlags) && !canonical && !delegates) {
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
        String path = file.replace('\\', '/');
        int root = path.indexOf("src/main/java/");
        String relative = root < 0 ? path : path.substring(root + "src/main/java/".length());
        return relative + "#" + owner.getSimpleName() + "." + node.getName() + "/"
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
