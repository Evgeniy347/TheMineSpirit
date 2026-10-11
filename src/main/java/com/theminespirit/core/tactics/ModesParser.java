package com.theminespirit.core.tactics;

import java.util.EnumMap;
import java.util.Map;

final class ModesParser {
    private static final String[] MODE_FIELDS = {"initiates", "radiusBase", "engageRadius", "pursuitRadius", "anchor",
            "leash", "returnToAnchor"};

    private ModesParser() {
    }

    static Modes parse(ConfigNode root) {
        root.fields(new String[] {"returnTolerance", "modes"});
        Map<CombatMode, ModeParams> params = new EnumMap<>(CombatMode.class);
        ConfigNode modes = root.get("modes");
        for (Map.Entry<String, ConfigNode> entry : modes.entries().entrySet()) {
            CombatMode mode = entry.getValue().enumValueOf(CombatMode.class, entry.getKey(), "режим боя");
            if (params.put(mode, modeParams(entry.getValue().fields(MODE_FIELDS))) != null) {
                throw entry.getValue().fail("режим " + mode.id() + " описан дважды", "ключи регистронезависимы: уберите дубль");
            }
        }
        for (CombatMode mode : CombatMode.values()) {
            if (!params.containsKey(mode)) {
                throw modes.fail("не описан режим " + mode.id(), "в modes.json описаны все режимы боя без пропусков");
            }
        }
        return root.build(() -> new Modes(params, root.get("returnTolerance").number()));
    }

    private static ModeParams modeParams(ConfigNode node) {
        return node.build(() -> new ModeParams(node.get("initiates").bool(),
                node.get("radiusBase").enumValue(RadiusBase.class),
                node.get("engageRadius").number(),
                node.get("pursuitRadius").number(),
                node.get("anchor").enumValue(AnchorKind.class),
                node.get("leash").bool(),
                node.get("returnToAnchor").bool()));
    }
}
