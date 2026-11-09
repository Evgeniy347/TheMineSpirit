package com.theminespirit.core.ui;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class SettlementTreeCodec {
    private static final int MAGIC = 0x53455452;
    private static final int VERSION = 1;
    private static final int MAX_NODES = 2048;
    private static final int MAX_EDGES = 4096;
    private static final int MAX_POINTS = 32;
    private static final int MAX_MISSING = 256;
    private static final int MAX_REQUIREMENTS = 256;
    private static final List<SettlementTree.Kind> KINDS = List.of(SettlementTree.Kind.values());
    private static final List<SettlementTree.State> STATES = List.of(SettlementTree.State.values());

    private SettlementTreeCodec() {}

    public static byte[] encode(SettlementTree tree) {
        Objects.requireNonNull(tree, "Древо не может быть null: упаковывать нечего");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeInt(MAGIC);
            out.writeInt(VERSION);
            out.writeInt(tree.stage());
            out.writeInt(tree.stages());
            out.writeInt(tree.currentStage());
            out.writeInt(tree.width());
            out.writeInt(tree.height());
            out.writeInt(tree.nodes().size());
            for (SettlementTree.Node node : tree.nodes()) {
                writeNode(out, node);
            }
            out.writeInt(tree.edges().size());
            for (SettlementTree.Edge edge : tree.edges()) {
                out.writeUTF(edge.from());
                out.writeUTF(edge.to());
                out.writeInt(edge.points().size());
                for (SettlementTree.Point point : edge.points()) {
                    out.writeInt(point.x());
                    out.writeInt(point.y());
                }
            }
        } catch (IOException impossible) {
            throw new IllegalStateException(
                    "Древо поселения не упаковано в память: " + impossible.getMessage(), impossible);
        }
        return bytes.toByteArray();
    }

    public static SettlementTree decode(byte[] payload) {
        Objects.requireNonNull(payload, "Пакет древа не может быть null: разбирать нечего");
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(payload))) {
            int magic = in.readInt();
            if (magic != MAGIC) {
                throw new IllegalArgumentException(
                        "Пакет древа не начинается с метки сборки: клиент и сервер разошлись версиями");
            }
            int version = in.readInt();
            if (version != VERSION) {
                throw new IllegalArgumentException(
                        "Версия пакета древа " + version + ", ожидалась " + VERSION + ": обновите мод на клиенте");
            }
            int stage = in.readInt();
            int stages = in.readInt();
            int currentStage = in.readInt();
            int width = in.readInt();
            int height = in.readInt();
            int nodeCount = count(in.readInt(), MAX_NODES, "рамок");
            List<SettlementTree.Node> nodes = new ArrayList<>(nodeCount);
            for (int index = 0; index < nodeCount; index++) {
                nodes.add(readNode(in));
            }
            int edgeCount = count(in.readInt(), MAX_EDGES, "связей");
            List<SettlementTree.Edge> edges = new ArrayList<>(edgeCount);
            for (int index = 0; index < edgeCount; index++) {
                edges.add(readEdge(in));
            }
            return new SettlementTree(stage, stages, currentStage, nodes, edges, width, height);
        } catch (IOException broken) {
            throw new IllegalArgumentException("Пакет древа поселения оборван: " + broken.getMessage(), broken);
        }
    }

    private static void writeNode(DataOutputStream out, SettlementTree.Node node) throws IOException {
        out.writeUTF(node.id());
        out.writeInt(KINDS.indexOf(node.kind()));
        out.writeInt(STATES.indexOf(node.state()));
        out.writeUTF(node.title());
        out.writeUTF(node.icon());
        out.writeInt(node.level());
        out.writeInt(node.percent());
        out.writeBoolean(node.optional());
        out.writeBoolean(node.blocking());
        out.writeInt(node.x());
        out.writeInt(node.y());
        out.writeBoolean(node.at() != null);
        if (node.at() != null) {
            out.writeInt(node.at().x());
            out.writeInt(node.at().y());
            out.writeInt(node.at().z());
        }
        out.writeInt(node.missing().size());
        for (Map.Entry<String, Integer> entry : node.missing().entrySet()) {
            out.writeUTF(entry.getKey());
            out.writeInt(entry.getValue());
        }
        out.writeInt(node.requirements().size());
        for (String requirement : node.requirements()) {
            out.writeUTF(requirement);
        }
        out.writeUTF(node.detail());
    }

    private static SettlementTree.Node readNode(DataInputStream in) throws IOException {
        String id = in.readUTF();
        SettlementTree.Kind kind = KINDS.get(index(in.readInt(), KINDS.size(), "вида рамки"));
        SettlementTree.State state = STATES.get(index(in.readInt(), STATES.size(), "состояния рамки"));
        String title = in.readUTF();
        String icon = in.readUTF();
        int level = in.readInt();
        int percent = in.readInt();
        boolean optional = in.readBoolean();
        boolean blocking = in.readBoolean();
        int x = in.readInt();
        int y = in.readInt();
        SettlementTree.Position at =
                in.readBoolean() ? new SettlementTree.Position(in.readInt(), in.readInt(), in.readInt()) : null;
        int missingCount = count(in.readInt(), MAX_MISSING, "недостачи рамки");
        Map<String, Integer> missing = new LinkedHashMap<>();
        for (int index = 0; index < missingCount; index++) {
            missing.put(in.readUTF(), in.readInt());
        }
        int requirementCount = count(in.readInt(), MAX_REQUIREMENTS, "требований рамки");
        List<String> requirements = new ArrayList<>(requirementCount);
        for (int index = 0; index < requirementCount; index++) {
            requirements.add(in.readUTF());
        }
        return new SettlementTree.Node(
                id,
                kind,
                state,
                title,
                icon,
                level,
                percent,
                optional,
                blocking,
                x,
                y,
                at,
                missing,
                requirements,
                in.readUTF());
    }

    private static SettlementTree.Edge readEdge(DataInputStream in) throws IOException {
        String from = in.readUTF();
        String to = in.readUTF();
        int pointCount = count(in.readInt(), MAX_POINTS, "точек связи");
        List<SettlementTree.Point> points = new ArrayList<>(pointCount);
        for (int index = 0; index < pointCount; index++) {
            points.add(new SettlementTree.Point(in.readInt(), in.readInt()));
        }
        return new SettlementTree.Edge(from, to, points);
    }

    private static int count(int value, int max, String what) {
        if (value < 0 || value > max) {
            throw new IllegalArgumentException("Пакет древа несёт " + value + " (" + what + ") при пределе " + max
                    + ": пакет повреждён или сборки клиента и сервера разошлись");
        }
        return value;
    }

    private static int index(int value, int size, String what) {
        if (value < 0 || value >= size) {
            throw new IllegalArgumentException("Пакет древа называет " + what + " №" + value + " при допустимых 0.."
                    + (size - 1) + ": пакет повреждён или сборки клиента и сервера разошлись");
        }
        return value;
    }
}
