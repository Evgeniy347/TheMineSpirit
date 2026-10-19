package com.theminespirit.neoforge.policy;

import com.theminespirit.core.policy.ProtectedZoneParser;
import com.theminespirit.core.policy.ProtectedZones;
import java.util.List;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

public final class PolicyConfig {
    public static final String FILE_NAME = "theminespirit-policy.toml";

    private static final String EXAMPLE_ZONE = "minecraft:overworld 0 0 0 0 0 0";
    private static final ModConfigSpec SPEC;
    private static final ModConfigSpec.ConfigValue<List<? extends String>> PROTECTED_ZONES;

    private static List<String> parsedFrom = List.of();
    private static ProtectedZones parsed = ProtectedZones.of(List.of());

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.push("policy");
        PROTECTED_ZONES = builder.comment(
                        "Защищённые зоны: дух не ломает и не ставит блоки внутри. Строка: «измерение x1 y1 z1 x2 y2 z2», не больше 64 строк.",
                        "Пример: \"" + EXAMPLE_ZONE + "\". Ошибка в строке останавливает запуск сервера.")
                .defineListAllowEmpty(
                        "protectedZones", List.of(), () -> EXAMPLE_ZONE, entry -> entry instanceof String);
        builder.pop();
        SPEC = builder.build();
    }

    private PolicyConfig() {}

    public static ModConfigSpec spec() {
        return SPEC;
    }

    public static void register(ModContainer container, IEventBus modEventBus) {
        container.registerConfig(ModConfig.Type.SERVER, spec(), FILE_NAME);
        modEventBus.addListener(ModConfigEvent.Loading.class, PolicyConfig::onConfig);
        modEventBus.addListener(ModConfigEvent.Reloading.class, PolicyConfig::onConfig);
    }

    public static synchronized ProtectedZones zones() {
        List<String> raw = List.copyOf(PROTECTED_ZONES.get());
        if (!raw.equals(parsedFrom)) {
            parsed = ProtectedZoneParser.parse(raw);
            parsedFrom = raw;
        }
        return parsed;
    }

    private static void onConfig(ModConfigEvent event) {
        if (SPEC.equals(event.getConfig().getSpec())) {
            zones();
        }
    }
}
