import com.sun.jdi.AbsentInformationException;
import com.sun.jdi.ArrayReference;
import com.sun.jdi.ArrayType;
import com.sun.jdi.BooleanValue;
import com.sun.jdi.Bootstrap;
import com.sun.jdi.ClassLoaderReference;
import com.sun.jdi.ClassObjectReference;
import com.sun.jdi.ClassType;
import com.sun.jdi.IncompatibleThreadStateException;
import com.sun.jdi.LocalVariable;
import com.sun.jdi.Location;
import com.sun.jdi.Method;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.StackFrame;
import com.sun.jdi.StringReference;
import com.sun.jdi.ThreadReference;
import com.sun.jdi.Value;
import com.sun.jdi.VirtualMachine;
import com.sun.jdi.connect.AttachingConnector;
import com.sun.jdi.connect.Connector;
import com.sun.jdi.event.BreakpointEvent;
import com.sun.jdi.event.Event;
import com.sun.jdi.event.EventSet;
import com.sun.jdi.request.BreakpointRequest;
import com.sun.jdi.request.EventRequest;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

public final class LiveDebug {
    private static final String HELPER_CLASS = "livedebug.LiveShell";
    private static final String MOD_ROOT = "com.theminespirit.TheMineSpirit";
    private static final String REST_ROUTER = "com.theminespirit.neoforge.rest.RestServer";
    private static final String CLASS_FOR_NAME = "(Ljava/lang/String;ZLjava/lang/ClassLoader;)Ljava/lang/Class;";

    private static VirtualMachine vm;
    private static ObjectReference modLoader;
    private static ThreadReference eventThread;
    private static EventSet eventSet;
    private static BreakpointRequest restBreakpoint;

    private LiveDebug() {}

    public static void main(String[] args) {
        if (args.length == 0) {
            System.err.println("нужна команда: state|classes|init|eval|hotswap|break");
            System.exit(2);
        }
        String command = args[0];
        String[] rest = Arrays.copyOfRange(args, 1, args.length);
        Runtime.getRuntime().addShutdownHook(new Thread(LiveDebug::resumeOnExit));
        int exit = 0;
        try {
            connect();
            switch (command) {
                case "state" -> printState();
                case "classes" -> listClasses(rest);
                case "init" -> {
                    ensureHelper();
                    System.out.println(say(callHelper("init", evalClasspath())));
                }
                case "eval" -> {
                    ensureHelper();
                    System.out.println(say(callHelper("eval", readScript(rest))));
                }
                case "hotswap" -> hotswap(rest);
                case "break" -> breakpoint(rest);
                default -> {
                    System.err.println("неизвестная команда: " + command);
                    exit = 2;
                }
            }
        } catch (ReflectiveFailure e) {
            System.err.println(e.getMessage());
            exit = 1;
        } catch (Exception e) {
            System.err.println("ошибка живой отладки: " + e);
            exit = 1;
        } finally {
            resumeRest();
            closeSession();
        }
        System.exit(exit);
    }

    private static void connect() throws Exception {
        String host = env("LIVE_DEBUG_HOST", "192.168.0.147");
        String port = env("LIVE_DEBUG_PORT", "5005");
        AttachingConnector connector = null;
        for (AttachingConnector candidate : Bootstrap.virtualMachineManager().attachingConnectors()) {
            if ("com.sun.jdi.SocketAttach".equals(candidate.name())) {
                connector = candidate;
            }
        }
        if (connector == null) {
            throw new IllegalStateException("JDI не нашёл SocketAttach: запустите java с --add-modules jdk.jdi");
        }
        Map<String, Connector.Argument> arguments = connector.defaultArguments();
        arguments.get("hostname").setValue(host);
        arguments.get("port").setValue(port);
        arguments.get("timeout").setValue("10000");
        vm = connector.attach(arguments);
        ReferenceType root = firstClass(MOD_ROOT, null);
        if (root == null) {
            throw new IllegalStateException("класс " + MOD_ROOT + " не загружен на сервере");
        }
        modLoader = root.classLoader();
    }

    private static void printState() {
        ReferenceType helper = firstClass(HELPER_CLASS, null);
        System.out.println("сервер: " + vm.name() + " " + vm.version());
        System.out.println("загрузчик модов: " + modLoader.referenceType().name() + " (id " + modLoader.uniqueID() + ")");
        System.out.println(helper == null
                ? "мост eval: не внедрён (первый eval внедрит его)"
                : "мост eval: внедрён (state — внутри eval)");
    }

    private static void listClasses(String[] args) {
        String filter = args.length == 0 ? "theminespirit" : args[0];
        Set<String> names = new TreeSet<>();
        for (ReferenceType type : vm.allClasses()) {
            if (type.name().contains(filter)) {
                names.add(type.name() + " [" + loaderName(type) + "]");
            }
        }
        names.stream().limit(300).forEach(System.out::println);
        System.out.println("всего: " + names.size());
    }

    private static void hotswap(String[] args) throws Exception {
        List<String> names = new ArrayList<>();
        for (String argument : args) {
            if (!argument.startsWith("--")) {
                names.add(argument);
            }
        }
        if (names.isEmpty()) {
            throw new ReflectiveFailure("укажите класс: hotswap com.theminespirit.neoforge.Example");
        }
        List<Path> roots = new ArrayList<>();
        for (String part : env("LIVE_DEBUG_CLASSES", "mod/build/classes/java/main").split(";")) {
            if (!part.isBlank()) {
                roots.add(Path.of(part.trim()));
            }
        }
        Map<ReferenceType, byte[]> changes = new LinkedHashMap<>();
        Map<String, byte[]> fresh = new LinkedHashMap<>();
        for (String name : names) {
            String relative = name.replace('.', '/') + ".class";
            Path base = null;
            Path root = null;
            for (Path candidate : roots) {
                Path file = candidate.resolve(relative);
                if (Files.isRegularFile(file)) {
                    base = file;
                    root = candidate;
                    break;
                }
            }
            if (base == null) {
                throw new ReflectiveFailure("нет собранного класса " + relative + " (сначала ./gradlew compileJava)");
            }
            Path parent = base.getParent();
            String file = base.getFileName().toString();
            String prefix = file.substring(0, file.length() - ".class".length());
            List<Path> files = new ArrayList<>();
            files.add(base);
            try (Stream<Path> siblings = Files.list(parent)) {
                siblings.filter(path -> {
                    String sibling = path.getFileName().toString();
                    return sibling.startsWith(prefix + "$") && sibling.endsWith(".class");
                }).sorted().forEach(files::add);
            }
            for (Path path : files) {
                String target = nameOf(root, path);
                ReferenceType match = null;
                for (ReferenceType type : vm.classesByName(target)) {
                    if (modLoader.equals(type.classLoader())) {
                        match = type;
                    }
                }
                byte[] bytes = Files.readAllBytes(path);
                if (match == null) {
                    fresh.put(target, bytes);
                } else {
                    changes.put(match, bytes);
                }
            }
        }
        if (changes.isEmpty() && fresh.isEmpty()) {
            throw new ReflectiveFailure("нечего менять: классы не найдены");
        }
        if (!fresh.isEmpty()) {
            withRestThread(() -> {
                ArrayType byteArray = arrayOfBytes();
                Method defineClass = findMethod(modLoader.referenceType(), "defineClass", "(Ljava/lang/String;[BII)Ljava/lang/Class;");
                if (defineClass == null) {
                    throw new ReflectiveFailure("у загрузчика модов нет defineClass");
                }
                for (Map.Entry<String, byte[]> entry : fresh.entrySet()) {
                    byte[] bytes = entry.getValue();
                    modLoader.invokeMethod(eventThread, defineClass,
                            List.of(vm.mirrorOf(entry.getKey()), array(bytes, byteArray), vm.mirrorOf(0), vm.mirrorOf(bytes.length)), 0);
                    callValues(vm.classesByName("java.lang.Class").get(0), "forName", CLASS_FOR_NAME,
                            Arrays.asList(vm.mirrorOf(entry.getKey()), vm.mirrorOf(true), modLoader));
                }
            });
        }
        if (!changes.isEmpty()) {
            vm.redefineClasses(changes);
        }
        for (Map.Entry<ReferenceType, byte[]> entry : changes.entrySet()) {
            System.out.println("переопределён " + entry.getKey().name() + " (" + entry.getValue().length + " байт)");
        }
        for (Map.Entry<String, byte[]> entry : fresh.entrySet()) {
            System.out.println("определён " + entry.getKey() + " (" + entry.getValue().length + " байт)");
        }
    }

    private static void breakpoint(String[] args) throws Exception {
        String spec = null;
        boolean interactive = false;
        String threadFilter = null;
        for (int i = 0; i < args.length; i++) {
            if ("-i".equals(args[i])) {
                interactive = true;
            } else if ("--thread".equals(args[i]) && i + 1 < args.length) {
                threadFilter = args[++i];
            } else if (!args[i].startsWith("--")) {
                spec = args[i];
            }
        }
        if (spec == null) {
            throw new ReflectiveFailure("укажите точку: break com.theminespirit.neoforge.Example:42");
        }
        int colon = spec.lastIndexOf(':');
        if (colon < 1) {
            throw new ReflectiveFailure("точка задаётся как класс:строка");
        }
        String className = spec.substring(0, colon);
        int line;
        try {
            line = Integer.parseInt(spec.substring(colon + 1));
        } catch (NumberFormatException e) {
            throw new ReflectiveFailure("номер строки не число: " + spec);
        }
        ReferenceType type = firstClass(className, modLoader);
        if (type == null) {
            type = firstClass(className, null);
        }
        if (type == null) {
            throw new ReflectiveFailure("класс не загружен: " + className);
        }
        List<Location> locations = type.locationsOfLine(line);
        if (locations.isEmpty()) {
            throw new ReflectiveFailure("в " + className + " нет исполняемой строки " + line
                    + "; начните с метода: " + firstLines(type));
        }
        BreakpointRequest request = vm.eventRequestManager().createBreakpointRequest(locations.get(0));
        request.setSuspendPolicy(EventRequest.SUSPEND_EVENT_THREAD);
        if (threadFilter != null) {
            ThreadReference thread = thread(threadFilter);
            if (thread == null) {
                throw new ReflectiveFailure("нет потока с именем " + threadFilter);
            }
            request.addThreadFilter(thread);
        }
        request.enable();
        int timeout = Integer.parseInt(env("LIVE_DEBUG_TIMEOUT", "120"));
        System.out.println("брейкпоинт " + locations.get(0) + ", ждём до " + timeout + " с");
        long deadline = System.currentTimeMillis() + timeout * 1000L;
        try {
            while (System.currentTimeMillis() < deadline) {
                EventSet events = vm.eventQueue().remove(1000);
                if (events == null) {
                    continue;
                }
                BreakpointEvent hit = null;
                for (Event event : events) {
                    if (event instanceof BreakpointEvent breakpoint) {
                        hit = breakpoint;
                    }
                }
                if (hit == null) {
                    events.resume();
                    continue;
                }
                eventSet = events;
                dump(hit);
                if (interactive) {
                    repl(hit, events);
                    return;
                }
                events.resume();
                eventSet = null;
                System.out.println("продолжено; сервер тикает дальше");
                return;
            }
            throw new ReflectiveFailure("брейкпоинт не сработал за " + timeout + " с");
        } finally {
            request.disable();
            vm.eventRequestManager().deleteEventRequest(request);
        }
    }

    private static void repl(BreakpointEvent hit, EventSet events) throws Exception {
        System.out.println("команды: stack | vars [кадр] | up | down | cont | help");
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        int frame = 0;
        while (true) {
            System.out.print("dbg> ");
            System.out.flush();
            String line = in.readLine();
            if (line == null || "cont".equals(line.trim()) || "c".equals(line.trim())) {
                events.resume();
                eventSet = null;
                System.out.println("продолжено");
                return;
            }
            String command = line.trim();
            if (command.startsWith("stack")) {
                stack(hit.thread(), 12);
            } else if (command.startsWith("vars")) {
                String[] parts = command.split("\\s+");
                if (parts.length > 1) {
                    frame = Integer.parseInt(parts[1]);
                }
                locals(hit.thread(), frame);
            } else if ("up".equals(command)) {
                frame = Math.max(frame - 1, 0);
                locals(hit.thread(), frame);
            } else if ("down".equals(command)) {
                frame++;
                locals(hit.thread(), frame);
            } else if (!command.isEmpty() && !"help".equals(command)) {
                System.out.println("неизвестно: " + command);
            } else {
                System.out.println("stack | vars [кадр] | up | down | cont | help");
            }
        }
    }

    private static void dump(BreakpointEvent hit) {
        ThreadReference thread = hit.thread();
        System.out.println("останов: поток " + thread.name() + ", " + hit.location());
        stack(thread, 10);
        locals(thread, 0);
    }

    private static void stack(ThreadReference thread, int limit) {
        try {
            int count = Math.min(thread.frameCount(), limit);
            for (int i = 0; i < count; i++) {
                StackFrame frame = thread.frame(i);
                System.out.println("  #" + i + " " + frame.location());
            }
            if (thread.frameCount() > limit) {
                System.out.println("  ... всего кадров: " + thread.frameCount());
            }
        } catch (IncompatibleThreadStateException e) {
            System.out.println("  стек недоступен: " + e);
        }
    }

    private static void locals(ThreadReference thread, int index) {
        try {
            if (index < 0 || index >= thread.frameCount()) {
                System.out.println("нет кадра " + index);
                return;
            }
            StackFrame frame = thread.frame(index);
            System.out.println("кадр #" + index + " " + frame.location());
            ObjectReference self = frame.thisObject();
            if (self != null) {
                System.out.println("  this = " + self);
            }
            List<LocalVariable> variables = frame.visibleVariables();
            Map<LocalVariable, Value> values = frame.getValues(variables);
            for (Map.Entry<LocalVariable, Value> entry : values.entrySet()) {
                System.out.println("  " + entry.getKey().name() + " = " + text(entry.getValue()));
            }
        } catch (AbsentInformationException e) {
            System.out.println("  имена переменных отсутствуют (нет отладочной информации)");
        } catch (IncompatibleThreadStateException e) {
            System.out.println("  переменные недоступны: " + e);
        }
    }

    private static String text(Value value) {
        if (value instanceof StringReference string) {
            return "\"" + string.value() + "\"";
        }
        return String.valueOf(value);
    }

    private static void ensureHelper() throws Exception {
        String cp = evalClasspath();
        Path dir = Path.of(env("LIVE_DEBUG_HELPER", "build/live-debug/classes/livedebug"));
        withRestThread(() -> {
            if (firstClass(HELPER_CLASS, null) == null) {
                inject(dir);
            }
            ClassType type = (ClassType) firstClass(HELPER_CLASS, null);
            if (type == null) {
                throw new ReflectiveFailure("мост " + HELPER_CLASS + " не внедрился");
            }
            Value ready = callHelper("ready");
            if (!(ready instanceof BooleanValue flag) || !flag.booleanValue()) {
                callHelper("init", cp);
            }
        });
    }

    private static void inject(Path dir) {
        try {
            ArrayType byteArray = arrayOfBytes();
            Method defineClass = findMethod(modLoader.referenceType(), "defineClass", "(Ljava/lang/String;[BII)Ljava/lang/Class;");
            if (defineClass == null) {
                throw new ReflectiveFailure("у загрузчика модов нет defineClass");
            }
            List<Path> files;
            try (Stream<Path> walk = Files.walk(dir)) {
                files = walk.filter(path -> path.toString().endsWith(".class")).sorted().toList();
            }
            for (Path file : files) {
                String relative = dir.relativize(file).toString().replace((char) 92, (char) 47);
                String simple = relative.endsWith(".class") ? relative.substring(0, relative.length() - 6) : relative;
                String name = "livedebug." + simple.replace((char) 47, (char) 46);
                byte[] bytes = Files.readAllBytes(file);
                modLoader.invokeMethod(eventThread, defineClass,
                        List.of(vm.mirrorOf(name), array(bytes, byteArray), vm.mirrorOf(0), vm.mirrorOf(bytes.length)), 0);
            }
            callValues(vm.classesByName("java.lang.Class").get(0), "forName", CLASS_FOR_NAME,
                    Arrays.asList(vm.mirrorOf(HELPER_CLASS), vm.mirrorOf(true), modLoader));
        } catch (ReflectiveFailure e) {
            throw e;
        } catch (Exception e) {
            throw new ReflectiveFailure("внедрение моста не удалось: " + e);
        }
    }

    private static ArrayType arrayOfBytes() throws Exception {
        Value value = callValues(vm.classesByName("java.lang.Class").get(0), "forName", CLASS_FOR_NAME,
                Arrays.asList(vm.mirrorOf("[B"), vm.mirrorOf(false), null));
        return (ArrayType) ((ClassObjectReference) value).reflectedType();
    }

    private static ArrayReference array(byte[] bytes, ArrayType type) throws Exception {
        ArrayReference array = type.newInstance(bytes.length);
        List<Value> values = new ArrayList<>(bytes.length);
        for (byte item : bytes) {
            values.add(vm.mirrorOf(item));
        }
        array.setValues(values);
        return array;
    }

    private static synchronized void withRestThread(ThrowingRunnable action) throws Exception {
        ClassType router = (ClassType) firstClass(REST_ROUTER, null);
        if (router == null) {
            throw new ReflectiveFailure("REST-мост не загружен: eval требует включённый REST");
        }
        List<Location> locations = router.methodsByName("handle").isEmpty()
                ? List.of()
                : router.methodsByName("handle").get(0).allLineLocations();
        if (locations.isEmpty()) {
            throw new ReflectiveFailure("не нашёл точку входа REST-моста для внедрения");
        }
        restBreakpoint = vm.eventRequestManager().createBreakpointRequest(locations.get(0));
        restBreakpoint.setSuspendPolicy(EventRequest.SUSPEND_EVENT_THREAD);
        restBreakpoint.enable();
        triggerRest();
        long deadline = System.currentTimeMillis() + 15000;
        while (eventThread == null && System.currentTimeMillis() < deadline) {
            EventSet events = vm.eventQueue().remove(1000);
            if (events == null) {
                continue;
            }
            for (Event event : events) {
                if (event instanceof BreakpointEvent hit) {
                    eventThread = hit.thread();
                    eventSet = events;
                }
            }
            if (eventThread == null) {
                events.resume();
            }
        }
        if (eventThread == null) {
            throw new ReflectiveFailure("REST-запрос не дошёл до брейкпоинта: включён ли /v1/bots?");
        }
        action.run();
    }

    private static void triggerRest() {
        String base = env("LIVE_DEBUG_REST", "http://192.168.0.147:8765");
        CompletableFuture.runAsync(() -> {
            try {
                HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
                client.send(HttpRequest.newBuilder(URI.create(base + "/v1/bots"))
                        .timeout(Duration.ofSeconds(60)).GET().build(), HttpResponse.BodyHandlers.discarding());
            } catch (IOException e) {
                return;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }

    private static void resumeOnExit() {
        EventSet pending = eventSet;
        if (pending != null) {
            try {
                pending.resume();
            } catch (RuntimeException ignored) {
                return;
            }
        }
    }

    private static void resumeRest() {
        if (restBreakpoint != null) {
            restBreakpoint.disable();
            vm.eventRequestManager().deleteEventRequest(restBreakpoint);
            restBreakpoint = null;
        }
        if (eventSet != null) {
            eventSet.resume();
            eventSet = null;
        }
        eventThread = null;
    }

    private static void closeSession() {
        if (vm != null) {
            vm.dispose();
            vm = null;
        }
    }

    private static Value callHelper(String name, String... args) throws Exception {
        ClassType type = (ClassType) firstClass(HELPER_CLASS, null);
        if (type == null) {
            throw new ReflectiveFailure("мост " + HELPER_CLASS + " не внедрён");
        }
        List<Value> values = new ArrayList<>();
        for (String argument : args) {
            values.add(vm.mirrorOf(argument));
        }
        Method method = null;
        for (Method candidate : type.methodsByName(name)) {
            if (candidate.argumentTypeNames().size() == args.length) {
                method = candidate;
            }
        }
        if (method == null) {
            throw new NoSuchMethodException(HELPER_CLASS + "." + name);
        }
        return type.invokeMethod(eventThread, method, values, 0);
    }

    private static Value callValues(ReferenceType type, String name, String signature, List<? extends Value> arguments)
            throws Exception {
        Method method = findMethod(type, name, signature);
        if (method == null) {
            throw new NoSuchMethodException(type.name() + "." + name + signature);
        }
        return ((ClassType) type).invokeMethod(eventThread, method, arguments, 0);
    }

    private static Method findMethod(ReferenceType type, String name, String signature) {
        for (Method method : type.methodsByName(name)) {
            if (method.signature().equals(signature)) {
                return method;
            }
        }
        return null;
    }

    private static ReferenceType firstClass(String name, ObjectReference loader) {
        for (ReferenceType type : vm.classesByName(name)) {
            if (loader == null || loader.equals(type.classLoader())) {
                return type;
            }
        }
        return null;
    }

    private static ThreadReference thread(String namePart) {
        for (ThreadReference thread : vm.allThreads()) {
            if (thread.name().contains(namePart) && thread.status() != ThreadReference.THREAD_STATUS_ZOMBIE) {
                return thread;
            }
        }
        return null;
    }

    private static String loaderName(ReferenceType type) {
        ClassLoaderReference loader = type.classLoader();
        return loader == null ? "bootstrap" : loader.referenceType().name() + "#" + loader.uniqueID();
    }

    private static String say(Value value) {
        return value instanceof StringReference string ? string.value() : String.valueOf(value);
    }

    private static String nameOf(Path root, Path file) {
        String relative = root.relativize(file).toString().replace((char) 92, (char) 47);
        return relative.substring(0, relative.length() - 6).replace((char) 47, (char) 46);
    }

    private static String firstLines(ReferenceType type) {
        List<String> lines = new ArrayList<>();
        for (Method method : type.methods()) {
            try {
                List<Location> locations = method.allLineLocations();
                if (!locations.isEmpty()) {
                    lines.add(method.name() + ":" + locations.get(0).lineNumber());
                }
            } catch (AbsentInformationException ignored) {
            }
        }
        return String.join(", ", lines.stream().limit(12).toList());
    }

    private static String readScript(String[] args) throws IOException {
        if (args.length == 0) {
            throw new ReflectiveFailure("нужен фрагмент: eval '1+1' | eval @файл | eval - (stdin)");
        }
        if ("-".equals(args[0])) {
            return new String(System.in.readAllBytes(), StandardCharsets.UTF_8);
        }
        if (args[0].startsWith("@")) {
            return Files.readString(Path.of(args[0].substring(1)), StandardCharsets.UTF_8);
        }
        return String.join(" ", args);
    }

    private static String evalClasspath() throws IOException {
        String file = env("LIVE_DEBUG_JARS_FILE", "");
        if (!file.isBlank()) {
            Path path = Path.of(file);
            if (Files.isRegularFile(path)) {
                return Files.readString(path, StandardCharsets.UTF_8).trim();
            }
        }
        return env("LIVE_DEBUG_JARS", "");
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    private static final class ReflectiveFailure extends RuntimeException {
        private static final long serialVersionUID = 1L;

        ReflectiveFailure(String message) {
            super(message);
        }
    }
}
