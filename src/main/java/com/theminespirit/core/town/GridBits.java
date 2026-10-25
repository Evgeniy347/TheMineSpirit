package com.theminespirit.core.town;

import java.util.ArrayDeque;
import java.util.BitSet;
import java.util.Deque;

final class GridBits {
    private static final int[] DX = {1, -1, 0, 0};
    private static final int[] DZ = {0, 0, 1, -1};
    private final int width;
    private final int depth;

    GridBits(int width, int depth) {
        this.width = width;
        this.depth = depth;
    }

    int size() {
        return width * depth;
    }

    BitSet dilate(BitSet bits) {
        BitSet out = new BitSet(size());
        for (int index = bits.nextSetBit(0); index >= 0; index = bits.nextSetBit(index + 1)) {
            int x = index % width;
            int z = index / width;
            for (int dz = -1; dz <= 1; dz++) {
                for (int dx = -1; dx <= 1; dx++) {
                    if (inside(x + dx, z + dz)) {
                        out.set(at(x + dx, z + dz));
                    }
                }
            }
        }
        return out;
    }

    BitSet erode(BitSet bits) {
        BitSet out = new BitSet(size());
        for (int index = bits.nextSetBit(0); index >= 0; index = bits.nextSetBit(index + 1)) {
            int x = index % width;
            int z = index / width;
            boolean full = true;
            for (int dz = -1; dz <= 1 && full; dz++) {
                for (int dx = -1; dx <= 1 && full; dx++) {
                    full = inside(x + dx, z + dz) && bits.get(at(x + dx, z + dz));
                }
            }
            if (full) {
                out.set(index);
            }
        }
        return out;
    }

    BitSet component(BitSet bits, int start) {
        BitSet out = new BitSet(size());
        if (!bits.get(start)) {
            return out;
        }
        Deque<Integer> queue = new ArrayDeque<>();
        queue.add(start);
        out.set(start);
        while (!queue.isEmpty()) {
            int index = queue.poll();
            for (int side = 0; side < DX.length; side++) {
                int x = index % width + DX[side];
                int z = index / width + DZ[side];
                if (inside(x, z) && bits.get(at(x, z)) && !out.get(at(x, z))) {
                    out.set(at(x, z));
                    queue.add(at(x, z));
                }
            }
        }
        return out;
    }

    BitSet fillHoles(BitSet bits) {
        BitSet outside = new BitSet(size());
        Deque<Integer> queue = new ArrayDeque<>();
        for (int x = 0; x < width; x++) {
            seed(bits, outside, queue, x, 0);
            seed(bits, outside, queue, x, depth - 1);
        }
        for (int z = 0; z < depth; z++) {
            seed(bits, outside, queue, 0, z);
            seed(bits, outside, queue, width - 1, z);
        }
        while (!queue.isEmpty()) {
            int index = queue.poll();
            for (int side = 0; side < DX.length; side++) {
                seed(bits, outside, queue, index % width + DX[side], index / width + DZ[side]);
            }
        }
        BitSet out = new BitSet(size());
        out.set(0, size());
        out.andNot(outside);
        return out;
    }

    int[] insetDistance(BitSet bits) {
        int[] distance = new int[size()];
        Deque<Integer> queue = new ArrayDeque<>();
        for (int index = 0; index < size(); index++) {
            if (bits.get(index)) {
                distance[index] = Integer.MAX_VALUE;
            } else {
                queue.add(index);
            }
        }
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < depth; z++) {
                boolean edge = x == 0 || z == 0 || x == width - 1 || z == depth - 1;
                if (edge && bits.get(at(x, z))) {
                    distance[at(x, z)] = 1;
                    queue.add(at(x, z));
                }
            }
        }
        while (!queue.isEmpty()) {
            int index = queue.poll();
            int x = index % width;
            int z = index / width;
            for (int dz = -1; dz <= 1; dz++) {
                for (int dx = -1; dx <= 1; dx++) {
                    if (inside(x + dx, z + dz) && distance[at(x + dx, z + dz)] > distance[index] + 1) {
                        distance[at(x + dx, z + dz)] = distance[index] + 1;
                        queue.add(at(x + dx, z + dz));
                    }
                }
            }
        }
        return distance;
    }

    boolean inside(int x, int z) {
        return x >= 0 && z >= 0 && x < width && z < depth;
    }

    int at(int x, int z) {
        return z * width + x;
    }

    private void seed(BitSet bits, BitSet outside, Deque<Integer> queue, int x, int z) {
        if (inside(x, z) && !bits.get(at(x, z)) && !outside.get(at(x, z))) {
            outside.set(at(x, z));
            queue.add(at(x, z));
        }
    }
}
