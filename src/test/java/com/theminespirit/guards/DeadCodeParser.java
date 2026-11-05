package com.theminespirit.guards;

import static com.theminespirit.guards.DeadCodeSource.frames;
import static com.theminespirit.guards.DeadCodeSource.indexOf;
import static com.theminespirit.guards.DeadCodeSource.recordHeaders;
import static com.theminespirit.guards.DeadCodeSource.strip;
import static com.theminespirit.guards.DeadCodeSource.tokenAt;
import static com.theminespirit.guards.DeadCodeSource.tokenize;

import com.theminespirit.guards.DeadCodeModel.Declaration;
import com.theminespirit.guards.DeadCodeModel.Frame;
import com.theminespirit.guards.DeadCodeModel.Kind;
import com.theminespirit.guards.DeadCodeModel.Parsed;
import com.theminespirit.guards.DeadCodeModel.Token;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class DeadCodeParser {
    static final Pattern IMPORT = Pattern.compile("(?m)^[ \\t]*(?:import|package)\\s[^;]*;");
    static final Pattern TYPE = Pattern.compile("(?<![\\w$.])(@)?(class|interface|enum|record)\\s+([A-Za-z_$][\\w$]*)");
    static final String ANNOTATION = "@[A-Za-z_$][\\w$.]*(?:\\s*\\([^()]*\\))?\\s*";
    static final String MODIFIER =
            "(?:public|protected|private|static|final|abstract|synchronized|native|default|strictfp|sealed|"
                    + "non-sealed|transient|volatile)\\s+";
    static final String PREFIX = "(?:(?:" + ANNOTATION + ")|(?:" + MODIFIER + "))*";
    static final Pattern METHOD = Pattern.compile(
            "(?:^|[;{}])\\s*" + PREFIX
                    + "(?:<[^;{}()]*>\\s*)?"
                    + "([A-Za-z_$][\\w$.]*(?:\\s*<[^{};()]*>)?(?:\\s*\\[\\])*)\\s+"
                    + "([A-Za-z_$][\\w$]*)\\s*\\(",
            Pattern.MULTILINE);
    static final Pattern TEST_METHOD = Pattern.compile("\\bvoid\\s+([A-Za-z_$][\\w$]*)\\s*\\(");
    static final Pattern FIELD = Pattern.compile(
            "(?:^|[;{}])\\s*" + PREFIX
                    + "([A-Za-z_$][\\w$.]*(?:\\s*<[^{};()]*>)?(?:\\s*\\[\\])*)\\s+"
                    + "([A-Za-z_$][\\w$]*)\\s*(?==|;)",
            Pattern.MULTILINE);
    static final Set<String> RESERVED = Set.of(
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

    static Parsed parse(Path file, String raw) {
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
        String path = SourceFiles.logical(file);

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

    static void collectMembers(
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

    static boolean entryPointType(String code, int offset) {
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

    static Declaration declaration(
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

    static int declarationEnd(List<Token> tokens, int start) {
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

    static boolean inRanges(List<int[]> ranges, int offset) {
        return ranges.stream().anyMatch(range -> offset >= range[0] && offset < range[1]);
    }

    static String firstWord(String type) {
        int end = 0;
        while (end < type.length() && Character.isJavaIdentifierPart(type.charAt(end))) {
            end++;
        }
        return type.substring(0, end);
    }
}
