package com.theminespirit.core.building;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;

final class SchematicSectionReader {
    private final String origin;
    private final List<String> lines;
    private int index;

    SchematicSectionReader(String origin, List<String> lines, int index) {
        this.origin = origin;
        this.lines = lines;
        this.index = index;
    }

    int index() {
        return index;
    }

    void exemptions(int keyLine, String value, List<SchematicDocument.Exemption> exemptions) {
        requireSection(keyLine, value, "exemptions");
        String rule = null;
        int ruleLine = -1;
        while (index < lines.size() && SchematicTextSyntax.isSectionLine(lines.get(index))) {
            String line = lines.get(index);
            if (SchematicTextSyntax.isBlankOrComment(line)) {
                index++;
                continue;
            }
            String trimmed = line.trim();
            if (trimmed.startsWith("- rule:")) {
                if (rule != null) {
                    throw error(
                            ruleLine, "у исключения '" + rule + "' нет reason: без причины правило ослабляется молча");
                }
                rule = SchematicTextSyntax.stripComment(
                        trimmed.substring("- rule:".length()).trim());
                ruleLine = index;
            } else if (trimmed.startsWith("reason:")) {
                if (rule == null) {
                    throw error(index, "reason без rule: ожидается «- rule: id» строкой выше");
                }
                String reason = SchematicTextSyntax.unquote(
                        origin, trimmed.substring("reason:".length()).trim(), index);
                String ruleId = rule;
                int unknownLine = ruleLine;
                SchematicRules.RuleId id = SchematicRules.RuleId.byId(ruleId)
                        .orElseThrow(() -> error(
                                unknownLine,
                                "исключение ссылается на несуществующее правило '" + ruleId + "': допустимы "
                                        + SchematicTextParser.ruleIds()));
                if (reason.isBlank()) {
                    throw error(index, "у исключения '" + ruleId + "' пустой reason");
                }
                if (exemptions.stream().anyMatch(existing -> existing.rule() == id)) {
                    throw error(ruleLine, "исключение '" + ruleId + "' объявлено дважды: одна из причин потерялась бы");
                }
                exemptions.add(new SchematicDocument.Exemption(id, reason));
                rule = null;
            } else {
                throw error(index, "в exemptions ожидается «- rule: id» и «reason: \"причина\"»");
            }
            index++;
        }
        if (rule != null) {
            throw error(ruleLine, "у исключения '" + rule + "' нет reason: без причины правило ослабляется молча");
        }
    }

    void layers(int keyLine, String value, List<SchematicTextParser.RawLayer> layers) {
        requireSection(keyLine, value, "layers");
        while (index < lines.size()) {
            String line = lines.get(index);
            if (SchematicTextSyntax.isBlankOrComment(line)) {
                index++;
                continue;
            }
            Matcher header = SchematicTextParser.LAYER_LINE.matcher(line);
            if (!header.matches()) {
                if (!Character.isWhitespace(line.charAt(0))) {
                    return;
                }
                throw error(index, "ожидается заголовок слоя «  - yN: |» (N — высота слоя от 0)");
            }
            int dashIndent = header.group(1).length();
            if (dashIndent == 0) {
                throw error(index, "заголовок слоя без отступа: слои вложены в layers, пишите «  - y0: |»");
            }
            int y = Integer.parseInt(header.group(2));
            int layerLine = index;
            int rowIndent = dashIndent + 4;
            index++;
            List<String> rows = new ArrayList<>();
            while (index < lines.size()) {
                String row = lines.get(index);
                if (SchematicTextParser.LAYER_LINE.matcher(row).matches()) {
                    break;
                }
                if (!row.isBlank() && SchematicTextParser.indent(row) < rowIndent) {
                    break;
                }
                rows.add(row.length() <= rowIndent ? "" : row.substring(rowIndent));
                index++;
            }
            layers.add(new SchematicTextParser.RawLayer(y, layerLine, List.copyOf(rows)));
        }
    }

    private void requireSection(int line, String value, String key) {
        if (!value.isEmpty()) {
            throw error(
                    line, "после '" + key + ":' значение не пишется: содержимое раздела — строками с отступом ниже");
        }
    }

    private IllegalStateException error(int line, String message) {
        return SchematicTextSyntax.error(origin, line, message);
    }

    void legend(int keyLine, String value, Map<Character, SchematicTextParser.LegendEntry> legend) {
        requireSection(keyLine, value, "legend");
        while (index < lines.size() && SchematicTextSyntax.isSectionLine(lines.get(index))) {
            String line = lines.get(index);
            if (SchematicTextSyntax.isBlankOrComment(line)) {
                index++;
                continue;
            }
            Matcher entry = SchematicTextParser.LEGEND_LINE.matcher(line);
            if (!entry.matches()) {
                throw error(
                        index, "строка легенды не по формату: ожидается «  'c': блок[свойство=значение] \"текст\"»");
            }
            char symbol = entry.group(1).charAt(0);
            if (symbol == SchematicTextParser.AIR
                    || symbol == SchematicTextParser.ANY
                    || symbol == '\''
                    || symbol == '-') {
                throw error(
                        index,
                        "символ '" + symbol + "' зарезервирован: '.' — воздух, пробел — «не трогать», "
                                + "кавычка и дефис ломают разбор");
            }
            if (legend.containsKey(symbol)) {
                throw error(index, "символ '" + symbol + "' в легенде дважды: клетка с ним стала бы двусмысленной");
            }
            legend.put(
                    symbol,
                    blockSpec(SchematicTextSyntax.stripComment(entry.group(2).trim()), index));
            index++;
        }
        if (legend.isEmpty()) {
            throw error(keyLine, "легенда пуста: нечем расшифровать слои");
        }
    }

    private SchematicTextParser.LegendEntry blockSpec(String spec, int line) {
        boolean optional = false;
        String trimmed = spec.trim();
        if (trimmed.endsWith("~")) {
            optional = true;
            trimmed = trimmed.substring(0, trimmed.length() - 1).trim();
        }
        if (trimmed.startsWith("@")) {
            if (!SchematicBlocks.FLOOR.equals(trimmed)) {
                throw error(line, "неизвестный класс блока '" + trimmed + "': из классов поддерживается только @floor");
            }
            return new SchematicTextParser.LegendEntry(SchematicBlocks.FLOOR, Map.of(), "", line, optional);
        }
        Matcher block = SchematicTextParser.BLOCK_SPEC.matcher(trimmed);
        if (!block.matches()) {
            throw error(
                    line,
                    "блок '" + trimmed + "' не по формату: ожидается id[свойство=значение,…] и, для таблички, "
                            + "текст в кавычках; необязательная клетка помечается суффиксом ~");
        }
        String id = block.group(1).contains(":") ? block.group(1) : "minecraft:" + block.group(1);
        if ("air".equals(SchematicBlocks.path(id))) {
            throw error(line, "воздух в легенде не задаётся: для обязательной пустоты есть символ '.'");
        }
        Map<String, String> state = new LinkedHashMap<>();
        if (block.group(2) != null && !block.group(2).isBlank()) {
            for (String pair : block.group(2).split(",", -1)) {
                Matcher property = SchematicTextParser.STATE_PAIR.matcher(pair.trim());
                if (!property.matches()) {
                    throw error(line, "свойство '" + pair.trim() + "' не по формату: ожидается имя=значение строчными");
                }
                if (state.put(property.group(1), property.group(2)) != null) {
                    throw error(line, "свойство '" + property.group(1) + "' указано дважды");
                }
            }
        }
        if (SchematicBlocks.isDoor(id) && state.containsKey("half")) {
            throw error(line, "у двери не задают half: нижняя и верхняя половины определяются по слоям");
        }
        if (SchematicBlocks.isBed(id) && state.containsKey("part")) {
            throw error(line, "у кровати не задают part: ножная и головная клетки определяются по facing");
        }
        if (SchematicBlocks.isBed(id) && !state.containsKey("facing")) {
            throw error(line, "у кровати обязателен facing: по нему находится изголовье");
        }
        return new SchematicTextParser.LegendEntry(
                id, Map.copyOf(state), block.group(3) == null ? "" : block.group(3), line, optional);
    }
}
