package com.theminespirit.core.tactics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class LayerOrderTests {
    @Test
    void priority_DeclaredOrder_IsPolicyTacticsStrategyIdle() {
        assertEquals(List.of(Layer.POLICY, Layer.TACTICS, Layer.STRATEGY, Layer.IDLE), LayerOrder.PRIORITY,
                "Порядок слоёв POLICY > TACTICS > STRATEGY > IDLE зафиксирован: политики безопасности выше тактики, "
                        + "тактика выше стратегии. Переставить слои можно только осознанно, вместе с чеклистом "
                        + "политик и адаптером арбитража тика");
    }

    @Test
    void priority_EveryLayer_AppearsExactlyOnce() {
        assertEquals(List.of(Layer.values()), LayerOrder.PRIORITY,
                "Каждый слой назван в приоритете ровно один раз и в порядке объявления enum: новый слой без места в "
                        + "порядке остался бы без приоритета");
    }

    @Test
    void outranks_AdjacentLayers_HigherWinsOnlyOneWay() {
        assertEquals(List.of(true, false, true, false, true, false),
                List.of(LayerOrder.outranks(Layer.POLICY, Layer.TACTICS),
                        LayerOrder.outranks(Layer.TACTICS, Layer.POLICY),
                        LayerOrder.outranks(Layer.TACTICS, Layer.STRATEGY),
                        LayerOrder.outranks(Layer.STRATEGY, Layer.TACTICS),
                        LayerOrder.outranks(Layer.STRATEGY, Layer.IDLE),
                        LayerOrder.outranks(Layer.IDLE, Layer.STRATEGY)),
                "Слой выше перебивает слой ниже и никогда наоборот: иначе стратегия могла бы заглушить реакцию на угрозу");
    }

    @Test
    void outranks_SameLayer_IsFalse() {
        for (Layer layer : Layer.values()) {
            assertTrue(!LayerOrder.outranks(layer, layer),
                    "Слой не перебивает сам себя: " + layer + ", иначе арбитраж зациклился бы на одном слое");
        }
    }

    @Test
    void interruptedLayer_AfterPreemption_Resumes() {
        assertTrue(LayerOrder.INTERRUPTED_LAYER_RESUMES,
                "Прерванный слой возобновляется после ухода перебившего: после боя дух продолжает задачу стратега, "
                        + "а не теряет её");
    }
}
