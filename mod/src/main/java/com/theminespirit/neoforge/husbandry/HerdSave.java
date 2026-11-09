package com.theminespirit.neoforge.husbandry;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

final class HerdSave {
    private static final String VERSION_KEY = "v";
    private static final int VERSION = 1;
    private static final String DONE_KEY = "done";
    private static final String TARGET_KEY = "target";

    private HerdSave() {}

    static Map<String, Object> save(HerdSession session) {
        Map<String, Object> saved = new HashMap<>();
        saved.put(VERSION_KEY, VERSION);
        saved.put(DONE_KEY, session.done);
        saved.put("fed", session.fed);
        saved.put("babiesFed", session.babiesFed);
        saved.put("born", session.born);
        saved.put("sheared", session.sheared);
        saved.put("milked", session.milked);
        saved.put("eggsPicked", session.eggsPicked);
        saved.put("culled", session.culled);
        saved.put("escapedReturned", session.escapedReturned);
        saved.put("skipped", session.skipped);
        if (session.currentId != null
                && (session.state == HerdSession.State.APPROACH
                        || session.state == HerdSession.State.ACT
                        || session.state == HerdSession.State.COLLECT)) {
            saved.put(TARGET_KEY, session.currentId);
        }
        if (!session.handledIds.isEmpty()) {
            saved.put("handled", List.copyOf(session.handledIds));
        }
        return Map.copyOf(saved);
    }

    static void restore(HerdSession session, Map<String, Object> saved) {
        Objects.requireNonNull(saved, "Состояние не может быть null: пустое задают пустой картой");
        if (saved.get(VERSION_KEY) instanceof Number version && version.intValue() != VERSION) {
            throw new IllegalArgumentException("Версия состояния " + version + " не поддерживается: сессия стада "
                    + "читает только версию " + VERSION);
        }
        session.done = Math.max(session.done, number(saved, DONE_KEY));
        session.fed = Math.max(session.fed, number(saved, "fed"));
        session.babiesFed = Math.max(session.babiesFed, number(saved, "babiesFed"));
        session.born = Math.max(session.born, number(saved, "born"));
        session.sheared = Math.max(session.sheared, number(saved, "sheared"));
        session.milked = Math.max(session.milked, number(saved, "milked"));
        session.eggsPicked = Math.max(session.eggsPicked, number(saved, "eggsPicked"));
        session.culled = Math.max(session.culled, number(saved, "culled"));
        session.escapedReturned = Math.max(session.escapedReturned, number(saved, "escapedReturned"));
        session.skipped = Math.max(session.skipped, number(saved, "skipped"));
        if (saved.get(TARGET_KEY) instanceof String target && !target.isBlank()) {
            session.preferredTarget = target;
        }
        if (saved.get("handled") instanceof List<?> handled) {
            for (Object id : handled) {
                if (id instanceof String text && !text.isBlank()) {
                    session.handledIds.add(text);
                }
            }
        }
    }

    private static int number(Map<String, Object> saved, String key) {
        return saved.get(key) instanceof Number value ? Math.max(0, value.intValue()) : 0;
    }
}
