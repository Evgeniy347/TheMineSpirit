package com.theminespirit.testing;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.management.ClassLoadingMXBean;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import net.jqwik.api.Property;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.TestSource;
import org.junit.platform.engine.support.descriptor.ClassSource;
import org.junit.platform.engine.support.descriptor.MethodSource;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;

public final class TestCostListener implements TestExecutionListener {
    private static final String CSV_PROPERTY = "theminespirit.testCost.csv";
    private static final Path DEFAULT_CSV = Path.of("TestResults/unit/test-cost.csv");

    private final ThreadMXBean threadMx = ManagementFactory.getThreadMXBean();
    private final List<GarbageCollectorMXBean> gcMbeans = ManagementFactory.getGarbageCollectorMXBeans();
    private final ClassLoadingMXBean classMx = ManagementFactory.getClassLoadingMXBean();
    private final boolean cpuSupported =
            threadMx.isThreadCpuTimeSupported() && threadMx.isCurrentThreadCpuTimeSupported();

    private final Path csv;
    private final ConcurrentMap<String, Long> startWall = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Long> startCpu = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Long> startGc = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Integer> classesAtTestStart = new ConcurrentHashMap<>();

    public TestCostListener() {
        String configured = System.getProperty(CSV_PROPERTY);
        this.csv = configured == null || configured.isBlank() ? DEFAULT_CSV : Path.of(configured);
    }

    @Override
    public void testPlanExecutionStarted(TestPlan testPlan) {

        try {
            Files.createDirectories(csv.getParent());
            Files.writeString(csv, "class,test,kind,cpu_ms,wall_ms,gc_ms,warmup\n", StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Не удалось подготовить CSV тестовой стоимости " + csv, e);
        }
    }

    @Override
    public void executionStarted(TestIdentifier id) {
        if (!id.isTest()) {
            return;
        }
        String key = id.getUniqueId();
        startWall.put(key, System.nanoTime());
        startGc.put(key, gcTime());
        classesAtTestStart.put(key, classMx.getLoadedClassCount());
        if (cpuSupported) {
            startCpu.put(key, threadMx.getCurrentThreadCpuTime());
        }
    }

    @Override
    public void executionFinished(TestIdentifier id, TestExecutionResult result) {
        if (!id.isTest() || !startWall.containsKey(id.getUniqueId())) {
            return;
        }
        String key = id.getUniqueId();
        long wallNs = System.nanoTime() - startWall.remove(key);
        long cpuNs = 0;
        if (cpuSupported) {
            Long c = startCpu.remove(key);
            cpuNs = c == null ? 0 : Math.max(0, threadMx.getCurrentThreadCpuTime() - c);
        }
        long gcMsDelta = Math.max(0, gcTime() - startGc.remove(key));
        int classesAtStart = classesAtTestStart.remove(key);
        long loaded = Math.max(0L, classMx.getLoadedClassCount() - classesAtStart);
        boolean warmup = loaded >= 200L;

        String className = classOf(id);
        String testName = testName(id);
        String kind = kindOf(className, testName);
        String line = String.format(
                Locale.ROOT,
                "%s,%s,%s,%d,%d,%d,%s\n",
                escape(className),
                escape(testName),
                kind,
                cpuNs / 1_000_000L,
                wallNs / 1_000_000L,
                gcMsDelta,
                warmup ? "true" : "false");
        try {
            Files.writeString(csv, line, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new UncheckedIOException("Не удалось записать строку стоимости теста в " + csv, e);
        }
    }

    private long gcTime() {
        long sum = 0;
        for (GarbageCollectorMXBean b : gcMbeans) {
            long t = b.getCollectionTime();
            if (t > 0) sum += t;
        }
        return sum;
    }

    private String kindOf(String className, String testName) {
        try {
            Class<?> clazz =
                    Class.forName(className, false, Thread.currentThread().getContextClassLoader());
            for (Method method : clazz.getDeclaredMethods()) {
                if (method.getName().equals(testName) && method.isAnnotationPresent(Property.class)) {
                    return "jqwik";
                }
            }
        } catch (Throwable ignored) {
            // класс или метод недоступны — считаем обычным JUnit-тестом
        }
        return "junit";
    }

    private String classOf(TestIdentifier id) {
        TestSource src = id.getSource().orElse(null);
        if (src instanceof ClassSource cs) {
            return cs.getClassName();
        }
        if (src instanceof MethodSource ms) {
            return ms.getClassName();
        }
        return id.getLegacyReportingName();
    }

    private String testName(TestIdentifier id) {
        TestSource src = id.getSource().orElse(null);
        if (src instanceof MethodSource ms) {
            return ms.getMethodName();
        }
        return id.getDisplayName();
    }

    private String escape(String s) {
        if (s == null) return "";
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }
}
