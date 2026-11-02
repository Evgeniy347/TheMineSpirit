// lib: Из Baritone (github.com/cabaletta/baritone, src/main/java/baritone/cache/ChunkPacker.java), лицензия LGPL-3.0.
package com.theminespirit.core.navigation;

@FunctionalInterface
public interface NavChunkSource {
    NavBlockType type(int x, int y, int z);
}
