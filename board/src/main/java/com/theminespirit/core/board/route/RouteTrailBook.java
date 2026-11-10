package com.theminespirit.core.board.route;

import com.theminespirit.core.town.PlotLeveling;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class RouteTrailBook {
    private final Map<RouteResource, RouteTrail> trails = new EnumMap<>(RouteResource.class);
    private final Map<RouteResource, RouteTrail> planned = new EnumMap<>(RouteResource.class);
    private final List<RouteRetired> retired = new ArrayList<>();
    private long nextRetire;

    public void plan(RouteResource resource, PlotLeveling path) {
        Objects.requireNonNull(resource, "Ресурс тропы не может быть null: тропу прокладывают к маршруту");
        Objects.requireNonNull(path, "План тропы не может быть null: из него берут проложенные клетки");
        RouteTrail trail = RouteTrail.of(path);
        if (!trail.empty()) {
            planned.put(resource, trail);
        }
    }

    public void built(RouteResource resource) {
        RouteTrail trail = planned.remove(resource);
        if (trail != null) {
            trails.put(resource, trail);
        }
    }

    public void retire(RouteResource resource) {
        Objects.requireNonNull(resource, "Ресурс тропы не может быть null: снимают тропу маршрута");
        planned.remove(resource);
        RouteTrail trail = trails.remove(resource);
        if (trail != null && !trail.empty()) {
            retired.add(new RouteRetired(nextRetire++, resource, trail));
        }
    }

    public List<RouteRetired> retired() {
        return List.copyOf(retired);
    }

    public boolean done(long id) {
        return retired.removeIf(entry -> entry.id() == id);
    }

    public Map<RouteResource, RouteTrail> trails() {
        return Map.copyOf(trails);
    }

    public Map<RouteResource, RouteTrail> planned() {
        return Map.copyOf(planned);
    }

    public long nextRetire() {
        return nextRetire;
    }

    public void restore(RouteResource resource, RouteTrail trail, boolean builtTrail) {
        Objects.requireNonNull(resource, "Ресурс сохранённой тропы не может быть null: её некуда положить");
        Objects.requireNonNull(trail, "Сохранённая тропа не может быть null: пустую задают без записи");
        if (trail.empty()) {
            return;
        }
        (builtTrail ? trails : planned).put(resource, trail);
    }

    public void restore(RouteRetired entry) {
        Objects.requireNonNull(entry, "Сохранённое снятие не может быть null: без него очередь не собрать");
        retired.add(entry);
        nextRetire = Math.max(nextRetire, entry.id() + 1);
    }

    public void nextRetire(long value) {
        if (value < 0) {
            throw new IllegalArgumentException("Следующий номер снятия " + value + ": он не отрицателен");
        }
        nextRetire = Math.max(nextRetire, value);
    }

    public void clear() {
        trails.clear();
        planned.clear();
        retired.clear();
        nextRetire = 0;
    }
}
