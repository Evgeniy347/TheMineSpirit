package com.theminespirit.core.tactics;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class TargetSelector {
    public enum Kind {
        HOSTILE,
        ANIMAL,
        PLAYER,
        SPIRIT,
        NEAREST,
        ANY
    }

    public record Target(Kind kind, Optional<String> name) {
        public Target {
            Objects.requireNonNull(kind, "kind не может быть null: вид цели выбирается скриптом явно");
            Objects.requireNonNull(name, "name не может быть null: отсутствие имени — Optional.empty()");
            boolean namedKind = kind == Kind.PLAYER || kind == Kind.SPIRIT;
            if (namedKind != name.isPresent()) {
                throw new IllegalArgumentException("Цель вида " + kind + " и имя " + name + " не согласованы: имя "
                        + (namedKind ? "обязательно для PLAYER и SPIRIT" : "не нужен ни одному виду цели"));
            }
        }
    }

    public static Target parse(String raw) {
        Objects.requireNonNull(raw, "raw не может быть null: цель берётся из параметров скрипта");
        String text = raw.trim();
        if (text.isEmpty()) {
            throw new IllegalArgumentException("Цель '" + raw + "' пустая: скрипт обязан сказать, кого искать, "
                    + "а пустая строка не указывает ни на одно существо");
        }
        Kind keyword = keywordOf(text);
        if (keyword != null) {
            return new Target(keyword, Optional.empty());
        }
        int colon = text.indexOf(':');
        if (colon >= 0) {
            String prefix = text.substring(0, colon).toLowerCase(Locale.ROOT);
            String value = text.substring(colon + 1);
            if (prefix.equals("player")) {
                return new Target(Kind.PLAYER, Optional.of(namedValue("player", value)));
            }
            if (prefix.equals("spirit")) {
                String uuid = namedValue("spirit", value);
                try {
                    UUID.fromString(uuid);
                } catch (IllegalArgumentException error) {
                    throw new IllegalArgumentException(
                            "Сpirit-цель '" + raw + "' не разобрана: '" + uuid
                                    + "' не является UUID, а дух ищется только по своему UUID",
                            error);
                }
                return new Target(Kind.SPIRIT, Optional.of(uuid));
            }
        }
        throw new IllegalArgumentException("Цель '" + raw + "' не распознана: допустимы hostile, animal, any, "
                + "nearest, player:<имя>, spirit:<uuid>");
    }

    private static Kind keywordOf(String text) {
        return switch (text.toLowerCase(Locale.ROOT)) {
            case "hostile" -> Kind.HOSTILE;
            case "animal" -> Kind.ANIMAL;
            case "any" -> Kind.ANY;
            case "nearest" -> Kind.NEAREST;
            default -> null;
        };
    }

    private static String namedValue(String prefix, String value) {
        if (value.isBlank()) {
            throw new IllegalArgumentException("У цели '" + prefix + ":' не указано имя: без имени не найти "
                    + "ни игрока, ни духа, а скрипт поехал бы к случайному существу");
        }
        return value;
    }
}
