package com.amiral.ui;

import java.awt.Color;
import java.awt.Font;

/** Colours, fonts and sizes of the dark naval theme. */
public final class Theme {

    // Window
    public static final Color BACKGROUND = Color.decode("#0E1726");
    public static final Color PANEL = Color.decode("#142133");
    public static final Color HEADER = Color.decode("#0A111D");
    public static final Color TEXT = Color.decode("#DCE7F3");
    public static final Color TEXT_DIM = Color.decode("#8EA3BA");
    public static final Color ACCENT = Color.decode("#3FA9F5");

    // Board
    public static final Color OCEAN_TOP = Color.decode("#0B2A4A");
    public static final Color OCEAN_BOTTOM = Color.decode("#0A1F38");
    public static final Color GRID = new Color(120, 170, 220, 45);
    public static final Color BOARD_EDGE = new Color(90, 160, 230, 140);
    public static final Color LABEL = Color.decode("#7FA7CF");
    public static final Color WATER = OCEAN_TOP;

    // Ships
    public static final Color HULL_LIGHT = Color.decode("#B4C2D0");
    public static final Color HULL_DARK = Color.decode("#5D6D7E");
    public static final Color HULL_EDGE = Color.decode("#1E2A36");
    public static final Color DECK = Color.decode("#46525F");
    public static final Color SUB_LIGHT = Color.decode("#6C7A89");
    public static final Color SUB_DARK = Color.decode("#2E3A47");
    public static final Color WRECK_LIGHT = Color.decode("#5A4141");
    public static final Color WRECK_DARK = Color.decode("#2A1C1C");
    public static final Color SHIP = HULL_DARK;

    // Shots
    public static final Color HIT = Color.decode("#FF5A36");
    public static final Color FIRE = Color.decode("#FFB238");
    public static final Color SUNK = Color.decode("#C0392B");
    public static final Color MISS = Color.decode("#CFE8FF");
    public static final Color MISS_DOT = Color.decode("#8CC8FF");

    // Placement and weapons
    public static final Color PREVIEW_OK = Color.decode("#2ECC71");
    public static final Color PREVIEW_BAD = Color.decode("#E74C3C");
    public static final Color RADAR = Color.decode("#FF4D4D");
    public static final Color RADAR_GLOW = Color.decode("#FF8A65");
    public static final Color TORPEDO = Color.decode("#FFA726");
    public static final Color RETICLE = Color.decode("#FFD166");
    public static final Color YOUR_TURN = Color.decode("#2ECC71");
    public static final Color ENEMY_TURN = Color.decode("#E74C3C");
    public static final Color GOLD = Color.decode("#F1C40F");

    // Chat and messages
    public static final Color CHAT_ME = Color.decode("#5DADE2");
    public static final Color CHAT_ENEMY = Color.decode("#F1948A");
    public static final Color ERROR_BORDER = Color.decode("#E74C3C");

    public static final Font BASE_FONT = new Font(Font.SANS_SERIF, Font.PLAIN, 13);
    public static final Font LABEL_FONT = new Font(Font.SANS_SERIF, Font.BOLD, 13);
    public static final Font TITLE_FONT = new Font(Font.SANS_SERIF, Font.BOLD, 16);
    public static final Font BANNER_FONT = new Font(Font.SANS_SERIF, Font.BOLD, 18);
    public static final Font LOGO_FONT = new Font(Font.SANS_SERIF, Font.BOLD, 20);
    public static final Font MONO_FONT = new Font(Font.MONOSPACED, Font.PLAIN, 12);

    public static final int GAP = 8;
    public static final int SIDE_PANEL_WIDTH = 290;
    public static final int OUTPUT_HEIGHT = 130;
    public static final int BOARD_PREFERRED = 400;

    private Theme() {
    }

    /** The colour with a new alpha (0-255, clamped). */
    public static Color alpha(Color c, double a) {
        int v = (int) Math.max(0, Math.min(255, a));
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), v);
    }
}
