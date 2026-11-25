package com.theminespirit.core.town;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

final class TownDrawioXml {
    record Cell(Map<String, String> attrs, Map<String, String> geometry) {}

    private TownDrawioXml() {}

    static List<Cell> cells(String xml) {
        List<Cell> out = new ArrayList<>();
        int pos = 0;
        while ((pos = xml.indexOf("<mxCell", pos)) >= 0) {
            int start = pos + "<mxCell".length();
            int end = tagEnd(xml, pos);
            int bodyEnd = xml.indexOf("</mxCell>", end);
            int next = xml.indexOf("<mxCell", end);
            if (next >= 0 && (bodyEnd < 0 || next < bodyEnd)) {
                bodyEnd = next;
            }
            String body = xml.substring(end, bodyEnd < 0 ? xml.length() : bodyEnd);
            out.add(new Cell(attributes(xml, start, end), geometry(body)));
            pos = end;
        }
        return out;
    }

    static String content(String xml) {
        int diagram = xml.indexOf("<diagram");
        if (diagram < 0) {
            throw new IllegalArgumentException("В файле нет <diagram>: это не .drawio карты поселения");
        }
        int open = tagEnd(xml, diagram);
        int close = xml.indexOf("</diagram>", open);
        String inner = close < 0 ? xml.substring(open) : xml.substring(open, close);
        if (inner.contains("<mxGraphModel")) {
            return inner;
        }
        return compressed(inner.trim());
    }

    private static Map<String, String> geometry(String body) {
        int start = body.indexOf("<mxGeometry");
        if (start < 0) {
            return Map.of();
        }
        return attributes(body, start + "<mxGeometry".length(), tagEnd(body, start));
    }

    private static String compressed(String text) {
        try {
            byte[] packed = Base64.getMimeDecoder().decode(text);
            try (InflaterInputStream in =
                    new InflaterInputStream(new ByteArrayInputStream(packed), new Inflater(true))) {
                String inflated = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                String xml = URLDecoder.decode(inflated, StandardCharsets.UTF_8);
                if (!xml.contains("<mxGraphModel")) {
                    throw new IllegalArgumentException("Сжатая диаграмма распаковалась, но в ней нет mxGraphModel");
                }
                return xml;
            }
        } catch (IOException | IllegalArgumentException error) {
            throw new IllegalArgumentException(
                    "Диаграмма .drawio не читается: сохраните файл без сжатия "
                            + "(Extras → Edit Diagram) или выгрузите план заново",
                    error);
        }
    }

    private static Map<String, String> attributes(String text, int from, int to) {
        Map<String, String> out = new LinkedHashMap<>();
        int index = from;
        while (index < to) {
            while (index < to && Character.isWhitespace(text.charAt(index))) {
                index++;
            }
            if (index >= to || text.charAt(index) == '/' || text.charAt(index) == '>') {
                break;
            }
            int name = index;
            while (index < to
                    && !Character.isWhitespace(text.charAt(index))
                    && text.charAt(index) != '='
                    && text.charAt(index) != '/'
                    && text.charAt(index) != '>') {
                index++;
            }
            String key = text.substring(name, index);
            while (index < to && Character.isWhitespace(text.charAt(index))) {
                index++;
            }
            if (index >= to || text.charAt(index) != '=') {
                out.put(key, "");
                continue;
            }
            index++;
            while (index < to && Character.isWhitespace(text.charAt(index))) {
                index++;
            }
            if (index < to && (text.charAt(index) == '"' || text.charAt(index) == '\'')) {
                char quote = text.charAt(index++);
                int value = index;
                while (index < to && text.charAt(index) != quote) {
                    index++;
                }
                out.put(key, unescape(text.substring(value, index)));
                index++;
            } else {
                int value = index;
                while (index < to && !Character.isWhitespace(text.charAt(index)) && text.charAt(index) != '>') {
                    index++;
                }
                out.put(key, unescape(text.substring(value, index)));
            }
        }
        return out;
    }

    private static int tagEnd(String text, int start) {
        boolean single = false;
        boolean doubleQuote = false;
        for (int index = start; index < text.length(); index++) {
            char symbol = text.charAt(index);
            if (symbol == '\'' && !doubleQuote) {
                single = !single;
            } else if (symbol == '"' && !single) {
                doubleQuote = !doubleQuote;
            } else if (symbol == '>' && !single && !doubleQuote) {
                return index + 1;
            }
        }
        throw new IllegalArgumentException("Тег .drawio не закрыт: файл обрезан или повреждён");
    }

    private static String unescape(String text) {
        return text.replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&apos;", "'")
                .replace("&amp;", "&");
    }
}
