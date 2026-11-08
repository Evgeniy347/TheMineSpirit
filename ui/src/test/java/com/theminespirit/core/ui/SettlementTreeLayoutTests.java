package com.theminespirit.core.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.StageRules;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SettlementTreeLayoutTests {
    @Test
    void layout_AllRealStages_HasNoOverlappingFrames() {
        StageRules rules = StageRules.load();
        SettlementTreeIcons icons = SettlementTreeIcons.load();
        for (int stage = 1; stage <= rules.stages().size(); stage++) {
            SettlementTree tree = SettlementTrees.build(rules, SettlementTreeFacts.empty(), icons, 1, stage);
            List<SettlementTree.Node> nodes = tree.nodes();
            for (int first = 0; first < nodes.size(); first++) {
                for (int second = first + 1; second < nodes.size(); second++) {
                    assertFalse(
                            overlaps(nodes.get(first), nodes.get(second)),
                            "Этап " + stage + ": рамки «" + nodes.get(first).title() + "» и «"
                                    + nodes.get(second).title() + "» наложились друг на друга");
                }
            }
        }
    }

    @Test
    void layout_SameTables_IsDeterministic() {
        StageRules rules = StageRules.load();
        SettlementTreeIcons icons = SettlementTreeIcons.load();
        for (int stage = 1; stage <= rules.stages().size(); stage++) {
            SettlementTree first = SettlementTrees.build(rules, SettlementTreeFacts.empty(), icons, 1, stage);
            SettlementTree second = SettlementTrees.build(rules, SettlementTreeFacts.empty(), icons, 1, stage);

            assertEquals(
                    places(first),
                    places(second),
                    "Этап " + stage + ": раскладка древа повторяется при тех же таблицах и состоянии мира");
        }
    }

    @Test
    void layout_AllRealStages_LinksDoNotCrossFrames() {
        StageRules rules = StageRules.load();
        SettlementTreeIcons icons = SettlementTreeIcons.load();
        for (int stage = 1; stage <= rules.stages().size(); stage++) {
            SettlementTree tree = SettlementTrees.build(rules, SettlementTreeFacts.empty(), icons, 1, stage);
            for (SettlementTree.Edge edge : tree.edges()) {
                for (SettlementTree.Point point : edge.points()) {
                    for (SettlementTree.Node node : tree.nodes()) {
                        if (node.id().equals(edge.from()) || node.id().equals(edge.to())) {
                            continue;
                        }
                        assertFalse(
                                inside(node, point),
                                "Этап " + stage + ": линия " + edge.from() + " → " + edge.to()
                                        + " проходит сквозь рамку «" + node.title() + "»");
                    }
                }
            }
        }
    }

    @Test
    void layout_AllRealStages_RootLeftAndGateRight() {
        StageRules rules = StageRules.load();
        SettlementTreeIcons icons = SettlementTreeIcons.load();
        for (int stage = 1; stage <= rules.stages().size(); stage++) {
            SettlementTree tree = SettlementTrees.build(rules, SettlementTreeFacts.empty(), icons, 1, stage);
            SettlementTree.Node root = tree.node("s" + stage + ":root").orElseThrow();
            SettlementTree.Node gate = tree.node("s" + stage + ":gate").orElseThrow();
            for (SettlementTree.Node node : tree.nodes()) {
                assertTrue(
                        node.x() >= root.x(),
                        "Этап " + stage + ": рамка «" + node.title() + "» не левее корня — ветки идут вправо");
                assertTrue(
                        node.x() <= gate.x(),
                        "Этап " + stage + ": рамка «" + node.title() + "» не правее ворот — ворота замыкают ветку");
            }
            assertTrue(
                    gate.y() >= root.y(),
                    "Этап " + stage + ": ворота стоят ниже корня, отдельной строкой, и не наезжают на ветки");
            assertTrue(
                    tree.width() >= gate.x() + SettlementTreeStyle.NODE
                            && tree.height() >= gate.y() + SettlementTreeStyle.NODE,
                    "Этап " + stage + ": размер древа вмещает ворота — иначе их не дотащить мышью");
        }
    }

    @Test
    void frames_RealStageTables_ListEveryRequirementAndPlanObject() {
        StageRules rules = StageRules.load();
        SettlementTreeIcons icons = SettlementTreeIcons.load();
        for (int stage = 1; stage <= rules.stages().size(); stage++) {
            SettlementTree tree = SettlementTrees.build(rules, SettlementTreeFacts.empty(), icons, 1, stage);
            StageRules.Stage rule = rules.stages().get(stage - 1);
            for (var entry : rule.buildings().entrySet()) {
                assertTrue(
                        tree.node("s" + stage + ":case:" + entry.getKey().name() + ":" + entry.getValue())
                                .isPresent(),
                        "Этап " + stage + ": нет рамки обязательного кейса " + entry.getKey() + " уровня "
                                + entry.getValue());
            }
            for (var entry : rule.requires().entrySet()) {
                assertTrue(
                        tree.node("s" + stage + ":plan:" + entry.getKey() + ":" + entry.getValue())
                                .isPresent(),
                        "Этап " + stage + ": нет рамки обязательной постройки " + entry.getKey());
            }
            for (var entry : rule.optional().entrySet()) {
                assertTrue(
                        tree.node("s" + stage + ":plan:" + entry.getKey() + ":" + entry.getValue())
                                .isPresent(),
                        "Этап " + stage + ": нет рамки желательной постройки " + entry.getKey());
            }
            for (String planId : rule.plan()) {
                boolean hidden = icons.alias(planId)
                        .map(caseValue -> rule.buildings().containsKey(caseValue))
                        .orElse(false);
                if (!hidden) {
                    assertTrue(
                            tree.node("s" + stage + ":plan:" + planId + ":1").isPresent(),
                            "Этап " + stage + ": нет рамки объекта плана " + planId);
                }
            }
            String gate = tree.node("s" + stage + ":gate").orElseThrow().title();
            String expected =
                    stage == rules.stages().size() ? "Поселение завершено" : "Этап " + (stage + 1) + " открыт";
            assertEquals(expected, gate, "Этап " + stage + ": последняя рамка ветки называется «" + expected + "»");
        }
    }

    @Test
    void icons_AllStageObjects_HaveDataEntries() {
        StageRules rules = StageRules.load();
        SettlementTreeIcons icons = SettlementTreeIcons.load();
        for (StageRules.Stage stage : rules.stages()) {
            for (String planId : stage.plan()) {
                assertFilled(icons.planEntry(planId), "постройки плана '" + planId + "'");
            }
            for (String planId : stage.requires().keySet()) {
                assertFilled(icons.planEntry(planId), "обязательной постройки '" + planId + "'");
            }
            for (String planId : stage.optional().keySet()) {
                assertFilled(icons.planEntry(planId), "желательной постройки '" + planId + "'");
            }
            for (var boardCase : stage.buildings().keySet()) {
                assertFilled(icons.caseEntry(boardCase), "кейса '" + boardCase + "'");
            }
        }
    }

    private static void assertFilled(SettlementTreeIcons.Entry entry, String what) {
        assertFalse(entry.title().isBlank(), "Пустое название " + what + " в " + SettlementTreeIcons.RESOURCE);
        assertFalse(entry.icon().isBlank(), "Пустая иконка " + what + " в " + SettlementTreeIcons.RESOURCE);
    }

    private static Map<String, SettlementTree.Point> places(SettlementTree tree) {
        Map<String, SettlementTree.Point> places = new LinkedHashMap<>();
        tree.nodes().forEach(node -> places.put(node.id(), new SettlementTree.Point(node.x(), node.y())));
        return places;
    }

    private static boolean overlaps(SettlementTree.Node first, SettlementTree.Node second) {
        return first.x() < second.x() + SettlementTreeStyle.NODE
                && second.x() < first.x() + SettlementTreeStyle.NODE
                && first.y() < second.y() + SettlementTreeStyle.NODE
                && second.y() < first.y() + SettlementTreeStyle.NODE;
    }

    private static boolean inside(SettlementTree.Node node, SettlementTree.Point point) {
        return point.x() > node.x()
                && point.x() < node.x() + SettlementTreeStyle.NODE
                && point.y() > node.y()
                && point.y() < node.y() + SettlementTreeStyle.NODE;
    }
}
