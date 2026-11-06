package com.theminespirit.guards;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class MechanicsModuleImports {
    private static final Pattern IMPORT =
            Pattern.compile("^\\s*import\\s+(?:static\\s+)?([\\w.*]+)\\s*;", Pattern.MULTILINE);
    private static final Pattern PACKAGE = Pattern.compile("^\\s*package\\s+([\\w.]+)\\s*;", Pattern.MULTILINE);
    private static final List<String> ALLOWED_PREFIXES = List.of(
            "java.", "com.fasterxml.", "org.jspecify.", "org.junit.", "org.opentest4j.", "net.jqwik.", "com.tngtech.");

    private MechanicsModuleImports() {}

    static Set<String> apiClasses(Map<Path, String> sources) {
        Set<String> classes = new LinkedHashSet<>();
        sources.forEach((file, text) -> {
            Matcher pkg = PACKAGE.matcher(text);
            assertTrue(pkg.find(), "Файл " + file + " без package: :api не может быть частью пакета");
            classes.add(pkg.group(1) + "." + file.getFileName().toString().replace(".java", ""));
        });
        return classes;
    }

    static List<String> violations(Map<Path, String> sources, Set<String> apiClasses, List<String> ownPackages) {
        return violations(sources, apiClasses, ownPackages, List.of());
    }

    static List<String> violations(
            Map<Path, String> sources, Set<String> apiClasses, List<String> ownPackages, List<String> allowedPackages) {
        List<String> found = new ArrayList<>();
        sources.forEach((file, text) -> {
            Matcher matcher = IMPORT.matcher(text);
            while (matcher.find()) {
                String name = matcher.group(1);
                if (name.endsWith(".*")) {
                    name = name.substring(0, name.length() - 2);
                }
                if (allowed(name, apiClasses, ownPackages, allowedPackages)) {
                    continue;
                }
                found.add(SourceFiles.logical(file) + ": " + name);
            }
        });
        return found;
    }

    private static boolean allowed(
            String name, Set<String> apiClasses, List<String> ownPackages, List<String> allowedPackages) {
        for (String prefix : ALLOWED_PREFIXES) {
            if (name.startsWith(prefix)) {
                return true;
            }
        }
        for (String ownPackage : ownPackages) {
            String own = "com.theminespirit." + ownPackage;
            if (name.equals(own) || name.startsWith(own + ".")) {
                return true;
            }
        }
        for (String allowedPackage : allowedPackages) {
            String allowed = "com.theminespirit." + allowedPackage;
            if (name.equals(allowed) || name.startsWith(allowed + ".")) {
                return true;
            }
        }
        for (String apiClass : apiClasses) {
            if (name.equals(apiClass) || name.startsWith(apiClass + ".")) {
                return true;
            }
        }
        return false;
    }
}
