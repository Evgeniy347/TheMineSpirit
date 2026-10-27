package com.theminespirit.core.board.graph;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class GraphFlow {
    public static final int MAX_ITERATIONS = 200;
    public static final double EPSILON = 1e-6;

    public record Link(String from, String to, double factor) {}

    private final Map<String, Integer> index = new LinkedHashMap<>();
    private final List<Link> links = new ArrayList<>();
    private final Map<String, Double> roots = new LinkedHashMap<>();
    private final Map<String, Double> locals = new LinkedHashMap<>();

    public int node(String key) {
        Objects.requireNonNull(key, "Узел потока не может быть null: у каждого узла графа есть ключ");
        return index.computeIfAbsent(key, ignored -> index.size());
    }

    public void root(String key, double value) {
        node(key);
        roots.merge(key, value, Double::sum);
    }

    public void link(String from, String to, double factor) {
        node(from);
        node(to);
        links.add(new Link(from, to, factor));
    }

    public void local(String key, double value) {
        node(key);
        locals.merge(key, value, Double::sum);
    }

    public List<String> keys() {
        return List.copyOf(index.keySet());
    }

    public List<Link> links() {
        return List.copyOf(links);
    }

    public Map<String, Double> roots() {
        return Map.copyOf(roots);
    }

    public Map<String, Double> solve() {
        int size = index.size();
        double[] base = new double[size];
        roots.forEach((key, value) -> base[node(key)] += value);
        int[] from = new int[links.size()];
        int[] to = new int[links.size()];
        double[] factor = new double[links.size()];
        for (int link = 0; link < links.size(); link++) {
            from[link] = node(links.get(link).from());
            to[link] = node(links.get(link).to());
            factor[link] = links.get(link).factor();
        }
        double[] value = base.clone();
        for (int iteration = 0; iteration < MAX_ITERATIONS; iteration++) {
            double[] next = base.clone();
            for (int link = 0; link < from.length; link++) {
                next[to[link]] += factor[link] * value[from[link]];
            }
            double delta = 0;
            for (int node = 0; node < size; node++) {
                delta = Math.max(delta, Math.abs(next[node] - value[node]));
            }
            value = next;
            if (delta < EPSILON) {
                break;
            }
        }
        Map<String, Double> result = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : index.entrySet()) {
            result.put(entry.getKey(), value[entry.getValue()] + locals.getOrDefault(entry.getKey(), 0.0));
        }
        return result;
    }

    public static int priority(double score, int max) {
        if (score <= 0) {
            return 0;
        }
        return (int) Math.min(max, Math.round(max * (1 - Math.exp(-score / max))));
    }
}
