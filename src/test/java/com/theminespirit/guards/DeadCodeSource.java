package com.theminespirit.guards;

import com.theminespirit.guards.DeadCodeModel.Frame;
import com.theminespirit.guards.DeadCodeModel.Kind;
import com.theminespirit.guards.DeadCodeModel.Token;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

final class DeadCodeSource {
    static List<int[]> recordHeaders(List<Token> tokens) {
        List<int[]> headers = new ArrayList<>();
        for (int index = 0; index + 2 < tokens.size(); index++) {
            Token keyword = tokens.get(index);
            if (!"record".equals(keyword.text())
                    || !tokens.get(index + 1).identifier()
                    || !"(".equals(tokens.get(index + 2).text())) {
                continue;
            }
            int depth = 0;
            for (int inner = index + 2; inner < tokens.size(); inner++) {
                String text = tokens.get(inner).text();
                if ("(".equals(text)) {
                    depth++;
                } else if (")".equals(text)) {
                    depth--;
                    if (depth == 0) {
                        headers.add(new int[] {
                            tokens.get(index + 2).offset(), tokens.get(inner).offset() + 1
                        });
                        break;
                    }
                }
            }
        }
        return headers;
    }

    static Frame[] frames(List<Token> tokens) {
        Frame[] frames = new Frame[tokens.size()];
        Deque<Frame> stack = new ArrayDeque<>();
        String pendingName = null;
        boolean pendingEnum = false;
        boolean pendingAnnotation = false;
        for (int index = 0; index < tokens.size(); index++) {
            Token token = tokens.get(index);
            frames[index] = stack.peek();
            if (token.identifier()
                    && isTypeKeyword(token.text())
                    && (index == 0 || !".".equals(tokens.get(index - 1).text()))) {
                if (index + 1 < tokens.size() && tokens.get(index + 1).identifier()) {
                    pendingName = tokens.get(index + 1).text();
                    pendingEnum = "enum".equals(token.text());
                    pendingAnnotation = "interface".equals(token.text())
                            && index > 0
                            && "@".equals(tokens.get(index - 1).text());
                }
            } else if ("{".equals(token.text()) && pendingName != null) {
                stack.push(new Frame(Kind.TYPE, pendingName, pendingEnum, pendingAnnotation));
                pendingName = null;
            } else if ("{".equals(token.text())) {
                stack.push(new Frame(Kind.BLOCK, "", false, false));
            } else if ("}".equals(token.text())) {
                if (!stack.isEmpty()) {
                    stack.pop();
                }
            } else if (";".equals(token.text())
                    && !stack.isEmpty()
                    && stack.peek().kind() == Kind.TYPE
                    && stack.peek().isEnum()
                    && stack.peek().enumConstantsEnd() < 0) {
                stack.push(stack.pop().withEnumConstantsEnd(token.offset()));
            }
        }
        return frames;
    }

    static boolean isTypeKeyword(String text) {
        return "class".equals(text) || "interface".equals(text) || "enum".equals(text) || "record".equals(text);
    }

    static String strip(String source) {
        char[] chars = source.toCharArray();
        int index = 0;
        while (index < chars.length) {
            char current = chars[index];
            if (current == '/' && index + 1 < chars.length && chars[index + 1] == '/') {
                index = blank(chars, index, lineEnd(chars, index));
            } else if (current == '/' && index + 1 < chars.length && chars[index + 1] == '*') {
                int end = source.indexOf("*/", index + 2);
                index = blank(chars, index, end < 0 ? chars.length : end + 2);
            } else if (current == '"' && source.startsWith("\"\"\"", index)) {
                int end = source.indexOf("\"\"\"", index + 3);
                index = blank(chars, index, end < 0 ? chars.length : end + 3);
            } else if (current == '"' || current == '\'') {
                int cursor = index + 1;
                while (cursor < chars.length && chars[cursor] != current) {
                    if (chars[cursor] == '\\') {
                        cursor++;
                    }
                    cursor++;
                }
                index = blank(chars, index, Math.min(chars.length, cursor + 1));
            } else {
                index++;
            }
        }
        return new String(chars);
    }

    static int lineEnd(char[] chars, int start) {
        int end = start;
        while (end < chars.length && chars[end] != '\n') {
            end++;
        }
        return end;
    }

    static int blank(char[] chars, int start, int end) {
        for (int index = start; index < end; index++) {
            if (chars[index] != '\n') {
                chars[index] = ' ';
            }
        }
        return end;
    }

    static List<Token> tokenize(String code) {
        List<Token> tokens = new ArrayList<>();
        int index = 0;
        while (index < code.length()) {
            char current = code.charAt(index);
            if (Character.isJavaIdentifierStart(current)) {
                int end = index + 1;
                while (end < code.length() && Character.isJavaIdentifierPart(code.charAt(end))) {
                    end++;
                }
                tokens.add(new Token(code.substring(index, end), index, true));
                index = end;
            } else if (Character.isDigit(current)) {
                int end = index + 1;
                while (end < code.length()
                        && (Character.isLetterOrDigit(code.charAt(end))
                                || code.charAt(end) == '.'
                                || code.charAt(end) == '_')) {
                    end++;
                }
                index = end;
            } else if (Character.isWhitespace(current)) {
                index++;
            } else {
                tokens.add(new Token(String.valueOf(current), index, false));
                index++;
            }
        }
        return tokens;
    }

    static Token tokenAt(List<Token> tokens, int offset) {
        int low = 0;
        int high = tokens.size() - 1;
        while (low <= high) {
            int middle = (low + high) >>> 1;
            Token token = tokens.get(middle);
            if (token.offset() < offset) {
                low = middle + 1;
            } else if (token.offset() > offset) {
                high = middle - 1;
            } else {
                return token;
            }
        }
        return null;
    }

    static int indexOf(List<Token> tokens, Token token) {
        int low = 0;
        int high = tokens.size() - 1;
        while (low <= high) {
            int middle = (low + high) >>> 1;
            Token current = tokens.get(middle);
            if (current.offset() < token.offset()) {
                low = middle + 1;
            } else if (current.offset() > token.offset()) {
                high = middle - 1;
            } else {
                return middle;
            }
        }
        return -1;
    }
}
