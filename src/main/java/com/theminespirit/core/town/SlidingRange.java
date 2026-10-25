package com.theminespirit.core.town;

import java.util.ArrayDeque;
import java.util.Deque;

final class SlidingRange {
    private SlidingRange() {}

    static int[] spread(int[] values, int width, int depth, int windowX, int windowZ) {
        int[] rowMax = slide(values, width, depth, windowX, true, true);
        int[] rowMin = slide(values, width, depth, windowX, true, false);
        int[] max = slide(rowMax, width, depth, windowZ, false, true);
        int[] min = slide(rowMin, width, depth, windowZ, false, false);
        int[] spread = new int[values.length];
        for (int index = 0; index < values.length; index++) {
            spread[index] = max[index] == Integer.MIN_VALUE || min[index] == Integer.MAX_VALUE
                    ? Integer.MAX_VALUE
                    : max[index] - min[index];
        }
        return spread;
    }

    private static int[] slide(int[] values, int width, int depth, int window, boolean alongX, boolean max) {
        int[] out = new int[values.length];
        int lines = alongX ? depth : width;
        int length = alongX ? width : depth;
        for (int line = 0; line < lines; line++) {
            Deque<Integer> deque = new ArrayDeque<>();
            for (int step = length - 1; step >= 0; step--) {
                int value = values[at(line, step, width, alongX)];
                while (!deque.isEmpty() && better(values[at(line, deque.peekLast(), width, alongX)], value, max)) {
                    deque.pollLast();
                }
                deque.addLast(step);
                while (deque.peekFirst() >= step + window) {
                    deque.pollFirst();
                }
                boolean full = step + window <= length;
                out[at(line, step, width, alongX)] = full
                        ? values[at(line, deque.peekFirst(), width, alongX)]
                        : max ? Integer.MIN_VALUE : Integer.MAX_VALUE;
            }
        }
        return out;
    }

    private static boolean better(int kept, int incoming, boolean max) {
        return max ? kept <= incoming : kept >= incoming;
    }

    private static int at(int line, int step, int width, boolean alongX) {
        return alongX ? line * width + step : step * width + line;
    }
}
