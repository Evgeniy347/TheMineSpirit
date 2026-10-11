package com.theminespirit.core.tactics;

import java.util.List;
import java.util.stream.Collectors;

public record Condition(List<Atom> atoms) {
    public static final Condition ALWAYS = new Condition(List.of());

    public Condition {
        atoms = List.copyOf(atoms);
    }

    public boolean matches(FactMap facts) {
        for (Atom atom : atoms) {
            if (!atom.test(facts)) {
                return false;
            }
        }
        return true;
    }

    public List<FactName> facts() {
        return atoms.stream().map(Atom::fact).toList();
    }

    @Override
    public String toString() {
        return atoms.isEmpty() ? "всегда" : atoms.stream().map(Atom::toString).collect(Collectors.joining(" и "));
    }

    public sealed interface Atom permits NumAtom, BoolAtom, EnumAtom {
        FactName fact();

        boolean test(FactMap facts);
    }

    public record NumAtom(FactName fact, Comparison op, double value) implements Atom {
        @Override
        public boolean test(FactMap facts) {
            return op.compare(facts.number(fact), value);
        }

        @Override
        public String toString() {
            return fact.key() + " " + op.symbol() + " " + value;
        }
    }

    public record BoolAtom(FactName fact, Comparison op, boolean value) implements Atom {
        @Override
        public boolean test(FactMap facts) {
            boolean actual = facts.bool(fact);
            return op == Comparison.EQ ? actual == value : actual != value;
        }

        @Override
        public String toString() {
            return fact.key() + " " + op.symbol() + " " + value;
        }
    }

    public record EnumAtom(FactName fact, Comparison op, List<String> values) implements Atom {
        public EnumAtom {
            values = List.copyOf(values);
        }

        @Override
        public boolean test(FactMap facts) {
            boolean listed = values.contains(facts.text(fact));
            return op == Comparison.NE ? !listed : listed;
        }

        @Override
        public String toString() {
            return fact.key() + " " + op.symbol() + " " + values;
        }
    }
}
