package com.theminespirit.core.building;

final class SchematicTextSyntax {
    private SchematicTextSyntax() {}

    static IllegalStateException error(String origin, int line, String message) {
        String where = line < 0 ? "" : ", строка " + (line + 1);
        return new IllegalStateException("Файл постройки '" + origin + "'" + where + ": " + message);
    }

    static int integer(String origin, String value, int line, String key) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException notNumber) {
            throw error(origin, line, "у ключа '" + key + "' ожидается целое число, а не '" + value + "'");
        }
    }

    static String unquote(String origin, String value, int line) {
        if (value.startsWith("\"")) {
            if (value.length() < 2
                    || !value.endsWith("\"")
                    || value.substring(1, value.length() - 1).contains("\"")) {
                throw error(origin, line, "строка в кавычках не закрыта или содержит кавычку внутри: " + value);
            }
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    static String stripComment(String value) {
        if (value.startsWith("\"")) {
            int close = value.indexOf('"', 1);
            if (close > 0) {
                String rest = value.substring(close + 1);
                int hash = rest.indexOf(" #");
                return hash < 0 ? value : value.substring(0, close + 1 + hash).trim();
            }
            return value;
        }
        if (value.startsWith("#")) {
            return "";
        }
        int hash = value.indexOf(" #");
        return hash < 0 ? value : value.substring(0, hash).trim();
    }

    static boolean isBlankOrComment(String line) {
        String trimmed = line.trim();
        return trimmed.isEmpty() || trimmed.startsWith("#");
    }

    static boolean isSectionLine(String line) {
        return line.isEmpty() || line.isBlank() || Character.isWhitespace(line.charAt(0));
    }
}
