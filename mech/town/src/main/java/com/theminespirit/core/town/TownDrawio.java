package com.theminespirit.core.town;

import java.util.Base64;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

public final class TownDrawio {
    public static final int FORMAT = 2;
    private static final String PLOT_STYLE = "rounded=0;whiteSpace=wrap;html=1;fillColor=#fff2cc;strokeColor=#d6b656;";

    public record Decoded(TownMap map, int revision) {}

    private TownDrawio() {}

    public static Decoded decode(String xml) {
        return TownDrawioParser.decode(xml);
    }

    public static String encode(TownMap map, int revision, int x1, int z1, int x2, int z2, int scale, byte[] png) {
        Objects.requireNonNull(png, "Подложка не может быть null: .drawio без картинки не открыть");
        if (png.length == 0) {
            throw new IllegalArgumentException("Подложка пуста: PNG для .drawio не отдан");
        }
        return build(map, revision, x1, z1, x2, z2, scale, png);
    }

    public static String encodeStored(TownMap map, int revision, int x1, int z1, int x2, int z2, int scale) {
        return build(map, revision, x1, z1, x2, z2, scale, null);
    }

    private static String build(
            TownMap map, int revision, int x1, int z1, int x2, int z2, int scale, byte @Nullable [] png) {
        Objects.requireNonNull(map, "Карта не может быть null: без участков и стены выгружать нечего");
        if (scale < 1 || scale > 8) {
            throw new IllegalArgumentException("Масштаб " + scale + ": допустимо 1..8 пикселей на блок");
        }
        if (x1 >= x2 || z1 >= z2) {
            throw new IllegalArgumentException(
                    "Рамка " + x1 + "," + z1 + " → " + x2 + "," + z2 + " пуста: нужны x1<x2 и z1<z2");
        }
        int width = (x2 - x1 + 1) * scale;
        int height = (z2 - z1 + 1) * scale;
        int bytes = png == null ? 0 : png.length;
        StringBuilder xml = new StringBuilder(64 * 1024 + bytes * 4 / 3);
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<mxfile host=\"app.diagrams.net\" agent=\"The Mine Spirit\" type=\"device\">\n");
        xml.append("  <diagram id=\"town\" name=\"")
                .append(escape("План поселения · ревизия " + revision))
                .append("\">\n");
        xml.append("    <mxGraphModel grid=\"1\" gridSize=\"")
                .append(16 * scale)
                .append(
                        "\" guides=\"1\" tooltips=\"1\" connect=\"1\" arrows=\"1\" fold=\"1\" page=\"1\" pageScale=\"1\" "
                                + "pageWidth=\"")
                .append(width)
                .append("\" pageHeight=\"")
                .append(height)
                .append("\" math=\"0\" shadow=\"0\">\n");
        xml.append("      <root>\n");
        xml.append("        <mxCell id=\"0\"/>\n");
        xml.append("        <mxCell id=\"1\" parent=\"0\"/>\n");
        if (png != null) {
            image(xml, png, width, height);
        }
        plots(xml, map, x1, z1, scale);
        streets(xml, map, x1, z1, scale);
        walls(xml, map, x1, z1, scale);
        meta(xml, map, revision, x1, z1, x2, z2, scale);
        xml.append("      </root>\n    </mxGraphModel>\n  </diagram>\n</mxfile>\n");
        return xml.toString();
    }

    private static void image(StringBuilder xml, byte[] png, int width, int height) {
        String data = Base64.getEncoder().encodeToString(png);
        vertex(
                xml,
                "terrain",
                "",
                "shape=image;verticalLabelPosition=bottom;labelBackgroundColor=default;verticalAlign=top;"
                        + "aspect=fixed;imageAspect=0;movable=0;resizable=0;deletable=0;image=data:image/png,"
                        + data + ";",
                0,
                0,
                width,
                height,
                "");
    }

    private static void plots(StringBuilder xml, TownMap map, int x1, int z1, int scale) {
        int index = 0;
        for (TownMap.MapPlot plot : map.plots()) {
            index++;
            vertex(
                    xml,
                    "plot-" + index,
                    plot.id(),
                    PLOT_STYLE + "fontSize=" + Math.max(8, scale * 4) + ";",
                    (plot.minX() - x1) * scale,
                    (plot.minZ() - z1) * scale,
                    plot.width() * scale,
                    plot.depth() * scale,
                    "tmKind=\"plot\" tmId=\"" + escape(plot.id()) + "\" tmBuilding=\"" + escape(plot.building())
                            + "\" tmFacing=\"" + escape(plot.facing()) + "\" tmX1=\"" + plot.x1() + "\" tmZ1=\""
                            + plot.z1() + "\" tmX2=\"" + plot.x2() + "\" tmZ2=\"" + plot.z2() + "\"");
        }
    }

    private static void streets(StringBuilder xml, TownMap map, int x1, int z1, int scale) {
        int index = 0;
        for (TownMap.MapStreet street : map.streets()) {
            index++;
            vertex(
                    xml,
                    "street-" + index,
                    street.id(),
                    "rounded=0;whiteSpace=wrap;html=1;strokeWidth=0;fillColor=#f5f5f5;strokeColor=#666666;"
                            + "fontColor=#333333;",
                    (street.minX() - x1) * scale,
                    (street.minZ() - z1) * scale,
                    street.width() * scale,
                    street.depth() * scale,
                    "tmKind=\"street\" tmId=\"" + escape(street.id()) + "\" tmKindValue=\"" + escape(street.kind())
                            + "\" tmX1=\"" + street.x1() + "\" tmZ1=\"" + street.z1() + "\" tmX2=\"" + street.x2()
                            + "\" tmZ2=\"" + street.z2() + "\"");
        }
    }

    private static void walls(StringBuilder xml, TownMap map, int x1, int z1, int scale) {
        int index = 0;
        for (TownMap.WallSegment segment : map.wall()) {
            index++;
            double ax = pixel(segment.x1(), x1, scale);
            double az = pixel(segment.z1(), z1, scale);
            double bx = pixel(segment.x2(), x1, scale);
            double bz = pixel(segment.z2(), z1, scale);
            int length = Math.max(1, (int) Math.round(Math.hypot(bx - ax, bz - az)));
            int thickness = Math.max(2, 2 * scale);
            int rotation = (int) Math.round(Math.toDegrees(Math.atan2(bz - az, bx - ax)));
            int left = (int) Math.round((ax + bx) / 2.0 - length / 2.0);
            int top = (int) Math.round((az + bz) / 2.0 - thickness / 2.0);
            vertex(
                    xml,
                    "wall-" + index,
                    "",
                    "rounded=0;whiteSpace=wrap;html=1;strokeWidth=1;strokeColor=#000000;fillColor=#000000;rotation="
                            + rotation + ";",
                    left,
                    top,
                    length,
                    thickness,
                    "tmKind=\"wall\" tmX1=\"" + segment.x1() + "\" tmZ1=\"" + segment.z1() + "\" tmX2=\"" + segment.x2()
                            + "\" tmZ2=\"" + segment.z2() + "\"");
        }
    }

    private static void meta(StringBuilder xml, TownMap map, int revision, int x1, int z1, int x2, int z2, int scale) {
        vertex(
                xml,
                "meta",
                "",
                "text;html=1;opacity=0;",
                0,
                0,
                1,
                1,
                "tmKind=\"meta\" tmFormat=\"" + FORMAT + "\" tmRevision=\"" + revision + "\" tmAnchorX=\""
                        + map.anchor().x() + "\" tmAnchorZ=\"" + map.anchor().z() + "\" tmX1=\"" + x1 + "\" tmZ1=\""
                        + z1 + "\" tmX2=\"" + x2 + "\" tmZ2=\"" + z2 + "\" tmScale=\"" + scale + "\"");
    }

    private static void vertex(
            StringBuilder xml,
            String id,
            String value,
            String style,
            int x,
            int y,
            int width,
            int height,
            String attributes) {
        xml.append("        <mxCell id=\"")
                .append(id)
                .append("\" value=\"")
                .append(escape(value))
                .append("\" style=\"")
                .append(style)
                .append("\" vertex=\"1\" parent=\"1\"");
        if (!attributes.isEmpty()) {
            xml.append(' ').append(attributes);
        }
        xml.append(">\n");
        xml.append("          <mxGeometry x=\"")
                .append(x)
                .append("\" y=\"")
                .append(y)
                .append("\" width=\"")
                .append(width)
                .append("\" height=\"")
                .append(height)
                .append("\" as=\"geometry\"/>\n");
        xml.append("        </mxCell>\n");
    }

    private static int pixel(int block, int origin, int scale) {
        return (block - origin) * scale + scale / 2;
    }

    private static String escape(String text) {
        StringBuilder out = new StringBuilder(text.length() + 8);
        for (int index = 0; index < text.length(); index++) {
            char symbol = text.charAt(index);
            switch (symbol) {
                case '&' -> out.append("&amp;");
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '"' -> out.append("&quot;");
                case '\'' -> out.append("&apos;");
                default -> out.append(symbol);
            }
        }
        return out.toString();
    }
}
