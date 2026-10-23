package com.theminespirit.neoforge.board;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Крафтер, которому доска уже заказала передачу ингредиентов. Без памяти доска каждый цикл брала ближайшего
 * свободного духа: отдавший булыжник оказывался ближе, становился крафтером и заказывал передачу обратно —
 * {@code transfer:first:craft_furnace:cobblestone} шла по кругу до конца приёмки (простой 48590 тиков, 24.10).
 */
final class CrafterMemory {
    private final Map<String, UUID> crafters = new HashMap<>();

    void remember(String ticketId, UUID crafter) {
        crafters.put(ticketId, crafter);
    }

    void forget(String ticketId) {
        crafters.remove(ticketId);
    }

    void retain(Predicate<String> alive) {
        crafters.keySet().removeIf(alive.negate());
    }

    Optional<UUID> remembered(String ticketId) {
        return Optional.ofNullable(crafters.get(ticketId));
    }

    int size() {
        return crafters.size();
    }

    <S> S choose(String ticketId, List<S> spirits, Function<S, UUID> id, Predicate<S> free, Supplier<S> fallback) {
        UUID remembered = crafters.get(ticketId);
        if (remembered != null) {
            Optional<S> crafter = spirits.stream()
                    .filter(spirit -> remembered.equals(id.apply(spirit)))
                    .findFirst();
            if (crafter.isPresent() && free.test(crafter.orElseThrow())) {
                return crafter.orElseThrow();
            }
        }
        return fallback.get();
    }
}
