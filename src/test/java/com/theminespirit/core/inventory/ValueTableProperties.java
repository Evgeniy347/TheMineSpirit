package com.theminespirit.core.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

class ValueTableProperties {
    private static final ValueTable VALUES = ValueTable.load();

    @Provide
    Arbitrary<String> itemIds() {
        Arbitrary<String> namespace = Arbitraries.strings().withCharRange('a', 'z').numeric().withChars('_', ':')
                .ofMinLength(1).ofMaxLength(8);
        Arbitrary<String> path = Arbitraries.strings().withCharRange('a', 'z').numeric().withChars('_', '.', '-')
                .ofMinLength(1).ofMaxLength(16);
        return Combinators.combine(namespace, path).as((n, p) -> n + ":" + p);
    }

    @Provide
    Arbitrary<String> anyText() {
        return Arbitraries.strings().withChars(' ', '*', '_', ':', 'Ж', 'A', '1', '\\', '/').ofMaxLength(24);
    }

    @Property
    void valueOf_AnyItemString_NeverThrowsAndStaysInScale(@ForAll("anyText") String item) {
        int value = VALUES.valueOf(item);

        assertTrue(value >= 0 && value <= ValueTable.VALUE_LIMIT,
                "Ценность любой строки id (" + value + " для '" + item + "') обязана лежать в шкале 0.."
                        + ValueTable.VALUE_LIMIT + ": значение вне шкалы сломало бы сравнение стопок при "
                        + "переполнении инвентаря, а исключение — подбор дропа");
    }

    @Property
    void categoryOf_AnyItemString_ReturnsKnownCategory(@ForAll("anyText") String item) {
        String category = VALUES.categoryOf(item);

        assertTrue(VALUES.categories().stream().anyMatch(known -> known.name().equals(category)),
                "Категория '" + category + "' предмета '" + item + "' обязана быть одной из категорий файла "
                        + "ценностей: неизвестное имя группы сломало бы раскладку сундука");
    }

    @Property
    void stackValue_Stack_EqualsUnitValueTimesCount(@ForAll("itemIds") String item, @ForAll int variant,
            @ForAll("counts") int count, @ForAll("maxStacks") int maxStack) {
        Stack stack = new Stack(item, Math.abs(variant % 4), count, maxStack);

        assertEquals(VALUES.valueOf(stack.item()) * stack.count(), VALUES.stackValue(stack),
                "Ценность стопки предмета '" + item + "' × " + stack.count() + " обязана равняться цене предмета "
                        + "на количество: сравнение «взять или выбросить» идёт по стопкам, а не по предметам");
    }

    @Provide
    Arbitrary<Integer> counts() {
        return Arbitraries.integers().between(1, 64);
    }

    @Provide
    Arbitrary<Integer> maxStacks() {
        return Arbitraries.integers().between(1, 64);
    }
}
