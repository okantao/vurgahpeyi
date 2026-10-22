package com.amiral.ui;

import java.awt.Color;
import java.awt.Font;

/** Colours, fonts and sizes (ROADMAP section 7). */
public final class Theme {

    public static final Color WATER = Color.decode("#F4F6F7");
    public static final Color GRID = Color.decode("#D5D8DC");
    public static final Color SHIP = Color.decode("#7F8C8D");
    public static final Color HIT = Color.decode("#E74C3C");
    public static final Color SUNK = Color.decode("#922B21");
    public static final Color MISS = Color.decode("#AED6F1");
    public static final Color MISS_DOT = Color.decode("#1B4F72");
    public static final Color PREVIEW_OK = Color.decode("#ABEBC6");
    public static final Color PREVIEW_BAD = Color.decode("#F5B7B1");
    public static final Color CHAT_ME = Color.decode("#2E86C1");
    public static final Color CHAT_ENEMY = Color.decode("#C0392B");
    public static final Color ERROR_BORDER = Color.decode("#E74C3C");
    public static final Color LABEL = Color.decode("#2C3E50");

    public static final Font BASE_FONT = new Font(Font.SANS_SERIF, Font.PLAIN, 13);
    public static final Font LABEL_FONT = new Font(Font.SANS_SERIF, Font.BOLD, 14);
    public static final Font TITLE_FONT = new Font(Font.SANS_SERIF, Font.BOLD, 15);
    public static final Font MONO_FONT = new Font(Font.MONOSPACED, Font.PLAIN, 12);

    public static final int GAP = 6;
    public static final int SIDE_PANEL_WIDTH = 240;
    public static final int OUTPUT_HEIGHT = 150;
    public static final int BOARD_PREFERRED = 360;

    private Theme() {
    }
}
