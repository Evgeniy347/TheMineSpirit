package com.theminespirit.gametest;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.gametest.framework.GameTestRegistry;
import net.minecraft.gametest.framework.TestFunction;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@EventBusSubscriber(modid = "theminespirit", value = Dist.DEDICATED_SERVER)
public class GameTestFilter {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final String FILTER_PROPERTY = "theminespirit.gametest.filter";

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        String filter = System.getProperty(FILTER_PROPERTY, "");
        if (filter.isBlank()) {
            return;
        }

        String[] parts = filter.split(",");
        List<String> filters = new ArrayList<>();
        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                filters.add(trimmed);
            }
        }

        if (filters.isEmpty()) {
            return;
        }

        try {
            applyFilter(filters);
        } catch (Exception e) {
            LOGGER.error("Failed to apply GameTest filter", e);
        }
    }

    private static void applyFilter(List<String> filters) throws Exception {
        Field functionsField = GameTestRegistry.class.getDeclaredField("TEST_FUNCTIONS");
        functionsField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Collection<TestFunction> functions = (Collection<TestFunction>) functionsField.get(null);

        Field classNamesField = GameTestRegistry.class.getDeclaredField("TEST_CLASS_NAMES");
        classNamesField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Set<String> classNames = (Set<String>) classNamesField.get(null);

        Set<TestFunction> toRemove = new HashSet<>();

        for (TestFunction tf : functions) {
            boolean keep = false;
            for (String filter : filters) {
                if (matches(tf, filter)) {
                    keep = true;
                    break;
                }
            }
            if (!keep) {
                toRemove.add(tf);
            }
        }

        for (TestFunction tf : toRemove) {
            functions.remove(tf);
        }

        for (String className : classNames) {
            boolean hasTests = functions.stream().anyMatch(tf -> tf.testName().startsWith(className + "."));
            if (!hasTests) {
                classNames.remove(className);
            }
        }

        LOGGER.info("GameTest filter applied: {} tests removed, {} remaining", toRemove.size(), functions.size());
    }

    private static boolean matches(TestFunction tf, String filter) {
        if (filter.equals(tf.batchName())) {
            return true;
        }

        if (filter.equals(tf.testName())) {
            return true;
        }

        if (tf.testName().startsWith(filter + ".")) {
            return true;
        }

        return false;
    }
}
