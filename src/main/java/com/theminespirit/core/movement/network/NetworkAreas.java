package com.theminespirit.core.movement.network;

import com.theminespirit.core.building.BuildBox;
import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Участки сети: габарит, где отрезки построены одним обходом мира, и когда их строить заново. Участок строится один
 * раз; заново — только если потерял отрезок (проход заложили) или маршрут через него не нашёлся, и не чаще
 * {@link #REBUILD_TICKS}. Площадка стройки (QUESTIONS-3, ответ 2) — участок с началом сети во входе, как будто дверь
 * уже стоит; пересчитывается целиком не чаще {@link #REBUILD_TICKS}, если с прошлого раза поставлен блок.
 */
public final class NetworkAreas {
    // 3 с: при демо-темпе стройки 10 блоков/с это ~30 блоков, дух за это время проходит ~12 клеток — сеть отстаёт
    // не больше чем на отрезок, а отрезок, который не прошёлся, пересчитывается и без этого.
    public static final long REBUILD_TICKS = 60;
    // Сторона участка: поселение 160×160 (решение владельца 19.10) обходится участками не больше четверти площади.
    public static final int MAX_SIDE = 96;
    static final int MARGIN_XZ = 16;
    static final int MARGIN_Y = 10;
    static final int GRID = 16;

    private final Map<Integer, Area> areas = new TreeMap<>();
    private final Map<Integer, Integer> owner = new HashMap<>();
    private int nextId;

    // Участки, которые нужно построить сейчас, чтобы маршрут от ног к цели шёл по сети; пусто — строить нечего.
    public List<Integer> prepare(BlockCoord feet, BlockCoord target, long now) {
        Objects.requireNonNull(feet, "Ноги духа не могут быть null: по ним выбирается участок сети");
        Objects.requireNonNull(target, "Цель не может быть null: по ней выбирается участок сети");
        List<Integer> build = new ArrayList<>();
        Optional<Area> covering = covering(feet, target);
        if (covering.isEmpty()) {
            Optional<BuildBox> need = need(feet, target);
            if (need.isEmpty()) {
                return List.of();
            }
            build.add(plainArea(need.get()));
        }
        for (Area area : areas.values()) {
            boolean near = area.box.contains(feet) || area.box.contains(target);
            if (near && !area.swallowed && area.due(now) && !build.contains(area.id)) {
                build.add(area.id);
            }
        }
        return build;
    }

    public int beginSite(BuildBox box, List<BlockCoord> entrances) {
        Objects.requireNonNull(box, "Габарит площадки не может быть null: в нём строится сеть стройки");
        Objects.requireNonNull(entrances, "Входы площадки не могут быть null: с них начинается сеть стройки");
        Area area = new Area(nextId++, box, entrances, true);
        areas.put(area.id, area);
        return area.id;
    }

    public void endSite(int id) {
        Area area = require(id);
        Area plain = new Area(id, area.box, area.entrances, false).adopt(area);
        plain.stale = true;
        areas.put(id, plain);
    }

    public void blockPlaced(BlockCoord block) {
        for (Area area : areas.values()) {
            if (area.site && area.box.contains(block)) {
                area.placed = true;
            }
        }
    }

    public void segmentLost(int segment) {
        Integer id = owner.remove(segment);
        Area area = id == null ? null : areas.get(id);
        if (area != null) {
            area.stale = true;
        }
    }

    public void routeFailed(BlockCoord feet, BlockCoord target) {
        for (Area area : areas.values()) {
            if (area.box.contains(feet) && area.box.contains(target)) {
                area.stale = true;
            }
        }
    }

    public BuildBox box(int id) {
        return require(id).box;
    }

    // Начала сети участка: входы его площадки и входы площадок стройки внутри него (двери, которых ещё нет).
    public List<BlockCoord> entrances(int id) {
        Area area = require(id);
        List<BlockCoord> entrances = new ArrayList<>(area.entrances);
        for (Area other : areas.values()) {
            if (other.id != id && other.site) {
                other.entrances.stream().filter(area.box::contains).forEach(entrances::add);
            }
        }
        return List.copyOf(entrances);
    }

    public boolean exists(int id) {
        return areas.containsKey(id);
    }

    public Set<Integer> segments(int id) {
        return Set.copyOf(require(id).segments);
    }

    // Отрезки заменённых участков — к удалению из сети; новые записаны за участком.
    public Set<Integer> rebuilt(int id, Set<Integer> segments, long now) {
        Area area = require(id);
        Set<Integer> old = new TreeSet<>(area.segments);
        old.forEach(owner::remove);
        area.segments.clear();
        area.segments.addAll(segments);
        segments.forEach(segment -> owner.put(segment, id));
        area.builtAt = now;
        area.built = true;
        area.stale = false;
        area.placed = false;
        return old;
    }

    // Участки, целиком поглощённые новым; их отрезки — к удалению.
    public Set<Integer> takeSwallowed() {
        Set<Integer> dropped = new TreeSet<>();
        for (Area area : List.copyOf(areas.values())) {
            if (area.swallowed) {
                dropped.addAll(area.segments);
                area.segments.forEach(owner::remove);
                areas.remove(area.id);
            }
        }
        return dropped;
    }

    public int size() {
        return areas.size();
    }

    private Optional<Area> covering(BlockCoord feet, BlockCoord target) {
        for (Area area : areas.values()) {
            if (area.box.contains(feet) && area.box.contains(target)) {
                return Optional.of(area);
            }
        }
        return Optional.empty();
    }

    private int plainArea(BuildBox need) {
        BuildBox box = need;
        for (Area area : areas.values()) {
            if (!area.site && intersects(area.box, box)) {
                BuildBox union = union(area.box, box);
                if (side(union) <= MAX_SIDE) {
                    box = union;
                }
            }
        }
        for (Area area : areas.values()) {
            if (!area.site && contains(box, area.box)) {
                area.swallowed = true;
            }
        }
        Area created = new Area(nextId++, box, List.of(), false);
        areas.put(created.id, created);
        return created.id;
    }

    static Optional<BuildBox> need(BlockCoord feet, BlockCoord target) {
        int minX = floorGrid(Math.min(feet.x(), target.x()) - MARGIN_XZ);
        int minZ = floorGrid(Math.min(feet.z(), target.z()) - MARGIN_XZ);
        int maxX = floorGrid(Math.max(feet.x(), target.x()) + MARGIN_XZ) + GRID - 1;
        int maxZ = floorGrid(Math.max(feet.z(), target.z()) + MARGIN_XZ) + GRID - 1;
        BuildBox box = BuildBox.of(
                minX,
                Math.min(feet.y(), target.y()) - MARGIN_Y,
                minZ,
                maxX,
                Math.max(feet.y(), target.y()) + MARGIN_Y,
                maxZ);
        return side(box) <= MAX_SIDE ? Optional.of(box) : Optional.empty();
    }

    private static int floorGrid(int value) {
        return Math.floorDiv(value, GRID) * GRID;
    }

    private static int side(BuildBox box) {
        return Math.max(box.sideX(), box.sideZ());
    }

    private static boolean intersects(BuildBox a, BuildBox b) {
        return a.minX() <= b.maxX()
                && b.minX() <= a.maxX()
                && a.minY() <= b.maxY()
                && b.minY() <= a.maxY()
                && a.minZ() <= b.maxZ()
                && b.minZ() <= a.maxZ();
    }

    private static boolean contains(BuildBox outer, BuildBox inner) {
        return outer.minX() <= inner.minX()
                && outer.minY() <= inner.minY()
                && outer.minZ() <= inner.minZ()
                && outer.maxX() >= inner.maxX()
                && outer.maxY() >= inner.maxY()
                && outer.maxZ() >= inner.maxZ();
    }

    private static BuildBox union(BuildBox a, BuildBox b) {
        return BuildBox.of(
                Math.min(a.minX(), b.minX()),
                Math.min(a.minY(), b.minY()),
                Math.min(a.minZ(), b.minZ()),
                Math.max(a.maxX(), b.maxX()),
                Math.max(a.maxY(), b.maxY()),
                Math.max(a.maxZ(), b.maxZ()));
    }

    private Area require(int id) {
        Area area = areas.get(id);
        if (area == null) {
            throw new IllegalArgumentException("участка сети " + id + " нет: его поглотил соседний или не заводили");
        }
        return area;
    }

    private static final class Area {
        final int id;
        final BuildBox box;
        final List<BlockCoord> entrances;
        final boolean site;
        final Set<Integer> segments = new TreeSet<>();
        long builtAt;
        boolean built;
        boolean stale;
        boolean placed;
        boolean swallowed;

        Area(int id, BuildBox box, List<BlockCoord> entrances, boolean site) {
            this.id = id;
            this.box = box;
            this.entrances = List.copyOf(entrances);
            this.site = site;
        }

        Area adopt(Area other) {
            segments.addAll(other.segments);
            builtAt = other.builtAt;
            built = other.built;
            return this;
        }

        boolean due(long now) {
            if (!built) {
                return true;
            }
            boolean changed = stale || (site && placed);
            return changed && now - builtAt >= REBUILD_TICKS;
        }
    }
}
