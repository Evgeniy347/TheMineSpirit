package livedebug;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import jdk.jshell.SourceCodeAnalysis.Completeness;
import jdk.jshell.Diag;
import jdk.jshell.spi.ExecutionControl;
import jdk.jshell.JShell;
import jdk.jshell.Snippet;
import jdk.jshell.SnippetEvent;
import jdk.jshell.SourceCodeAnalysis;
import jdk.jshell.execution.DirectExecutionControl;
import jdk.jshell.execution.LoaderDelegate;
import jdk.jshell.spi.ExecutionControlProvider;
import jdk.jshell.spi.ExecutionEnv;

public final class LiveShell {
    private static final String PRELUDE = String.join("\n",
            "import net.minecraft.server.MinecraftServer;",
            "import net.neoforged.neoforge.server.ServerLifecycleHooks;",
            "MinecraftServer server = ServerLifecycleHooks.getCurrentServer();",
            "Object onServer(java.util.concurrent.Callable<Object> task) throws Exception {",
            "    if (\"Server thread\".equals(Thread.currentThread().getName())) {",
            "        return task.call();",
            "    }",
            "    java.util.concurrent.CompletableFuture<Object> future = new java.util.concurrent.CompletableFuture<>();",
            "    server.execute(() -> {",
            "        try {",
            "            future.complete(task.call());",
            "        } catch (Throwable failure) {",
            "            future.completeExceptionally(failure);",
            "        }",
            "    });",
            "    return future.get();",
            "}");

    private static final ByteArrayOutputStream OUT = new ByteArrayOutputStream();
    private static final ByteArrayOutputStream ERR = new ByteArrayOutputStream();
    private static JShell shell;
    private static String classpath = "";
    private static String preludeStatus = "не запускался";

    private LiveShell() {}

    public static synchronized String init(String cp) {
        classpath = cp == null ? "" : cp.trim();
        if (shell != null) {
            shell.close();
            shell = null;
        }
        OUT.reset();
        ERR.reset();
        JShell.Builder builder = JShell.builder()
                .out(new PrintStream(OUT, true))
                .err(new PrintStream(ERR, true))
                .executionEngine(new Provider(), Map.of());
        if (!classpath.isEmpty()) {
            builder.compilerOptions("--class-path", classpath);
        }
        shell = builder.build();
        List<String> problems = new ArrayList<>();
        run(PRELUDE, new StringBuilder(), problems);
        preludeStatus = problems.isEmpty() ? "ok" : "ошибка: " + String.join("", problems);
        return state();
    }

    public static synchronized String eval(String source) {
        if (shell == null) {
            return "ошибка: JShell не запущен (вызовите init)";
        }
        OUT.reset();
        ERR.reset();
        StringBuilder answer = new StringBuilder();
        run(source == null ? "" : source, answer, new ArrayList<>());
        if (answer.length() == 0) {
            answer.append("(нет значения)\n");
        }
        String out = OUT.toString();
        String err = ERR.toString();
        if (!out.isEmpty()) {
            answer.append("out: ").append(out).append(out.endsWith("\n") ? "" : "\n");
        }
        if (!err.isEmpty()) {
            answer.append("err: ").append(err).append(err.endsWith("\n") ? "" : "\n");
        }
        return answer.toString();
    }

    public static synchronized boolean ready() {
        return shell != null;
    }

    public static synchronized String state() {
        return "shell=" + (shell == null ? "нет" : "готов")
                + ", classpath=" + (classpath.isEmpty() ? "пуст" : classpath.length() + " символов")
                + ", prelude=" + preludeStatus;
    }

    public static synchronized void close() {
        if (shell != null) {
            shell.close();
            shell = null;
        }
    }

    private static void run(String source, StringBuilder answer, List<String> errors) {
        SourceCodeAnalysis analysis = shell.sourceCodeAnalysis();
        String rest = source;
        for (int guard = 0; guard < 1000 && !rest.isBlank(); guard++) {
            SourceCodeAnalysis.CompletionInfo info = analysis.analyzeCompletion(rest);
            Completeness completeness = info.completeness();
            if (completeness == Completeness.EMPTY) {
                break;
            }
            if (completeness != Completeness.COMPLETE && completeness != Completeness.COMPLETE_WITH_SEMI) {
                evalOne(rest, answer, errors);
                break;
            }
            evalOne(info.source(), answer, errors);
            rest = info.remaining();
        }
    }

    private static void evalOne(String source, StringBuilder answer, List<String> errors) {
        List<SnippetEvent> events = shell.eval(source);
        for (SnippetEvent event : events) {
            Snippet snippet = event.snippet();
            if (event.status() == Snippet.Status.REJECTED) {
                String problem = diagnostics(snippet);
                errors.add(problem);
                answer.append(problem);
                continue;
            }
            if (event.value() != null) {
                answer.append("=> ").append(event.value()).append('\n');
            }
            if (event.exception() != null) {
                answer.append("!! ").append(event.exception()).append('\n');
            }
        }
    }

    private static String diagnostics(Snippet snippet) {
        StringBuilder text = new StringBuilder();
        for (Diag diag : shell.diagnostics(snippet).toList()) {
            text.append("ошибка: позиция ").append(diag.getPosition()).append(": ")
                    .append(diag.getMessage(Locale.ROOT)).append('\n');
        }
        return text.length() == 0 ? "ошибка: сниппет отвергнут без диагностики\n" : text.toString();
    }

    static final class SnippetLoader extends ClassLoader {
        SnippetLoader(ClassLoader parent) {
            super(parent);
        }

        Class<?> define(String name, byte[] bytes) {
            return defineClass(name, bytes, 0, bytes.length);
        }
    }

    static final class Delegate implements LoaderDelegate {
        private final SnippetLoader loader = new SnippetLoader(LiveShell.class.getClassLoader());
        private final Map<String, Class<?>> defined = new HashMap<>();

        @Override
        public void load(ExecutionControl.ClassBytecodes[] bytecodes) throws ExecutionControl.ClassInstallException {
            boolean[] loaded = new boolean[bytecodes.length];
            try {
                for (int i = 0; i < bytecodes.length; i++) {
                    Class<?> type = loader.define(bytecodes[i].name(), bytecodes[i].bytecodes());
                    defined.put(bytecodes[i].name(), type);
                    loaded[i] = true;
                }
            } catch (Throwable failure) {
                throw new ExecutionControl.ClassInstallException("load: " + failure, loaded);
            }
        }

        @Override
        public void classesRedefined(ExecutionControl.ClassBytecodes[] bytecodes) {
            for (ExecutionControl.ClassBytecodes item : bytecodes) {
                defined.put(item.name(), loader.define(item.name(), item.bytecodes()));
            }
        }

        @Override
        public void addToClasspath(String path) {}

        @Override
        public Class<?> findClass(String name) throws ClassNotFoundException {
            Class<?> type = defined.get(name);
            return type != null ? type : loader.loadClass(name);
        }
    }

    static final class Control extends DirectExecutionControl {
        private final SnippetLoader loader;

        Control(Delegate delegate) {
            super(delegate);
            loader = delegate.loader;
        }

        @Override
        protected String invoke(Method method) throws Exception {
            Thread thread = Thread.currentThread();
            ClassLoader previous = thread.getContextClassLoader();
            thread.setContextClassLoader(loader);
            try {
                method.setAccessible(true);
                Object result = method.invoke(null);
                return valueString(result);
            } catch (InvocationTargetException failure) {
                throw failure;
            } finally {
                thread.setContextClassLoader(previous);
            }
        }
    }

    static final class Provider implements ExecutionControlProvider {
        @Override
        public String name() {
            return "livedebug";
        }

        @Override
        public Map<String, String> defaultParameters() {
            return Map.of();
        }

        @Override
        public ExecutionControl generate(ExecutionEnv env, Map<String, String> parameters) {
            return new Control(new Delegate());
        }
    }
}
