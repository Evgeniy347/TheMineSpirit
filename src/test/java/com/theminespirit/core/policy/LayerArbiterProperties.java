package com.theminespirit.core.policy;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;

class LayerArbiterProperties {
    private static final String ALPHABET = "rstix";

    private static String randomScript(int seed) {
        Random random = new Random(seed);
        StringBuilder script = new StringBuilder();
        int length = 40 + random.nextInt(60);
        boolean calmStretch = false;
        for (int i = 0; i < length; i++) {
            if (!calmStretch && random.nextInt(8) == 0) {
                calmStretch = true;
            }
            char step = calmStretch && random.nextInt(4) > 0 ? 's' : ALPHABET.charAt(random.nextInt(ALPHABET.length()));
            script.append(step);
            if (random.nextInt(6) == 0) {
                calmStretch = false;
            }
        }
        return script.toString();
    }

    private static boolean demandsPolicy(char step) {
        return step == 'r' || step == 'x';
    }

    @Property(tries = 200)
    void step_ReflexTick_PolicyAlwaysOwns(@ForAll @IntRange(min = 0, max = 1_000_000) int seed) {
        String script = randomScript(seed);
        String owners = LayerArbiterTests.run(script);

        for (int i = 0; i < script.length(); i++) {
            if (demandsPolicy(script.charAt(i))) {
                assertTrue(owners.charAt(i) == 'P', "В тик с рефлексом владеет политика всегда: " + script + " → " + owners + ", шаг " + i);
            }
        }
    }

    @Property(tries = 200)
    void step_OwnerLeavesPolicy_OnlyAfterTenQuietTicks(@ForAll @IntRange(min = 0, max = 1_000_000) int seed) {
        String script = randomScript(seed);
        String owners = LayerArbiterTests.run(script);

        for (int i = 1; i < script.length(); i++) {
            if (owners.charAt(i - 1) == 'P' && owners.charAt(i) != 'P') {
                assertTrue(i >= 9, "Политика не уступает раньше десяти тиков: " + script + " → " + owners + ", шаг " + i);
                for (int back = i - 9; back <= i; back++) {
                    assertTrue(!demandsPolicy(script.charAt(back)),
                            "Перед уходом политики 10 тиков подряд без рефлекса: " + script + " → " + owners + ", шаг " + i);
                }
            }
        }
    }

    @Property(tries = 200)
    void step_OwnerLeavesTactics_OnlyAfterTenTicksWithoutDemand(@ForAll @IntRange(min = 0, max = 1_000_000) int seed) {
        String script = randomScript(seed);
        String owners = LayerArbiterTests.run(script);

        for (int i = 1; i < script.length(); i++) {
            if (owners.charAt(i - 1) == 'T' && owners.charAt(i) != 'T' && owners.charAt(i) != 'P') {
                assertTrue(i >= 9, "Тактика не уступает стратегии раньше десяти тиков: " + script + " → " + owners + ", шаг " + i);
                for (int back = i - 9; back <= i; back++) {
                    assertTrue(script.charAt(back) != 't', "Перед уходом тактики 10 тиков подряд без боя: " + script + " → " + owners + ", шаг " + i);
                }
            }
        }
    }
}
