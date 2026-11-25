package com.theminespirit.core.town;

import java.util.Base64;
import java.util.Objects;

public final class TownDrawio {
    public static final int FORMAT = 1;
    private static final String PLOT_STYLE = "rounded=0;whiteSpace=wrap;html=1;fillColor=#fff2cc;strokeColor=#d6b656;";

    private TownDrawio() {}

    public static String encode(TownMap map, int revision, int x1, int z1, int x2, int z2, int scale, byte[] png) {
        Objects.requireNonNull(map, "Карта не может быть null: без участков и стены выгружать нечего");
        Objects.requireNonNull(png, "Подложка не может быть null: .drawio без картинки не открыть");
        if (scale < 1 || scale > 8) {
            throw new IllegalArgumentException("Масштаб " + scale + ": допустимо 1..8 пикселей на блок");
        }
        if (x1 >= x2 || z1 >= z2) {
            throw new IllegalArgumentException(
                    "Рамка " + x1 + "," + z1 + " → " + x2 + "," + z2 + " пуста: нужны x1<x2 и z1<z2");
        }
        if (png.length == 0) {
            throw new IllegalArgumentException("Подложка пуста: PNG для .drawio не отдан");
        }
        int width = (x2 - x1 + 1) * scale;
        int height = (z2 - z1 + 1) * scale;
        StringBuilder xml = new StringBuilder(64 * 1024 + png.length * 4 / 3);
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
        image(xml, png, width, height);
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
                            + "\" tmFacing=\"" + escape(plot.facing()) + "\"");
        }
    }

    private static void streets(StringBuilder xml, TownMap map, int x1, int z1, int scale) {
        int index = 0;
        int rings = 0;
        for (TownMap.MapStreet street : map.streets()) {
            index++;
            if (street.ring()) {
                rings++;
                vertex(
                        xml,
                        "ring-" + index,
                        "кольцо: " + street.id(),
                        "text;html=1;align=left;verticalAlign=top;fontSize=10;",
                        4,
                        18 * rings,
                        240,
                        18,
                        "tmKind=\"street\" tmId=\"" + escape(street.id()) + "\" tmKindValue=\"ring\""
                                + " tmX1=\"0\" tmZ1=\"0\" tmX2=\"0\" tmZ2=\"0\"");
                continue;
            }
            int thickness = Math.min(street.width(), street.depth()) * scale;
            int ax;
            int az;
            int bx;
            int bz;
            if (street.width() >= street.depth()) {
                int centerZ = (street.minZ() + street.maxZ()) / 2;
                ax = pixel(street.minX(), x1, scale);
                az = pixel(centerZ, z1, scale);
                bx = pixel(street.maxX(), x1, scale);
                bz = az;
            } else {
                int centerX = (street.minX() + street.maxX()) / 2;
                ax = pixel(centerX, x1, scale);
                az = pixel(street.minZ(), z1, scale);
                bx = ax;
                bz = pixel(street.maxZ(), z1, scale);
            }
            edge(
                    xml,
                    "street-" + index,
                    street.id(),
                    "endArrow=none;html=1;rounded=0;opacity=70;strokeColor=" + streetColor(street.kind())
                            + ";strokeWidth=" + thickness + ";",
                    ax,
                    az,
                    bx,
                    bz,
                    "tmKind=\"street\" tmId=\"" + escape(street.id()) + "\" tmKindValue=\"" + escape(street.kind())
                            + "\" tmX1=\"" + street.x1() + "\" tmZ1=\"" + street.z1() + "\" tmX2=\"" + street.x2()
                            + "\" tmZ2=\"" + street.z2() + "\"");
        }
    }

    private static String streetColor(String kind) {
        return switch (kind) {
            case "main" -> "#333333";
            case "ring" -> "#999999";
            default -> "#777777";
        };
    }

    private static void walls(StringBuilder xml, TownMap map, int x1, int z1, int scale) {
        int index = 0;
        for (TownMap.WallSegment segment : map.wall()) {
            index++;
            edge(
                    xml,
                    "wall-" + index,
                    "",
                    "endArrow=none;html=1;rounded=0;strokeColor=#282828;strokeWidth=" + Math.max(2, 3 * scale) + ";",
                    pixel(segment.x1(), x1, scale),
                    pixel(segment.z1(), z1, scale),
                    pixel(segment.x2(), x1, scale),
                    pixel(segment.z2(), z1, scale),
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

    private static void edge(
            StringBuilder xml,
            String id,
            String value,
            String style,
            int ax,
            int az,
            int bx,
            int bz,
            String attributes) {
        xml.append("        <mxCell id=\"")
                .append(id)
                .append("\" value=\"")
                .append(escape(value))
                .append("\" style=\"")
                .append(style)
                .append("\" edge=\"1\" parent=\"1\" ")
                .append(attributes)
                .append(">\n");
        xml.append("          <mxGeometry relative=\"1\" as=\"geometry\">\n");
        xml.append("            <mxPoint x=\"")
                .append(ax)
                .append("\" y=\"")
                .append(az)
                .append("\" as=\"sourcePoint\"/>\n");
        xml.append("            <mxPoint x=\"")
                .append(bx)
                .append("\" y=\"")
                .append(bz)
                .append("\" as=\"targetPoint\"/>\n");
        xml.append("          </mxGeometry>\n");
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
