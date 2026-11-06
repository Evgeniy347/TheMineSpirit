package com.theminespirit.guards;

import static com.theminespirit.guards.DeadCodeParser.parse;

import com.theminespirit.guards.DeadCodeModel.Declaration;
import com.theminespirit.guards.DeadCodeModel.Finding;
import com.theminespirit.guards.DeadCodeModel.Parsed;
import com.theminespirit.guards.DeadCodeModel.Token;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

final class DeadCodeScanner {
    static List<Finding> deadCode(Map<Path, String> sources) {
        return deadCode(sources, Map.of());
    }

    static List<Finding> deadCode(Map<Path, String> production, Map<Path, String> entrySources) {
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

    static void addParsed(
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
            file = file.withDeclarations(entries);
        }
        parsed.put(entry.getKey(), file);
        for (Declaration declaration : file.declarations()) {
            byName.computeIfAbsent(declaration.name(), name -> new ArrayList<>())
                    .add(declaration);
        }
    }

    static Set<String> liveTypes(Map<Path, Parsed> parsed, Map<String, List<Declaration>> byName) {
        Map<String, Set<String>> edges = new HashMap<>();
        Map<String, Declaration> types = new HashMap<>();
        Set<String> roots = new HashSet<>();
        Set<String> entryOwners = new HashSet<>();
        parsed.values().stream()
                .flatMap(file -> file.declarations().stream())
                .filter(declaration -> declaration.entry() && !isType(declaration) && "main".equals(declaration.name()))
                .forEach(declaration -> entryOwners.add(declaration.owner()));
        for (Parsed file : parsed.values()) {
            List<Declaration> fileTypes =
                    file.declarations().stream().filter(DeadCodeScanner::isType).toList();
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
                            || file.ignored(token.offset())
                            || declarationOffsets.contains(token.offset())) {
                        continue;
                    }
                    List<Declaration> candidates = byName.getOrDefault(token.text(), List.of()).stream()
                            .filter(DeadCodeScanner::isType)
                            .toList();
                    for (Declaration candidate : resolveType(type, candidates)) {
                        edge(edges, type, candidate);
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

    static List<Declaration> resolveType(Declaration type, List<Declaration> candidates) {
        if (candidates.size() <= 1) {
            return candidates;
        }
        List<Declaration> nested = candidates.stream()
                .filter(candidate -> candidate.owner().equals(type.name()))
                .toList();
        if (!nested.isEmpty()) {
            return nested;
        }
        String directory = directoryOf(type.file());
        List<Declaration> sameDirectory = candidates.stream()
                .filter(candidate -> directory.equals(directoryOf(candidate.file())))
                .toList();
        return sameDirectory.isEmpty() ? candidates : sameDirectory;
    }

    static String directoryOf(String file) {
        int slash = file.lastIndexOf('/');
        return slash < 0 ? "" : file.substring(0, slash);
    }

    static Map<String, Integer> references(
            Map<Path, Parsed> parsed,
            Map<String, List<Declaration>> byName,
            Set<String> liveTypes,
            Set<Path> entrySources) {
        Map<String, Integer> references = new HashMap<>();
        for (Map.Entry<Path, Parsed> entry : parsed.entrySet()) {
            Parsed file = entry.getValue();
            boolean allLive = entrySources.contains(entry.getKey());
            List<Declaration> types = file.declarations().stream()
                    .filter(DeadCodeScanner::isType)
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
                if (!token.identifier() || file.ignored(token.offset()) || stack.isEmpty()) {
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

    static List<String> memberOwners(
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

    static boolean hasMember(Map<String, List<Declaration>> byName, String owner, String name) {
        return byName.getOrDefault(name, List.of()).stream()
                .anyMatch(declaration ->
                        !isType(declaration) && declaration.owner().equals(owner));
    }

    static boolean isTypeName(String name, Map<String, List<Declaration>> byName) {
        return byName.getOrDefault(name, List.of()).stream().anyMatch(DeadCodeScanner::isType);
    }

    static String memberKey(Declaration declaration) {
        return declaration.owner() + "#" + declaration.name();
    }

    static void edge(Map<String, Set<String>> edges, Declaration from, Declaration to) {
        if (!key(from).equals(key(to))) {
            edges.computeIfAbsent(key(from), unused -> new HashSet<>()).add(key(to));
        }
    }

    static boolean isType(Declaration declaration) {
        return switch (declaration.kind()) {
            case "class", "interface", "enum", "record" -> true;
            default -> false;
        };
    }

    static String key(Declaration declaration) {
        return declaration.file() + "|" + declaration.kind() + "|" + declaration.owner() + "#" + declaration.name();
    }
}
