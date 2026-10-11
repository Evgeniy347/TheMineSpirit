package com.theminespirit.core.tactics;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

final class EnemyProfilesParser {
    private static final Pattern TYPE_KEY = Pattern.compile("minecraft:[a-z_]+");
    private static final String[] REQUIRED = {"type", "hp", "dps", "speed", "ranged", "explosive", "swims", "flies",
            "tier"};

    private EnemyProfilesParser() {
    }

    static EnemyProfiles parse(ConfigNode root) {
        ConfigNode list = root.fields(new String[] {"enemies"}).get("enemies");
        Map<String, EnemyProfile> byType = new HashMap<>();
        EnemyProfile fallback = null;
        for (ConfigNode item : list.nonEmptyItems()) {
            EnemyProfile profile = profile(item.fields(REQUIRED));
            if (byType.containsKey(profile.type()) || (fallback != null && isDefault(profile))) {
                throw item.get("type").fail("тип '" + profile.type() + "' описан дважды",
                        "каждый тип врага описывается ровно один раз");
            }
            if (isDefault(profile)) {
                fallback = profile;
            } else {
                byType.put(profile.type(), profile);
            }
        }
        if (fallback == null) {
            throw list.fail("нет профиля '" + EnemyProfiles.DEFAULT_KEY + "'",
                    "консервативный профиль для неизвестного типа обязателен: без него новый враг не оценить");
        }
        return new EnemyProfiles(byType, fallback);
    }

    private static boolean isDefault(EnemyProfile profile) {
        return profile.type().equals(EnemyProfiles.DEFAULT_KEY);
    }

    private static EnemyProfile profile(ConfigNode item) {
        ConfigNode type = item.get("type");
        String key = type.text();
        if (!key.equals(EnemyProfiles.DEFAULT_KEY) && !TYPE_KEY.matcher(key).matches()) {
            throw type.fail("ключ '" + key + "' не подходит под шаблон minecraft:[a-z_]+ или default",
                    "тип врага — id сущности вида minecraft:zombie");
        }
        return new EnemyProfile(key,
                item.get("hp").number(1, EnemyProfile.MAX_HP),
                item.get("dps").number(0, EnemyProfile.MAX_DPS),
                item.get("speed").number(0.01, EnemyProfile.MAX_SPEED),
                item.get("ranged").bool(),
                item.get("explosive").bool(),
                item.get("swims").bool(),
                item.get("flies").bool(),
                item.get("tier").integer(0, EnemyProfile.MAX_TIER));
    }
}
