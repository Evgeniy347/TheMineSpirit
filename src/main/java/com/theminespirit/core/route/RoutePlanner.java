package com.theminespirit.core.route;

import com.theminespirit.core.movement.Goal;
import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class RoutePlanner {
    private static final double MIN_IMPROVEMENT = 0.01;
    private static final int CLOSED = -2;
    private static final int UNSEEN = -1;
    private static final int GROWTH = 2;

    private final RouteCells cells;
    private final RouteMoves moves;
    private final RouteRules rules;
    private final Goal goal;
    private final LongIntTable index;
    private int[] xs;
    private int[] ys;
    private int[] zs;
    private double[] costs;
    private double[] scores;
    private double[] estimates;
    private int[] parents;
    private byte[] arrivals;
    private int[] placesLeft;
    private int[] heapSlots;
    private int[] heap;
    private int heapSize;
    private int nodes;

    private RoutePlanner(RouteWorld world, Goal goal, RouteRules rules) {
        this.cells = new RouteCells(world);
        this.moves = new RouteMoves(cells, rules);
        this.rules = rules;
        this.goal = goal;
        int capacity = Math.min(rules.maxNodes(), 1 << 12);
        this.index = new LongIntTable(capacity);
        allocate(capacity);
    }

    public static Route plan(RouteWorld world, BlockCoord start, Goal goal, RouteRules rules, int blocks) {
        Objects.requireNonNull(world, "Мир маршрута не может быть null: без клеток путь не построить");
        Objects.requireNonNull(start, "Старт не может быть null: без него неизвестно, откуда идти");
        Objects.requireNonNull(goal, "Цель не может быть null: без неё неизвестно, куда идти");
        Objects.requireNonNull(rules, "Правила маршрута не могут быть null: без цен ходов A* не работает");
        if (blocks < 0) {
            throw new IllegalArgumentException("Запас блоков " + blocks + " отрицателен: считайте блоки в инвентаре");
        }
        return new RoutePlanner(world, goal, rules).search(start, blocks);
    }

    private Route search(BlockCoord start, int blocks) {
        int first = node(start.x(), start.y(), start.z());
        costs[first] = 0.0;
        placesLeft[first] = blocks;
        estimates[first] = goal.heuristic(start);
        push(first, weighted(estimates[first]));
        int best = first;
        while (heapSize > 0) {
            int current = pop();
            BlockCoord at = coord(current);
            if (goal.isSatisfied(at)) {
                return route(Route.Status.FOUND, current);
            }
            if (estimates[current] < estimates[best]) {
                best = current;
            }
            if (nodes >= rules.maxNodes()) {
                break;
            }
            expand(current);
        }
        boolean progress = estimates[first] - estimates[best] >= rules.minPartial();
        return progress
                ? route(Route.Status.PARTIAL, best)
                : new Route(
                        Route.Status.NONE,
                        List.of(),
                        0.0,
                        start,
                        nodes,
                        cells.reads(),
                        moves.vetoCount(),
                        moves.vetoes());
    }

    private void expand(int current) {
        for (int move = 0; move < RouteMoves.COUNT; move++) {
            double step = moves.cost(move, xs[current], ys[current], zs[current], placesLeft[current], null);
            if (step == RouteMoves.IMPOSSIBLE) {
                continue;
            }
            double reached = costs[current] + step;
            long key = LongIntTable.key(moves.toX(), moves.toY(), moves.toZ());
            int next = index.get(key);
            if (next == LongIntTable.ABSENT) {
                if (nodes >= rules.maxNodes()) {
                    continue;
                }
                next = node(moves.toX(), moves.toY(), moves.toZ());
                estimates[next] = goal.heuristic(coord(next));
            } else if (reached >= costs[next] - MIN_IMPROVEMENT) {
                continue;
            }
            costs[next] = reached;
            parents[next] = current;
            arrivals[next] = (byte) move;
            placesLeft[next] = placesLeft[current] - moves.placed();
            push(next, reached + weighted(estimates[next]));
        }
    }

    private double weighted(double blocks) {
        return blocks * rules.walkTicks() * rules.heuristicWeight();
    }

    private Route route(Route.Status status, int last) {
        List<Integer> chain = new ArrayList<>();
        for (int at = last; parents[at] != UNSEEN; at = parents[at]) {
            chain.add(at);
        }
        Collections.reverse(chain);
        List<RouteStep> steps = new ArrayList<>();
        for (int at : chain) {
            int from = parents[at];
            moves.cost(arrivals[at], xs[from], ys[from], zs[from], placesLeft[from], steps);
        }
        return new Route(
                status, steps, costs[last], coord(last), nodes, cells.reads(), moves.vetoCount(), moves.vetoes());
    }

    private BlockCoord coord(int node) {
        return new BlockCoord(xs[node], ys[node], zs[node]);
    }

    private int node(int x, int y, int z) {
        if (nodes == xs.length) {
            allocate(xs.length * GROWTH);
        }
        int created = nodes++;
        xs[created] = x;
        ys[created] = y;
        zs[created] = z;
        costs[created] = Double.POSITIVE_INFINITY;
        parents[created] = UNSEEN;
        heapSlots[created] = UNSEEN;
        index.put(LongIntTable.key(x, y, z), created);
        return created;
    }

    private void allocate(int capacity) {
        int old = xs == null ? 0 : xs.length;
        xs = grow(xs, capacity, old);
        ys = grow(ys, capacity, old);
        zs = grow(zs, capacity, old);
        parents = grow(parents, capacity, old);
        placesLeft = grow(placesLeft, capacity, old);
        heapSlots = grow(heapSlots, capacity, old);
        heap = grow(heap, capacity, old);
        costs = grow(costs, capacity, old);
        scores = grow(scores, capacity, old);
        estimates = grow(estimates, capacity, old);
        byte[] moved = new byte[capacity];
        if (arrivals != null) {
            System.arraycopy(arrivals, 0, moved, 0, old);
        }
        arrivals = moved;
    }

    private static int[] grow(int[] array, int capacity, int old) {
        int[] grown = new int[capacity];
        if (array != null) {
            System.arraycopy(array, 0, grown, 0, old);
        }
        return grown;
    }

    private static double[] grow(double[] array, int capacity, int old) {
        double[] grown = new double[capacity];
        if (array != null) {
            System.arraycopy(array, 0, grown, 0, old);
        }
        return grown;
    }

    private void push(int node, double score) {
        scores[node] = score;
        int slot = heapSlots[node];
        if (slot < 0) {
            slot = heapSize++;
            heap[slot] = node;
            heapSlots[node] = slot;
        }
        siftUp(slot);
    }

    private int pop() {
        int top = heap[0];
        heapSlots[top] = CLOSED;
        heapSize--;
        if (heapSize > 0) {
            heap[0] = heap[heapSize];
            heapSlots[heap[0]] = 0;
            siftDown(0);
        }
        return top;
    }

    private void siftUp(int start) {
        int slot = start;
        int node = heap[slot];
        while (slot > 0) {
            int parent = (slot - 1) >>> 1;
            if (scores[heap[parent]] <= scores[node]) {
                break;
            }
            place(heap[parent], slot);
            slot = parent;
        }
        place(node, slot);
    }

    private void siftDown(int start) {
        int slot = start;
        int node = heap[slot];
        while (true) {
            int child = slot * 2 + 1;
            if (child >= heapSize) {
                break;
            }
            if (child + 1 < heapSize && scores[heap[child + 1]] < scores[heap[child]]) {
                child++;
            }
            if (scores[heap[child]] >= scores[node]) {
                break;
            }
            place(heap[child], slot);
            slot = child;
        }
        place(node, slot);
    }

    private void place(int node, int slot) {
        heap[slot] = node;
        heapSlots[node] = slot;
    }
}
