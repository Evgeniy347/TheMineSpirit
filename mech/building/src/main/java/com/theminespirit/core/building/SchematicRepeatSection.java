package com.theminespirit.core.building;

import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

final class SchematicRepeatSection {
    private static final String STEP_PREFIX = "step:";
    private static final String UNTIL_PREFIX = "until:";
    private static final String TIMES_PREFIX = "times:";
    private static final String TORCH_PREFIX = "torch:";

    private final String origin;
    private final List<String> lines;
    private int index;

    private SchematicRepeatSection(String origin, List<String> lines, int index) {
        this.origin = origin;
        this.lines = lines;
        this.index = index;
    }

    record Result(Schematic.Repeat repeat, int nextIndex) {}

    static Result read(String origin, List<String> lines, int index, int keyLine, String value) {
        SchematicRepeatSection section = new SchematicRepeatSection(origin, lines, index);
        Schematic.Repeat repeat = section.load(keyLine, value);
        return new Result(repeat, section.index);
    }

    private Schematic.Repeat load(int keyLine, String value) {
        if (!value.isEmpty()) {
            return inline(keyLine, value);
        }
        int @Nullable [] step = null;
        Schematic.Repeat.Until until = null;
        Integer torchEvery = null;
        int stepLine = -1;
        while (index < lines.size() && SchematicTextSyntax.isSectionLine(lines.get(index))) {
            String line = lines.get(index);
            if (SchematicTextSyntax.isBlankOrComment(line)) {
                index++;
                continue;
            }
            String trimmed = line.trim();
            if (trimmed.startsWith(STEP_PREFIX)) {
                if (step != null) {
                    throw error(index, "шаг повтора задан дважды: вторая строка молча перекрыла бы первую");
                }
                step = step(
                        SchematicTextSyntax.stripComment(
                                trimmed.substring(STEP_PREFIX.length()).trim()),
                        index);
                stepLine = index;
            } else if (trimmed.startsWith(UNTIL_PREFIX)) {
                if (until != null) {
                    throw error(index, "условие повтора задано дважды: until и times исключают друг друга");
                }
                until = until(
                        SchematicTextSyntax.stripComment(
                                trimmed.substring(UNTIL_PREFIX.length()).trim()),
                        index);
            } else if (trimmed.startsWith(TIMES_PREFIX)) {
                if (until != null) {
                    throw error(index, "условие повтора задано дважды: until и times исключают друг друга");
                }
                until = new Schematic.Repeat.Times(SchematicTextSyntax.integer(
                        origin,
                        SchematicTextSyntax.stripComment(
                                trimmed.substring(TIMES_PREFIX.length()).trim()),
                        index,
                        "times"));
            } else if (trimmed.startsWith(TORCH_PREFIX)) {
                if (torchEvery != null) {
                    throw error(index, "шаг факелов повтора задан дважды: вторая строка молча перекрыла бы первую");
                }
                torchEvery = torchEvery(
                        SchematicTextSyntax.stripComment(
                                trimmed.substring(TORCH_PREFIX.length()).trim()),
                        index);
            } else {
                throw error(
                        index, "в repeat ожидается «step: [dx, dy, dz]» и «until: y=50», «times: N» или «torch: N»");
            }
            index++;
        }
        if (step == null) {
            throw error(keyLine, "у repeat нет шага: без него сегмент не повторить");
        }
        if (until == null) {
            throw error(keyLine, "у repeat нет условия: задайте until: y=… или times: N");
        }
        if (step[1] == 0 && until instanceof Schematic.Repeat.TargetY) {
            throw error(stepLine, "повтор до y без шага по y: цель по высоте недостижима");
        }
        return new Schematic.Repeat(step[0], step[1], step[2], until, torchEvery == null ? 0 : torchEvery);
    }

    private Schematic.Repeat inline(int line, String value) {
        String body = value.trim();
        if (!body.startsWith("{") || !body.endsWith("}")) {
            throw error(line, "repeat не по формату: ожидается {step: [dx, dy, dz], until: y=50} или раздел строками");
        }
        body = body.substring(1, body.length() - 1);
        int @Nullable [] step = null;
        Schematic.Repeat.Until until = null;
        int torchEvery = 0;
        for (String part : splitTopLevel(body)) {
            String pair = part.trim();
            if (pair.startsWith(STEP_PREFIX)) {
                step = step(pair.substring(STEP_PREFIX.length()).trim(), line);
            } else if (pair.startsWith(UNTIL_PREFIX)) {
                until = until(pair.substring(UNTIL_PREFIX.length()).trim(), line);
            } else if (pair.startsWith(TIMES_PREFIX)) {
                until = new Schematic.Repeat.Times(SchematicTextSyntax.integer(
                        origin, pair.substring(TIMES_PREFIX.length()).trim(), line, "times"));
            } else if (pair.startsWith(TORCH_PREFIX)) {
                torchEvery = torchEvery(pair.substring(TORCH_PREFIX.length()).trim(), line);
            } else {
                throw error(line, "в repeat ожидается step, until, times или torch, а не '" + pair + "'");
            }
        }
        if (step == null || until == null) {
            throw error(line, "в repeat нужны step и until (или times): иначе непонятно, что и до чего повторять");
        }
        if (step[1] == 0 && until instanceof Schematic.Repeat.TargetY) {
            throw error(line, "повтор до y без шага по y: цель по высоте недостижима");
        }
        return new Schematic.Repeat(step[0], step[1], step[2], until, torchEvery);
    }

    private static List<String> splitTopLevel(String body) {
        List<String> parts = new ArrayList<>();
        int depth = 0;
        int start = 0;
        for (int i = 0; i < body.length(); i++) {
            char symbol = body.charAt(i);
            if (symbol == '[' || symbol == '{') {
                depth++;
            } else if (symbol == ']' || symbol == '}') {
                depth--;
            } else if (symbol == ',' && depth == 0) {
                parts.add(body.substring(start, i));
                start = i + 1;
            }
        }
        parts.add(body.substring(start));
        return parts;
    }

    private int[] step(String value, int line) {
        var matcher = SchematicTextParser.STEP.matcher(value);
        if (!matcher.matches()) {
            throw error(line, "шаг повтора '" + value + "' не по формату: ожидается [dx, dy, dz], например [1, -1, 0]");
        }
        int[] step = new int[] {
            Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)), Integer.parseInt(matcher.group(3))
        };
        if (step[0] == 0 && step[1] == 0 && step[2] == 0) {
            throw error(line, "шаг повтора нулевой: сегмент повторялся бы на месте");
        }
        return step;
    }

    private int torchEvery(String value, int line) {
        int interval = SchematicTextSyntax.integer(origin, value, line, "torch");
        if (interval < 1) {
            throw error(
                    line,
                    "шаг факелов " + interval
                            + " меньше 1: укажите, через сколько копий ставить факелы, или не задавайте torch");
        }
        return interval;
    }

    private Schematic.Repeat.Until until(String value, int line) {
        String target = SchematicTextSyntax.unquote(origin, value, line);
        var y = SchematicTextParser.TARGET_Y.matcher(target);
        if (y.matches()) {
            return new Schematic.Repeat.TargetY(Integer.parseInt(y.group(1)));
        }
        try {
            return new Schematic.Repeat.Times(Integer.parseInt(target));
        } catch (NumberFormatException notNumber) {
            throw error(line, "условие повтора '" + target + "' не по формату: ожидается y=50 или число сегментов");
        }
    }

    private IllegalStateException error(int line, String message) {
        return SchematicTextSyntax.error(origin, line, message);
    }
}
