package com.amiral.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import javax.swing.JComponent;
import javax.swing.Timer;

/** The big pill in the header that tells whose turn it is. Pulses on our turn. */
public class TurnBanner extends JComponent {

    private static final long serialVersionUID = 1L;

    /** What the banner shows. */
    public enum Mode {
        IDLE("READY TO PLAY", Theme.TEXT_DIM),
        PLACING("PLACE YOUR SHIPS", Theme.ACCENT),
        WAITING("WAITING FOR OPPONENT", Theme.TEXT_DIM),
        YOUR_TURN("YOUR TURN", Theme.YOUR_TURN),
        ENEMY_TURN("ENEMY'S TURN", Theme.ENEMY_TURN),
        WON("VICTORY!", Theme.GOLD),
        LOST("DEFEAT", Theme.ENEMY_TURN);

        final String text;
        final Color color;

        Mode(String text, Color color) {
            this.text = text;
            this.color = color;
        }
    }

    private Mode mode = Mode.IDLE;
    private final transient Timer timer = new Timer(40, e -> repaint());

    @Override
    public void addNotify() {
        super.addNotify();
        timer.start();
    }

    @Override
    public void removeNotify() {
        timer.stop();
        super.removeNotify();
    }

    public void setMode(Mode mode) {
        this.mode = mode;
        repaint();
    }

    public Mode mode() {
        return mode;
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(320, 40);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setFont(Theme.BANNER_FONT);
        FontMetrics fm = g2.getFontMetrics();
        int w = Math.min(getWidth() - 8, fm.stringWidth(mode.text) + 64);
        int h = Math.min(getHeight() - 6, 34);
        double x = (getWidth() - w) / 2.0;
        double y = (getHeight() - h) / 2.0;
        boolean pulse = mode == Mode.YOUR_TURN || mode == Mode.WON;
        double p = pulse ? 0.5 + 0.5 * Math.sin(System.nanoTime() / 1e6 / 250.0) : 0;
        RoundRectangle2D pill = new RoundRectangle2D.Double(x, y, w, h, h, h);
        for (int i = 3; i >= 1 && pulse; i--) {
            g2.setStroke(new BasicStroke(i * 3f));
            g2.setColor(Theme.alpha(mode.color, (30 + 50 * p) / i));
            g2.draw(pill);
        }
        g2.setColor(Theme.alpha(mode.color, 45));
        g2.fill(pill);
        g2.setStroke(new BasicStroke(1.5f));
        g2.setColor(mode.color);
        g2.draw(pill);
        double dot = 9;
        g2.fill(new java.awt.geom.Ellipse2D.Double(x + 16, y + (h - dot) / 2, dot, dot));
        g2.drawString(mode.text, (float) (x + (w - fm.stringWidth(mode.text)) / 2.0 + 8),
                (float) (y + (h + fm.getAscent() - fm.getDescent()) / 2.0));
        g2.dispose();
    }
}
