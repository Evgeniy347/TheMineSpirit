package com.theminespirit.core.ui;

public final class ShellLayout {
    public static final int HEADER_HEIGHT = 26;
    public static final int FOOTER_HEIGHT = 16;
    public static final int PAD = 8;
    public static final int BOTTOM_GAP = 2;
    public static final int CONTENT_TOP = HEADER_HEIGHT + 6;
    public static final int TOOLBAR_HEIGHT = 16;
    public static final int TOOLBAR_GAP = 6;

    private final BoardLayout.Rect header;
    private final BoardLayout.Rect content;
    private final BoardLayout.Rect footer;

    private ShellLayout(BoardLayout.Rect header, BoardLayout.Rect content, BoardLayout.Rect footer) {
        this.header = header;
        this.content = content;
        this.footer = footer;
    }

    public static ShellLayout of(int screenWidth, int screenHeight) {
        if (screenWidth < 0 || screenHeight < 0) {
            throw new IllegalArgumentException("Экран " + screenWidth + "×" + screenHeight
                    + " отрицателен: каркасу негде рисовать шапку и подвал");
        }
        BoardLayout.Rect header = new BoardLayout.Rect(0, 0, screenWidth, Math.min(HEADER_HEIGHT, screenHeight));
        int footerTop = Math.min(screenHeight, Math.max(header.bottom(), screenHeight - FOOTER_HEIGHT));
        BoardLayout.Rect footer = new BoardLayout.Rect(0, footerTop, screenWidth, screenHeight - footerTop);
        int contentTop = Math.min(CONTENT_TOP, footerTop);
        BoardLayout.Rect content = new BoardLayout.Rect(
                PAD, contentTop, Math.max(0, screenWidth - 2 * PAD), Math.max(0, footerTop - BOTTOM_GAP - contentTop));
        return new ShellLayout(header, content, footer);
    }

    public BoardLayout.Rect header() {
        return header;
    }

    public BoardLayout.Rect content() {
        return content;
    }

    public BoardLayout.Rect footer() {
        return footer;
    }

    public BoardLayout.Rect toolbar() {
        return new BoardLayout.Rect(
                content.x(), content.y(), content.width(), Math.min(TOOLBAR_HEIGHT, content.height()));
    }

    public BoardLayout.Rect body() {
        int top = content.y() + Math.min(TOOLBAR_HEIGHT + TOOLBAR_GAP, content.height());
        return new BoardLayout.Rect(content.x(), top, content.width(), Math.max(0, content.bottom() - top));
    }

    public static int titleX() {
        return PAD + 6;
    }

    public static int titleY() {
        return 8;
    }
}
