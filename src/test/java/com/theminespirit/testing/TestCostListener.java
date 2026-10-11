package com.theminespirit.testing;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.management.ClassLoadingMXBean;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.TestSource;
import org.junit.platform.engine.support.descriptor.ClassSource;
import org.junit.platform.engine.support.descriptor.MethodSource;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;

public final class TestCostListener implements TestExecutionListener {
    private static final Path CSV = Path.of("TestResults/unit/test-cost.csv");

    private final ThreadMXBean threadMx = ManagementFactory.getThreadMXBean();
    private final List<GarbageCollectorMXBean> gcMbeans = ManagementFactory.getGarbageCollectorMXBeans();
    private final ClassLoadingMXBean classMx = ManagementFactory.getClassLoadingMXBean();
    private final boolean cpuSupported = threadMx.isThreadCpuTimeSupported() && threadMx.isCurrentThreadCpuTimeSupported();

    private final AtomicLong loadedClassesBase = new AtomicLong(classMx.getLoadedClassCount());
    private final ConcurrentHashMap<String, Long> startCpu = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> startWall = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> startGc = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Integer> classesAtStart = new ConcurrentHashMap<>();
    private final List<Run> runs = Collections.synchronizedList(new ArrayList<>());

    @Override
    public void testPlanExecutionStarted(TestPlan testPlan) {
        try {
            Files.createDirectories(CSV.getParent());
            if (!Files.exists(CSV)) {
                Files.writeString(CSV, "class,test,cpu_ms,wall_ms,gc_ms,warmup\n", StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Не удалось подготовить CSV тестовой стоимости", e);
        }
    }

    @Override
    public void executionStarted(TestIdentifier id) {
        if (!id.isTest()) {
            return;
        }
        String key = idKey(id);
        startWall.put(key, System.nanoTime());
        startGc.put(key, gcTime());
        classesAtStart.put(key, classMx.getLoadedClassCount());
        if (cpuSupported) {
            startCpu.put(key, threadMx.getCurrentThreadCpuTime());
        }
    }

    @Override
    public void executionFinished(TestIdentifier id, TestExecutionResult result) {
        if (!id.isTest() || !startWall.containsKey(idKey(id))) {
            return;
        }
        String key = idKey(id);
        long wallNs = System.nanoTime() - startWall.remove(key);
        long cpuNs = 0;
        if (cpuSupported) {
            Long c = startCpu.remove(key);
            cpuNs = c == null ? 0 : Math.max(0, threadMx.getCurrentThreadCpuTime() - c);
        }
        long gcMsDelta = Math.max(0, gcTime() - startGc.remove(key));
        int classesAt = classesAtStart.remove(key);
        long loaded = Math.max(0L, classMx.getLoadedClassCount() - loadedClassesBase.get());
        boolean warmup = loaded >= 200L;

        String className = classOf(id);
        String testName = testName(id);
        String line = String.format(Locale.ROOT, "%s,%s,%d,%d,%d,%s\n",
                escape(className), escape(testName), cpuNs / 1_000_000L, wallNs / 1_000_000L, gcMsDelta, warmup ? "true" : "false");
        try {
            Files.writeString(CSV, line, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new UncheckedIOException("Не удалось записать строку стоимости теста", e);
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

    private String idKey(TestIdentifier id) {
        return id.getUniqueId();
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

    private static class Run {
        final String clazz;
        final String test;
        final long cpuMs;
        final boolean warmup;

        Run(String clazz, String test, long cpuMs, boolean warmup) {
            this.clazz = clazz;
            this.test = test;
            this.cpuMs = cpuMs;
            this.warmup = warmup;
        }
    }
}
