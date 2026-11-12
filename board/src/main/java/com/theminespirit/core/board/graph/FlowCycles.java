package com.theminespirit.core.board.graph;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.stream.IntStream;

final class FlowCycles {
    private static final int FRESH = 0;
    private static final int OPEN = 1;
    private static final int DONE = 2;

    private FlowCycles() {}

    static boolean[] backLinks(int size, int[] from, int[] to, int[] roots, int[] rank) {
        List<List<Integer>> out = new ArrayList<>();
        for (int node = 0; node < size; node++) {
            out.add(new ArrayList<>());
        }
        for (int link = 0; link < from.length; link++) {
            out.get(from[link]).add(link);
        }
        for (List<Integer> links : out) {
            links.sort(Comparator.comparingInt(link -> rank[to[link]]));
        }
        boolean[] back = new boolean[from.length];
        int[] state = new int[size];
        for (int root : IntStream.of(roots)
                .boxed()
                .sorted(Comparator.comparingInt(node -> rank[node]))
                .mapToInt(Integer::intValue)
                .toArray()) {
            walk(root, out, to, state, back);
        }
        for (int node : IntStream.range(0, size)
                .boxed()
                .sorted(Comparator.comparingInt(node -> rank[node]))
                .mapToInt(Integer::intValue)
                .toArray()) {
            walk(node, out, to, state, back);
        }
        return back;
    }

    private static void walk(int start, List<List<Integer>> out, int[] to, int[] state, boolean[] back) {
        if (state[start] != FRESH) {
            return;
        }
        Deque<int[]> stack = new ArrayDeque<>();
        state[start] = OPEN;
        stack.push(new int[] {start, 0});
        while (!stack.isEmpty()) {
            int[] frame = stack.peek();
            List<Integer> links = out.get(frame[0]);
            if (frame[1] == links.size()) {
                state[frame[0]] = DONE;
                stack.pop();
                continue;
            }
            int link = links.get(frame[1]++);
            int next = to[link];
            if (state[next] == OPEN) {
                back[link] = true;
            } else if (state[next] == FRESH) {
                state[next] = OPEN;
                stack.push(new int[] {next, 0});
            }
        }
    }
}
