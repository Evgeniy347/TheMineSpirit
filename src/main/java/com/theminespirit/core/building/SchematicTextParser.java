package com.theminespirit.core.building;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.script.CatalogLimits;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * Разбор текстового файла постройки (формат слоёв с легендой). Формат — строгое подмножество YAML:
 * ключи верхнего уровня, легенда {@code 'c': блок[свойства] "текст"}, исключения {@code - rule/reason} и слои
 * {@code - yN: |} со строками, отступ которых на 4 больше отступа дефиса. Первая строка слоя — фронт (z=0),
 * символы слева направо — +X. {@code .} — клетка обязана быть воздухом, пробел — клетку не трогать.
 * Каждая ошибка называет файл, строку и что исправить.
 */
public final class SchematicTextParser {
    public static final char AIR = '.';
    public static final char ANY = ' ';
    public static final String EXTENSION = ".txt";

    private static final Set<String> KEYS =
            Set.of("name", "type", "stage", "level", "requires", "size", "entrance", "legend", "exemptions", "layers");
    private static final Pattern KEY_LINE = Pattern.compile("^([a-z]+):(.*)$");
    private static final Pattern LEGEND_LINE = Pattern.compile("^\\s+'(.)':\\s*(.*)$");
    private static final Pattern LAYER_LINE = Pattern.compile("^(\\s*)- y(\\d+)(?:\\s+[^:|]*)?:\\s*\\|\\s*$");
    private static final Pattern SIZE = Pattern.compile("^(\\d+)\\s*[x×]\\s*(\\d+)\\s*[x×]\\s*(\\d+)$");
    private static final Pattern COORDS = Pattern.compile("^(\\d+)\\s*,\\s*(\\d+)\\s*,\\s*(\\d+)$");
    private static final Pattern BLOCK_SPEC =
            Pattern.compile("^([a-z0-9_.\\-]+(?::[a-z0-9_./\\-]+)?)(?:\\[([^\\]]*)])?(?:\\s+\"([^\"]*)\")?$");
    private static final Pattern STATE_PAIR = Pattern.compile("^([a-z0-9_]+)=([a-z0-9_]+)$");

    record LegendEntry(String block, Map<String, String> state, String text, int line) {}

    record RawLayer(int y, int line, List<String> rows) {}

    private final String origin;
    private final List<String> lines;
    private int index;

    private Optional<String> name = Optional.empty();
    private Optional<BuildingType> type = Optional.empty();
    private Optional<Integer> stage = Optional.empty();
    private int level = Preset.LEVEL_NONE;
    private Optional<String> requires = Optional.empty();
    private int @Nullable [] size;
    private int sizeLine;
    private @Nullable String entrance;
    private int entranceLine;
    private final Map<Character, LegendEntry> legend = new LinkedHashMap<>();
    private final List<SchematicDocument.Exemption> exemptions = new ArrayList<>();
    private final List<RawLayer> layers = new ArrayList<>();

    private SchematicTextParser(String text, String origin) {
        this.origin = origin;
        this.lines = List.of(text.replace("\r", "").split("\n", -1));
    }

    public static SchematicDocument parse(String text, String origin) {
        Objects.requireNonNull(text, "Текст постройки не может быть null: разбирать нечего");
        Objects.requireNonNull(origin, "Источник файла не может быть null: ошибка называет путь");
        return new SchematicTextParser(text, origin).run();
    }

    private SchematicDocument run() {
        Set<String> seen = new HashSet<>();
        while (index < lines.size()) {
            String line = lines.get(index);
            if (isBlankOrComment(line)) {
                index++;
                continue;
            }
            if (Character.isWhitespace(line.charAt(0))) {
                throw error(
                        index,
                        "строка с отступом вне раздела: отступ бывает только внутри legend, exemptions "
                                + "или layers");
            }
            Matcher key = KEY_LINE.matcher(line);
            if (!key.matches()) {
                throw error(index, "ожидается «ключ: значение» (ключи: " + String.join(", ", sortedKeys()) + ")");
            }
            String name = key.group(1);
            if (!KEYS.contains(name)) {
                throw error(index, "неизвестный ключ '" + name + "': допустимы " + String.join(", ", sortedKeys()));
            }
            if (!seen.add(name)) {
                throw error(index, "ключ '" + name + "' указан дважды: второе значение молча перекрыло бы первое");
            }
            String value = stripComment(key.group(2).trim());
            int keyLine = index;
            index++;
            switch (name) {
                case "legend" -> legend(keyLine, value);
                case "exemptions" -> exemptions(keyLine, value);
                case "layers" -> layers(keyLine, value);
                default -> scalar(name, value, keyLine);
            }
        }
        return build();
    }

    private void scalar(String key, String value, int line) {
        if (value.isEmpty()) {
            throw error(line, "у ключа '" + key + "' нет значения");
        }
        switch (key) {
            case "name" -> name = Optional.of(unquote(value, line));
            case "type" ->
                type = Optional.of(BuildingType.byId(value)
                        .orElseThrow(() -> error(line, "неизвестный тип '" + value + "': допустимы " + typeIds())));
            case "stage" -> stage = Optional.of(integer(value, line, "stage"));
            case "level" -> level = integer(value, line, "level");
            case "requires" -> requires = Optional.of(value);
            case "size" -> {
                Matcher size = SIZE.matcher(value);
                if (!size.matches()) {
                    throw error(line, "размер '" + value + "' не по формату: ожидается Ш×В×Д, например 7x5x7");
                }
                this.size = new int[] {
                    Integer.parseInt(size.group(1)), Integer.parseInt(size.group(2)), Integer.parseInt(size.group(3))
                };
                sizeLine = line;
            }
            case "entrance" -> {
                entrance = value;
                entranceLine = line;
            }
            default -> throw error(line, "ключ '" + key + "' не скаляр");
        }
    }

    private void legend(int keyLine, String value) {
        requireSection(keyLine, value, "legend");
        while (index < lines.size() && isSectionLine(lines.get(index))) {
            String line = lines.get(index);
            if (isBlankOrComment(line)) {
                index++;
                continue;
            }
            Matcher entry = LEGEND_LINE.matcher(line);
            if (!entry.matches()) {
                throw error(
                        index, "строка легенды не по формату: ожидается «  'c': блок[свойство=значение] \"текст\"»");
            }
            char symbol = entry.group(1).charAt(0);
            if (symbol == AIR || symbol == ANY || symbol == '\'' || symbol == '-') {
                throw error(
                        index,
                        "символ '" + symbol + "' зарезервирован: '.' — воздух, пробел — «не трогать», "
                                + "кавычка и дефис ломают разбор");
            }
            if (legend.containsKey(symbol)) {
                throw error(index, "символ '" + symbol + "' в легенде дважды: клетка с ним стала бы двусмысленной");
            }
            legend.put(symbol, blockSpec(stripComment(entry.group(2).trim()), index));
            index++;
        }
        if (legend.isEmpty()) {
            throw error(keyLine, "легенда пуста: нечем расшифровать слои");
        }
    }

    private LegendEntry blockSpec(String spec, int line) {
        Matcher block = BLOCK_SPEC.matcher(spec);
        if (!block.matches()) {
            throw error(
                    line,
                    "блок '" + spec + "' не по формату: ожидается id[свойство=значение,…] и, для таблички, "
                            + "текст в кавычках");
        }
        String id = block.group(1).contains(":") ? block.group(1) : "minecraft:" + block.group(1);
        if ("air".equals(SchematicBlocks.path(id))) {
            throw error(line, "воздух в легенде не задаётся: для обязательной пустоты есть символ '.'");
        }
        Map<String, String> state = new LinkedHashMap<>();
        if (block.group(2) != null && !block.group(2).isBlank()) {
            for (String pair : block.group(2).split(",", -1)) {
                Matcher property = STATE_PAIR.matcher(pair.trim());
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
        return new LegendEntry(id, Map.copyOf(state), block.group(3) == null ? "" : block.group(3), line);
    }

    private void exemptions(int keyLine, String value) {
        requireSection(keyLine, value, "exemptions");
        String rule = null;
        int ruleLine = -1;
        while (index < lines.size() && isSectionLine(lines.get(index))) {
            String line = lines.get(index);
            if (isBlankOrComment(line)) {
                index++;
                continue;
            }
            String trimmed = line.trim();
            if (trimmed.startsWith("- rule:")) {
                if (rule != null) {
                    throw error(
                            ruleLine, "у исключения '" + rule + "' нет reason: без причины правило ослабляется молча");
                }
                rule = stripComment(trimmed.substring("- rule:".length()).trim());
                ruleLine = index;
            } else if (trimmed.startsWith("reason:")) {
                if (rule == null) {
                    throw error(index, "reason без rule: ожидается «- rule: id» строкой выше");
                }
                String reason = unquote(trimmed.substring("reason:".length()).trim(), index);
                String ruleId = rule;
                int unknownLine = ruleLine;
                SchematicRules.RuleId id = SchematicRules.RuleId.byId(ruleId)
                        .orElseThrow(() -> error(
                                unknownLine,
                                "исключение ссылается на несуществующее правило '" + ruleId + "': допустимы "
                                        + ruleIds()));
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

    private void layers(int keyLine, String value) {
        requireSection(keyLine, value, "layers");
        while (index < lines.size()) {
            String line = lines.get(index);
            if (isBlankOrComment(line)) {
                index++;
                continue;
            }
            Matcher header = LAYER_LINE.matcher(line);
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
                if (LAYER_LINE.matcher(row).matches()) {
                    break;
                }
                if (!row.isBlank() && indent(row) < rowIndent) {
                    break;
                }
                rows.add(row.length() <= rowIndent ? "" : row.substring(rowIndent));
                index++;
            }
            layers.add(new RawLayer(y, layerLine, List.copyOf(rows)));
        }
    }

    private SchematicDocument build() {
        if (size == null) {
            throw error(-1, "нет ключа size: габариты Ш×В×Д обязательны");
        }
        int sx = size[0];
        int sy = size[1];
        int sz = size[2];
        if (sx < 1 || sy < 1 || sz < 1) {
            throw error(sizeLine, "размер " + sx + "×" + sy + "×" + sz + ": стороны обязаны быть хотя бы 1");
        }
        int max = CatalogLimits.SCHEMATIC_SIZE_MAX;
        if (sx > max || sy > max || sz > max) {
            throw error(
                    sizeLine,
                    "размер " + sx + "×" + sy + "×" + sz + " больше предела " + max
                            + ": большая схема строится слишком долго");
        }
        if (legend.isEmpty()) {
            throw error(-1, "нет раздела legend: нечем расшифровать слои");
        }
        char[][][] grid = grid(sx, sy, sz);
        SchematicGrid.Result cells = SchematicGrid.resolve(grid, legend, origin);
        if (cells.blocks().size() > CatalogLimits.SCHEMATIC_BLOCKS_MAX) {
            throw error(
                    -1,
                    "блоков " + cells.blocks().size() + ", а предел " + CatalogLimits.SCHEMATIC_BLOCKS_MAX
                            + ": большая схема строится слишком долго");
        }
        Schematic schematic = new Schematic(sx, sy, sz, cells.blocks(), cells.air());
        SchematicDocument.Header header =
                new SchematicDocument.Header(name, type, stage, level, requires, entrance(schematic));
        return new SchematicDocument(schematic, header, exemptions);
    }

    private char[][][] grid(int sx, int sy, int sz) {
        RawLayer[] byY = new RawLayer[sy];
        for (RawLayer layer : layers) {
            if (layer.y() >= sy) {
                throw error(
                        layer.line(),
                        "слой y" + layer.y() + " выше габарита: по size высота " + sy + " (слои y0…y" + (sy - 1) + ")");
            }
            if (byY[layer.y()] != null) {
                throw error(layer.line(), "слой y" + layer.y() + " описан дважды");
            }
            byY[layer.y()] = layer;
        }
        char[][][] grid = new char[sy][sz][sx];
        for (int y = 0; y < sy; y++) {
            RawLayer layer = byY[y];
            if (layer == null) {
                throw error(-1, "нет слоя y" + y + ": по size слоёв " + sy + ", каждый описывается явно");
            }
            List<String> rows = new ArrayList<>(layer.rows());
            while (!rows.isEmpty() && rows.get(rows.size() - 1).isBlank()) {
                rows.remove(rows.size() - 1);
            }
            if (rows.size() != sz) {
                throw error(
                        layer.line(),
                        "в слое y" + y + " строк " + rows.size() + ", а по size глубина "
                                + sz + ": первая строка — фронт (z=0), последняя — тыл; пустые строки в конце слоя "
                                + "не считаются");
            }
            for (int z = 0; z < sz; z++) {
                String row = rows.get(z);
                if (row.length() > sx) {
                    throw error(
                            layer.line() + 1 + z,
                            "в строке " + row.length() + " символов, а по size ширина " + sx + " (лишнее: '"
                                    + row.substring(sx) + "')");
                }
                for (int x = 0; x < sx; x++) {
                    char symbol = x < row.length() ? row.charAt(x) : ANY;
                    if (symbol != AIR && symbol != ANY && !legend.containsKey(symbol)) {
                        throw error(layer.line() + 1 + z, "символ '" + symbol + "' (x=" + x + ") не описан в легенде");
                    }
                    grid[y][z][x] = symbol;
                }
            }
        }
        return grid;
    }

    private Optional<BlockCoord> entrance(Schematic schematic) {
        if (entrance == null) {
            return Optional.empty();
        }
        if ("front".equals(entrance)) {
            Schematic.Entry best = null;
            for (Schematic.Entry entry : schematic.blocks()) {
                if (!SchematicBlocks.isEntranceBlock(entry.block())
                        || SchematicBlocks.isSecondaryPart(entry.block(), entry.state())) {
                    continue;
                }
                if (best == null
                        || entry.dz() < best.dz()
                        || (entry.dz() == best.dz() && entry.dy() < best.dy())
                        || (entry.dz() == best.dz() && entry.dy() == best.dy() && entry.dx() < best.dx())) {
                    best = entry;
                }
            }
            if (best == null) {
                throw error(
                        entranceLine,
                        "entrance: front, но в слоях нет двери или калитки: "
                                + "поставьте дверь во фронт или укажите вход координатами x,y,z");
            }
            return Optional.of(new BlockCoord(best.dx(), best.dy(), best.dz()));
        }
        Matcher coords = COORDS.matcher(entrance);
        if (!coords.matches()) {
            throw error(entranceLine, "вход '" + entrance + "' не по формату: ожидается front или x,y,z");
        }
        BlockCoord cell = new BlockCoord(
                Integer.parseInt(coords.group(1)),
                Integer.parseInt(coords.group(2)),
                Integer.parseInt(coords.group(3)));
        if (cell.x() >= schematic.sizeX() || cell.y() >= schematic.sizeY() || cell.z() >= schematic.sizeZ()) {
            throw error(
                    entranceLine,
                    "вход " + entrance + " вне габаритов " + schematic.sizeX() + "×" + schematic.sizeY() + "×"
                            + schematic.sizeZ());
        }
        return Optional.of(cell);
    }

    private void requireSection(int line, String value, String key) {
        if (!value.isEmpty()) {
            throw error(
                    line, "после '" + key + ":' значение не пишется: содержимое раздела — строками с отступом ниже");
        }
    }

    private int integer(String value, int line, String key) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException notNumber) {
            throw error(line, "у ключа '" + key + "' ожидается целое число, а не '" + value + "'");
        }
    }

    private String unquote(String value, int line) {
        if (value.startsWith("\"")) {
            if (value.length() < 2
                    || !value.endsWith("\"")
                    || value.substring(1, value.length() - 1).contains("\"")) {
                throw error(line, "строка в кавычках не закрыта или содержит кавычку внутри: " + value);
            }
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private static String stripComment(String value) {
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

    private static boolean isBlankOrComment(String line) {
        String trimmed = line.trim();
        return trimmed.isEmpty() || trimmed.startsWith("#");
    }

    private static boolean isSectionLine(String line) {
        return line.isEmpty() || line.isBlank() || Character.isWhitespace(line.charAt(0));
    }

    private static int indent(String line) {
        int count = 0;
        while (count < line.length() && line.charAt(count) == ' ') {
            count++;
        }
        return count;
    }

    private static List<String> sortedKeys() {
        return KEYS.stream().sorted().toList();
    }

    private static String typeIds() {
        List<String> ids = new ArrayList<>();
        for (BuildingType value : BuildingType.values()) {
            ids.add(value.name().toLowerCase(Locale.ROOT));
        }
        return String.join(", ", ids);
    }

    private static String ruleIds() {
        List<String> ids = new ArrayList<>();
        for (SchematicRules.RuleId rule : SchematicRules.RuleId.values()) {
            ids.add(rule.id());
        }
        return String.join(", ", ids);
    }

    private IllegalStateException error(int line, String message) {
        String where = line < 0 ? "" : ", строка " + (line + 1);
        return new IllegalStateException("Файл постройки '" + origin + "'" + where + ": " + message);
    }
}
