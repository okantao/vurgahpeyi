package com.amiral.ui;

import com.amiral.model.Orientation;
import com.amiral.model.ShipType;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;

/**
 * Draws a ship as a hull seen from above: rounded stern, pointed bow and details for each type
 * (flight deck, gun turrets, conning tower). Drawn horizontally and rotated for vertical ships.
 */
public final class ShipPainter {

    /** How the hull is drawn. */
    public enum Style { NORMAL, WRECK, GHOST_OK, GHOST_BAD }

    private ShipPainter() {
    }

    /**
     * @param x left pixel of the first cell
     * @param y top pixel of the first cell
     * @param cell cell size in pixels
     */
    public static void paint(Graphics2D g, ShipType type, Orientation o, double x, double y, double cell,
            Style style) {
        AffineTransform saved = g.getTransform();
        if (o == Orientation.V) {
            g.translate(x + cell, y);
            g.rotate(Math.PI / 2);
        } else {
            g.translate(x, y);
        }
        paintHorizontal(g, type, type.length() * cell, cell, style);
        g.setTransform(saved);
    }

    /** Hull in local coordinates: (0,0) top-left, {@code length} along x, {@code thick} along y. */
    static void paintHorizontal(Graphics2D g, ShipType type, double length, double thick, Style style) {
        boolean sub = type == ShipType.SUBMARINE;
        double padX = thick * 0.10;
        double padY = thick * (sub ? 0.26 : 0.17);
        double x0 = padX;
        double x1 = length - padX;
        double y0 = padY;
        double y1 = thick - padY;
        double cy = thick / 2;
        double bow = Math.min(thick * 0.9, length * 0.3);

        Path2D hull = new Path2D.Double();
        hull.moveTo(x0 + (y1 - y0) * 0.35, y0);
        hull.lineTo(x1 - bow, y0);
        hull.quadTo(x1 - bow * 0.25, y0, x1, cy);
        hull.quadTo(x1 - bow * 0.25, y1, x1 - bow, y1);
        hull.lineTo(x0 + (y1 - y0) * 0.35, y1);
        hull.quadTo(x0, y1, x0, cy);
        hull.quadTo(x0, y0, x0 + (y1 - y0) * 0.35, y0);
        hull.closePath();

        Color light;
        Color dark;
        switch (style) {
            case WRECK -> {
                light = Theme.WRECK_LIGHT;
                dark = Theme.WRECK_DARK;
            }
            case GHOST_OK -> {
                light = Theme.alpha(Theme.PREVIEW_OK, 170);
                dark = Theme.alpha(Theme.PREVIEW_OK.darker(), 170);
            }
            case GHOST_BAD -> {
                light = Theme.alpha(Theme.PREVIEW_BAD, 170);
                dark = Theme.alpha(Theme.PREVIEW_BAD.darker(), 170);
            }
            default -> {
                light = sub ? Theme.SUB_LIGHT : Theme.HULL_LIGHT;
                dark = sub ? Theme.SUB_DARK : Theme.HULL_DARK;
            }
        }
        // Soft shadow on the water.
        if (style == Style.NORMAL || style == Style.WRECK) {
            g.setColor(new Color(0, 0, 0, 70));
            g.translate(thick * 0.05, thick * 0.08);
            g.fill(hull);
            g.translate(-thick * 0.05, -thick * 0.08);
        }
        g.setPaint(new GradientPaint(0, (float) y0, light, 0, (float) y1, dark));
        g.fill(hull);
        g.setStroke(new BasicStroke((float) Math.max(1, thick * 0.04)));
        g.setColor(style == Style.WRECK ? Theme.SUNK : (style == Style.NORMAL ? Theme.HULL_EDGE
                : Theme.alpha(Color.WHITE, 200)));
        g.draw(hull);

        if (style == Style.GHOST_OK || style == Style.GHOST_BAD) {
            return;
        }
        details(g, type, x0, x1 - bow * 0.6, y0, y1, cy, thick, style == Style.WRECK);
        if (style == Style.WRECK) {
            g.setColor(Theme.alpha(Theme.SUNK, 200));
            g.setStroke(new BasicStroke((float) Math.max(1.5, thick * 0.06), BasicStroke.CAP_ROUND,
                    BasicStroke.JOIN_ROUND));
            for (double cx = x0 + thick * 0.5; cx < x1 - thick * 0.3; cx += thick) {
                g.draw(new Line2D.Double(cx - thick * 0.18, y0 + 2, cx + thick * 0.1, cy));
                g.draw(new Line2D.Double(cx + thick * 0.1, cy, cx - thick * 0.05, y1 - 2));
            }
        }
    }

    private static void details(Graphics2D g, ShipType type, double x0, double x1, double y0, double y1,
            double cy, double thick, boolean wreck) {
        Color deck = wreck ? Theme.WRECK_DARK : Theme.DECK;
        Color metal = wreck ? Theme.WRECK_LIGHT : Theme.HULL_LIGHT.brighter();
        double h = y1 - y0;
        switch (type) {
            case CARRIER -> {
                g.setColor(deck);
                g.fill(new RoundRectangle2D.Double(x0 + h * 0.3, y0 + h * 0.18, x1 - x0 - h * 0.4, h * 0.64,
                        h * 0.2, h * 0.2));
                g.setColor(wreck ? Theme.alpha(Color.WHITE, 60) : Theme.alpha(Color.WHITE, 170));
                g.setStroke(new BasicStroke((float) Math.max(1, thick * 0.03), BasicStroke.CAP_BUTT,
                        BasicStroke.JOIN_MITER, 10, new float[] {(float) (thick * 0.18), (float) (thick * 0.12)}, 0));
                g.draw(new Line2D.Double(x0 + h * 0.5, cy, x1 - h * 0.3, cy));
                g.setColor(metal);
                g.fill(new RoundRectangle2D.Double(x0 + (x1 - x0) * 0.62, y0 - h * 0.02, thick * 0.55, h * 0.3,
                        4, 4));
            }
            case BATTLESHIP, CRUISER, DESTROYER -> {
                int turrets = type == ShipType.BATTLESHIP ? 3 : type == ShipType.CRUISER ? 2 : 1;
                g.setColor(deck);
                g.fill(new RoundRectangle2D.Double(x0 + h * 0.4, cy - h * 0.12, x1 - x0 - h * 0.6, h * 0.24,
                        h * 0.2, h * 0.2));
                // Bridge in the middle.
                g.setColor(wreck ? Theme.WRECK_LIGHT : Theme.HULL_LIGHT.darker());
                double bridgeX = x0 + (x1 - x0) * (type == ShipType.DESTROYER ? 0.3 : 0.45);
                g.fill(new RoundRectangle2D.Double(bridgeX, cy - h * 0.3, thick * 0.38, h * 0.6, 5, 5));
                double r = h * 0.28;
                for (int i = 0; i < turrets; i++) {
                    double tx = turrets == 1 ? x0 + (x1 - x0) * 0.72
                            : i < turrets - 1 ? x1 - thick * 0.25 - i * thick * 0.75
                            : x0 + h * 0.75;
                    g.setColor(deck.darker());
                    g.fill(new Ellipse2D.Double(tx - r, cy - r, r * 2, r * 2));
                    g.setColor(metal);
                    g.setStroke(new BasicStroke((float) Math.max(1.2, thick * 0.06), BasicStroke.CAP_ROUND,
                            BasicStroke.JOIN_ROUND));
                    double dir = tx < (x0 + x1) / 2 ? -1 : 1;
                    g.draw(new Line2D.Double(tx, cy - r * 0.3, tx + dir * r * 1.8, cy - r * 0.3));
                    g.draw(new Line2D.Double(tx, cy + r * 0.3, tx + dir * r * 1.8, cy + r * 0.3));
                }
            }
            case SUBMARINE -> {
                g.setColor(wreck ? Theme.WRECK_LIGHT : Theme.SUB_LIGHT.brighter());
                double w = thick * 0.7;
                g.fill(new RoundRectangle2D.Double((x0 + x1) / 2 - w / 2, cy - h * 0.3, w, h * 0.6, h * 0.6, h * 0.6));
                g.setColor(Theme.alpha(Color.BLACK, 90));
                g.setStroke(new BasicStroke(1f));
                g.draw(new Line2D.Double(x0 + h, cy, x1 - h * 0.6, cy));
            }
        }
    }
}
