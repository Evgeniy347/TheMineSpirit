package com.theminespirit.core.intellect.defense;

import com.theminespirit.core.tactics.EnemyFact;
import com.theminespirit.core.tactics.Point;
import java.util.List;

record ThreatCluster(double bearing, int count, double weight, Point centroid, List<EnemyFact> enemies) {
    ThreatCluster {
        if (!Double.isFinite(bearing) || !Double.isFinite(weight)) {
            throw new IllegalArgumentException(
                    "Сектор угроз: направление " + bearing + ", вес " + weight + " — числа обязаны быть конечными");
        }
        if (count <= 0 || weight < 0) {
            throw new IllegalArgumentException(
                    "Сектор угроз: мобов " + count + ", вес " + weight + " — мобы положительны, вес неотрицателен");
        }
        if (centroid == null) {
            throw new IllegalArgumentException("centroid не может быть null: без точки сектора некуда идти");
        }
        enemies = enemies == null ? List.of() : List.copyOf(enemies);
    }
}
