// lib: Из Baritone (github.com/cabaletta/baritone, cache/ChunkPacker.java), лицензия LGPL-3.0.
package com.theminespirit.core.navigation;

@FunctionalInterface
public interface NavChunkSource {
    NavBlockType type(int x, int y, int z);
}
