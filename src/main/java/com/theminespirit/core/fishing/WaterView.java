package com.theminespirit.core.fishing;

import com.theminespirit.core.observation.BlockCoord;
import java.util.Collection;

public interface WaterView {
    record Column(int x, int z, int surfaceY, int depth) {
        public Column {
            if (depth < 1) {
                throw new IllegalArgumentException("Колонка воды глубиной " + depth
                        + ": открытая вода начинается с одного блока вниз, иначе это мокрый пол, а не водоём");
            }
        }
    }

    Collection<Column> columns();

    boolean canStand(BlockCoord feet);
}
