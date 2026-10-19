package com.theminespirit.core.tactics;

import com.theminespirit.core.tactics.Condition.Atom;
import com.theminespirit.core.tactics.Condition.BoolAtom;
import com.theminespirit.core.tactics.Condition.EnumAtom;
import com.theminespirit.core.tactics.Condition.NumAtom;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

final class ConditionParser {
    private static final String[] ATOM_FIELDS = {"fact", "op", "value"};
    private static final Set<Comparison> BOOL_OPS = Set.of(Comparison.EQ, Comparison.NE);
    private static final Set<Comparison> ENUM_OPS = Set.of(Comparison.EQ, Comparison.NE, Comparison.IN);

    private ConditionParser() {
    }

    static Condition parse(ConfigNode array) {
        List<Atom> atoms = new ArrayList<>();
        for (ConfigNode item : array.items()) {
            atoms.add(atom(item.fields(ATOM_FIELDS)));
        }
        return new Condition(atoms);
    }

    private static Atom atom(ConfigNode node) {
        FactName fact = fact(node.get("fact"));
        ConfigNode opNode = node.get("op");
        Comparison op = Comparison.bySymbol(opNode.text()).orElseThrow(() -> opNode.fail(
                "неизвестный оператор '" + opNode.text() + "'", "допустимы < <= > >= == != in"));
        ConfigNode value = node.get("value");
        return switch (fact.type()) {
            case NUM -> numAtom(fact, op, opNode, value);
            case BOOL -> boolAtom(fact, op, opNode, value);
            case ENUM -> enumAtom(fact, op, opNode, value);
        };
    }

    private static FactName fact(ConfigNode node) {
        String key = node.text();
        return FactName.byKey(key).orElseThrow(() -> node.fail("неизвестный факт '" + key + "'",
                "имя берётся из каталога фактов (docs/tactics/README.md), возможна опечатка"));
    }

    private static Atom numAtom(FactName fact, Comparison op, ConfigNode opNode, ConfigNode value) {
        if (op == Comparison.IN) {
            throw opNode.fail("оператор in не применим к числовому факту " + fact.key(),
                    "для чисел используйте < <= > >= == !=");
        }
        return new NumAtom(fact, op, value.number(-1.0E9, 1.0E9));
    }

    private static Atom boolAtom(FactName fact, Comparison op, ConfigNode opNode, ConfigNode value) {
        if (!BOOL_OPS.contains(op)) {
            throw opNode.fail("оператор " + op.symbol() + " не применим к булеву факту " + fact.key(),
                    "для булевых фактов допустимы только == и !=");
        }
        return new BoolAtom(fact, op, value.bool());
    }

    private static Atom enumAtom(FactName fact, Comparison op, ConfigNode opNode, ConfigNode value) {
        if (!ENUM_OPS.contains(op)) {
            throw opNode.fail("оператор " + op.symbol() + " не применим к перечислению " + fact.key(),
                    "для перечислений допустимы ==, != и in");
        }
        List<String> values = new ArrayList<>();
        if (op == Comparison.IN) {
            value.nonEmptyItems().forEach(item -> values.add(enumValue(fact, item)));
        } else {
            values.add(enumValue(fact, value));
        }
        return new EnumAtom(fact, op, values);
    }

    private static String enumValue(FactName fact, ConfigNode node) {
        String text = node.text();
        if (!fact.enumValues().contains(text)) {
            throw node.fail("значение '" + text + "' не из перечисления " + fact.key(),
                    "допустимы " + fact.enumValues() + ", опечатка в имени");
        }
        return text;
    }
}
