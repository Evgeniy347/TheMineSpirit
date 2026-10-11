package com.theminespirit.core.plan;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.Random;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;

/**
 * Генератор случайных JSON-деревьев для свойств парсера плана (jqwik).
 *
 * <p>Случайная строка (как {@code @ForAll String}) почти никогда не является JSON, поэтому проверка
 * «парсер на любом JSON бросает только {@link PlanValidationException}» ничего не проверяла: до
 * валидатора доходили лишь синтаксические ошибки Jackson. Генератор строит деревья с ключами плана
 * ({@code type/script/params/steps/times/mode/check/then/else}) и значениями любых типов — null,
 * числа, строки, массивы, вложенные объекты, — чтобы свойство дошло до всех ветвей парсера.
 */
final class JsonTrees {
    private static final List<String> KEYS = List.of(
            "type", "script", "params", "steps", "times", "mode", "check", "then", "else",
            "children", "condition", "count", "species", "ticks", "radius", "x", "y", "z", "id", "name");
    private static final List<String> TEXTS = List.of(
            "script", "sequence", "repeat", "fallback", "parallel", "condition",
            "wait", "goto", "follow", "return_home", "chop_tree", "all", "any", "");
    private static final int MAX_DEPTH = 4;

    private JsonTrees() {
    }

    static Arbitrary<JsonNode> any() {
        return Arbitraries.randomValue(random -> build(random, 0));
    }

    private static JsonNode build(Random random, int depth) {
        boolean leaf = depth >= MAX_DEPTH;
        int kind = random.nextInt(leaf ? 5 : 7);
        JsonNodeFactory factory = JsonNodeFactory.instance;
        return switch (kind) {
            case 0 -> factory.textNode(TEXTS.get(random.nextInt(TEXTS.size())));
            case 1 -> factory.numberNode(random.nextInt(8000) - 4000);
            case 2 -> factory.numberNode(random.nextLong());
            case 3 -> factory.numberNode(random.nextDouble() * 200 - 100);
            case 4 -> leaf ? factory.booleanNode(random.nextBoolean()) : factory.nullNode();
            case 5 -> array(random, depth);
            default -> object(random, depth);
        };
    }

    private static ArrayNode array(Random random, int depth) {
        JsonNodeFactory factory = JsonNodeFactory.instance;
        ArrayNode array = factory.arrayNode();
        int size = random.nextInt(4);
        for (int i = 0; i < size; i++) {
            array.add(build(random, depth + 1));
        }
        return array;
    }

    private static ObjectNode object(Random random, int depth) {
        JsonNodeFactory factory = JsonNodeFactory.instance;
        ObjectNode object = factory.objectNode();
        int size = 1 + random.nextInt(4);
        for (int i = 0; i < size; i++) {
            String key = KEYS.get(random.nextInt(KEYS.size()));
            JsonNode value = random.nextBoolean()
                    ? build(random, depth + 1)
                    : factory.arrayNode();
            object.set(key, value);
        }
        return object;
    }
}
