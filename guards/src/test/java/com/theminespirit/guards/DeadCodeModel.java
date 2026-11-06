package com.theminespirit.guards;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class DeadCodeModel {
    public enum Kind {
        TYPE,
        BLOCK
    }

    public record Frame(Kind kind, String name, boolean isEnum, boolean annotation, int enumConstantsEnd) {
        Frame(Kind kind, String name, boolean isEnum, boolean annotation) {
            this(kind, name, isEnum, annotation, -1);
        }

        Frame withEnumConstantsEnd(int offset) {
            return new Frame(kind, name, isEnum, annotation, offset);
        }
    }

    public record Token(String text, int offset, boolean identifier) {}

    public record Declaration(
            String file, int line, String kind, String owner, String name, int offset, int end, boolean entry) {}

    public record Finding(String file, int line, String kind, String owner, String name) {
        String key() {
            return file + "|" + kind + "|" + owner + "#" + name;
        }

        String describe() {
            return file + ":" + line + " " + kind + " " + owner + "#" + name;
        }
    }

    public static final class Parsed {
        private final List<Token> tokens;
        private final List<Declaration> declarations;
        private final boolean[] ignored;
        private final Set<String> importedTypes;
        private final Map<String, String> staticImports;

        public Parsed(
                List<Token> tokens,
                List<Declaration> declarations,
                boolean[] ignored,
                Set<String> importedTypes,
                Map<String, String> staticImports) {
            this.tokens = tokens;
            this.declarations = declarations;
            this.ignored = ignored.clone();
            this.importedTypes = importedTypes;
            this.staticImports = staticImports;
        }

        public List<Token> tokens() {
            return tokens;
        }

        public List<Declaration> declarations() {
            return declarations;
        }

        public boolean ignored(int offset) {
            return ignored[offset];
        }

        public Parsed withDeclarations(List<Declaration> declarations) {
            return new Parsed(tokens, declarations, ignored, importedTypes, staticImports);
        }

        public Set<String> importedTypes() {
            return importedTypes;
        }

        public Map<String, String> staticImports() {
            return staticImports;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Parsed other
                    && Objects.equals(tokens, other.tokens)
                    && Objects.equals(declarations, other.declarations)
                    && Objects.equals(importedTypes, other.importedTypes)
                    && Objects.equals(staticImports, other.staticImports)
                    && Arrays.equals(ignored, other.ignored);
        }

        @Override
        public int hashCode() {
            return 31 * Objects.hash(tokens, declarations, importedTypes, staticImports) + Arrays.hashCode(ignored);
        }

        @Override
        public String toString() {
            return "Parsed[" + "tokens=" + tokens + ", " + "declarations=" + declarations + ", " + "ignored="
                    + Arrays.toString(ignored) + ", importedTypes=" + importedTypes + ", staticImports="
                    + staticImports + "]";
        }
    }
}
