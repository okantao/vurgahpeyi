package com.amiral.ui;

import com.amiral.model.Orientation;
import com.amiral.model.ShipType;
import java.awt.BasicStroke;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Line2D;
import java.util.EnumSet;
import java.util.Set;
import javax.swing.JComponent;

/** A strip of small hulls, one per ship type; sunk ships are shown as crossed-out wrecks. */
public class FleetStatusPanel extends JComponent {

    private static final long serialVersionUID = 1L;
    private static final int CELL = 13;
    private static final int GAP = 12;

    private final Set<ShipType> sunk = EnumSet.noneOf(ShipType.class);

    public FleetStatusPanel() {
        setToolTipText("Ships afloat / sunk");
    }

    public void setSunk(ShipType type) {
        sunk.add(type);
        repaint();
    }

    public void reset() {
        sunk.clear();
        repaint();
    }

    public Set<ShipType> sunk() {
        return Set.copyOf(sunk);
    }

    public int afloat() {
        return ShipType.values().length - sunk.size();
    }

    @Override
    public Dimension getPreferredSize() {
        int width = 0;
        for (ShipType t : ShipType.values()) {
            width += t.length() * CELL + GAP;
        }
        return new Dimension(width + 80, CELL + 10);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int total = getPreferredSize().width - 80;
        g2.setFont(Theme.LABEL_FONT.deriveFont(12f));
        String text = afloat() + "/5 afloat";
        int textWidth = g2.getFontMetrics().stringWidth(text);
        double x = Math.max(0, (getWidth() - total - textWidth - GAP) / 2.0);
        double y = (getHeight() - CELL) / 2.0;
        g2.setColor(afloat() == 0 ? Theme.SUNK : Theme.TEXT_DIM);
        g2.drawString(text, (float) x, (float) (y + CELL - 2));
        x += textWidth + GAP;
        for (ShipType t : ShipType.values()) {
            boolean isSunk = sunk.contains(t);
            ShipPainter.paint(g2, t, Orientation.H, x, y, CELL,
                    isSunk ? ShipPainter.Style.WRECK : ShipPainter.Style.NORMAL);
            if (isSunk) {
                g2.setColor(Theme.SUNK);
                g2.setStroke(new BasicStroke(2f));
                g2.draw(new Line2D.Double(x, y + CELL / 2.0, x + t.length() * CELL, y + CELL / 2.0));
            }
            x += t.length() * CELL + GAP;
        }
        g2.dispose();
    }
}
