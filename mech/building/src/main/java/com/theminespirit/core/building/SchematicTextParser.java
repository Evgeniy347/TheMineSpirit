package com.theminespirit.core.building;

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

public final class SchematicTextParser {
    public static final char AIR = '.';
    public static final char ANY = ' ';
    public static final String EXTENSION = ".txt";

    private static final Set<String> KEYS = Set.of(
            "name",
            "type",
            "stage",
            "level",
            "requires",
            "size",
            "plot",
            "entrance",
            "legend",
            "exemptions",
            "layers",
            "repeat");
    private static final Pattern KEY_LINE = Pattern.compile("^([a-z]+):(.*)$");
    static final Pattern LEGEND_LINE = Pattern.compile("^\\s+'(.)':\\s*(.*)$");
    static final Pattern LAYER_LINE = Pattern.compile("^(\\s*)- y(\\d+)(?:\\s+[^:|]*)?:\\s*\\|\\s*$");
    private static final Pattern SIZE = Pattern.compile("^(\\d+)\\s*[x×]\\s*(\\d+)\\s*[x×]\\s*(\\d+)$");
    private static final Pattern PLOT = Pattern.compile("^(\\d+)\\s*[x×]\\s*(\\d+)$");
    static final Pattern COORDS = Pattern.compile("^(\\d+)\\s*,\\s*(\\d+)\\s*,\\s*(\\d+)$");
    static final Pattern BLOCK_SPEC =
            Pattern.compile("^([a-z0-9_.\\-]+(?::[a-z0-9_./\\-]+)?)(?:\\[([^\\]]*)])?(?:\\s+\"([^\"]*)\")?$");
    static final Pattern STATE_PAIR = Pattern.compile("^([a-z0-9_]+)=([a-z0-9_]+)$");
    static final Pattern STEP = Pattern.compile("^\\[\\s*(-?\\d+)\\s*,\\s*(-?\\d+)\\s*,\\s*(-?\\d+)\\s*]$");
    static final Pattern TARGET_Y = Pattern.compile("^y\\s*=\\s*(-?\\d+)$");

    record LegendEntry(String block, Map<String, String> state, String text, int line, boolean optional) {}

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
    private int @Nullable [] plot;
    private int plotLine;
    private @Nullable String entrance;
    private int entranceLine;
    private Schematic.@Nullable Repeat repeat;
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
                case "repeat" -> repeat(keyLine, value);
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
            case "plot" -> {
                Matcher plot = PLOT.matcher(value);
                if (!plot.matches()) {
                    throw error(line, "участок '" + value + "' не по формату: ожидается Ш×Д, например 32x32");
                }
                this.plot = new int[] {Integer.parseInt(plot.group(1)), Integer.parseInt(plot.group(2))};
                plotLine = line;
            }
            case "entrance" -> {
                entrance = value;
                entranceLine = line;
            }
            default -> throw error(line, "ключ '" + key + "' не скаляр");
        }
    }

    private void legend(int keyLine, String value) {
        SchematicSectionReader reader = new SchematicSectionReader(origin, lines, index);
        reader.legend(keyLine, value, legend);
        index = reader.index();
    }

    private void repeat(int keyLine, String value) {
        SchematicRepeatSection.Result result = SchematicRepeatSection.read(origin, lines, index, keyLine, value);
        repeat = result.repeat();
        index = result.nextIndex();
    }

    private void exemptions(int keyLine, String value) {
        SchematicSectionReader reader = new SchematicSectionReader(origin, lines, index);
        reader.exemptions(keyLine, value, exemptions);
        index = reader.index();
    }

    private void layers(int keyLine, String value) {
        SchematicSectionReader reader = new SchematicSectionReader(origin, lines, index);
        reader.layers(keyLine, value, layers);
        index = reader.index();
    }

    private SchematicDocument build() {
        return new SchematicDraft(
                        origin,
                        name,
                        type,
                        stage,
                        level,
                        requires,
                        size,
                        sizeLine,
                        plot,
                        plotLine,
                        entrance,
                        entranceLine,
                        repeat,
                        legend,
                        exemptions,
                        layers)
                .assemble();
    }

    private int integer(String value, int line, String key) {
        return SchematicTextSyntax.integer(origin, value, line, key);
    }

    private String unquote(String value, int line) {
        return SchematicTextSyntax.unquote(origin, value, line);
    }

    private static String stripComment(String value) {
        return SchematicTextSyntax.stripComment(value);
    }

    private static boolean isBlankOrComment(String line) {
        return SchematicTextSyntax.isBlankOrComment(line);
    }

    static int indent(String line) {
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

    static String ruleIds() {
        List<String> ids = new ArrayList<>();
        for (SchematicRules.RuleId rule : SchematicRules.RuleId.values()) {
            ids.add(rule.id());
        }
        return String.join(", ", ids);
    }

    private IllegalStateException error(int line, String message) {
        return SchematicTextSyntax.error(origin, line, message);
    }
}
