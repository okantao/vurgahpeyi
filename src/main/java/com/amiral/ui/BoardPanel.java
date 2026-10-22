package com.amiral.ui;

import com.amiral.model.Coordinate;
import com.amiral.model.Orientation;
import com.amiral.model.Ship;
import com.amiral.model.ShipType;
import com.amiral.model.Weapon;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.MultipleGradientPaint;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/**
 * A custom-painted 10x10 sea with row/column labels. Draws ship hulls, hits, misses, radar finds,
 * placement and weapon previews, and animates shots, torpedoes and the radar pulse.
 */
public class BoardPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    /** What a cell shows. SHIP is kept for boards without hull data; the PREVIEW values are not stored. */
    public enum CellView { WATER, SHIP, HIT, SUNK, MISS, PREVIEW_OK, PREVIEW_BAD }

    private static final int FRAME_MILLIS = 33;
    private static final long RADAR_SWEEP = 950;
    private static final long RADAR_TOTAL = 1900;
    private static final long TORPEDO_RUN = 650;
    private static final long TORPEDO_TOTAL = 1500;
    private static final long SHOT_TOTAL = 650;

    private final CellView[][] cells = new CellView[Coordinate.SIZE][Coordinate.SIZE];
    /** Time (ms) from which a cell's state is shown; earlier the cell still looks like water. */
    private final long[][] visibleFrom = new long[Coordinate.SIZE][Coordinate.SIZE];
    private final List<Ship> ships = new ArrayList<>();
    private final Map<Coordinate, Long> detected = new HashMap<>();
    private final Set<Coordinate> scanned = new HashSet<>();
    private final Set<Coordinate> preview = new HashSet<>();
    private final transient List<Animation> animations = new ArrayList<>();
    private boolean previewOk;
    private Weapon previewWeapon;
    private transient Ship ghost;
    private boolean ghostOk;
    private boolean interactive;
    private boolean dimmed;
    private boolean highlight;
    private Coordinate hover;
    private transient Consumer<Coordinate> clickListener = c -> { };
    private transient Consumer<Coordinate> rightClickListener = c -> { };
    private transient Consumer<Coordinate> hoverListener = c -> { };
    private final transient Timer timer = new Timer(FRAME_MILLIS, e -> repaint());

    public BoardPanel() {
        clear();
        setOpaque(true);
        setBackground(Theme.BACKGROUND);
        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                updateHover(cellAt(e.getX(), e.getY()));
            }

            @Override
            public void mouseExited(MouseEvent e) {
                updateHover(null);
            }

            @Override
            public void mousePressed(MouseEvent e) {
                Coordinate c = cellAt(e.getX(), e.getY());
                if (c == null || !interactive) {
                    return;
                }
                if (SwingUtilities.isRightMouseButton(e)) {
                    rightClickListener.accept(c);
                } else if (SwingUtilities.isLeftMouseButton(e)) {
                    clickListener.accept(c);
                }
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
    }

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

    private static long now() {
        return System.nanoTime() / 1_000_000;
    }

    private void updateHover(Coordinate c) {
        if (c == null ? hover == null : c.equals(hover)) {
            return;
        }
        hover = c;
        if (interactive) {
            hoverListener.accept(c);
        }
        repaint();
    }

    // ---------------------------------------------------------------- state

    /** Back to an empty sea: no ships, shots, finds or animations. */
    public final void clear() {
        for (int r = 0; r < Coordinate.SIZE; r++) {
            java.util.Arrays.fill(cells[r], CellView.WATER);
            java.util.Arrays.fill(visibleFrom[r], 0);
        }
        ships.clear();
        detected.clear();
        scanned.clear();
        preview.clear();
        ghost = null;
        animations.clear();
        repaint();
    }

    public void setCell(Coordinate c, CellView v) {
        setCell(c, v, 0);
    }

    /** Sets a cell but shows it only after {@code delayMillis} (so an animation can land first). */
    public void setCell(Coordinate c, CellView v, long delayMillis) {
        cells[c.rowIndex()][c.col() - 1] = v;
        visibleFrom[c.rowIndex()][c.col() - 1] = delayMillis <= 0 ? 0 : now() + delayMillis;
        repaint();
    }

    public CellView cell(Coordinate c) {
        return cells[c.rowIndex()][c.col() - 1];
    }

    /** Hulls drawn on the sea (own fleet, or enemy ships once sunk). */
    public void setShips(Collection<Ship> newShips) {
        ships.clear();
        ships.addAll(newShips);
        repaint();
    }

    public void addShip(Ship s) {
        ships.removeIf(old -> old.type() == s.type());
        ships.add(s);
        repaint();
    }

    /** Adds the ship covering {@code cellsOfShip} (as reported for a sunk ship). */
    public void addShip(ShipType type, List<Coordinate> cellsOfShip) {
        if (cellsOfShip.isEmpty()) {
            return;
        }
        Coordinate start = cellsOfShip.stream()
                .min(Comparator.comparingInt(Coordinate::rowIndex).thenComparingInt(Coordinate::col)).orElseThrow();
        boolean vertical = cellsOfShip.stream().anyMatch(c -> c.col() == start.col() && c.rowIndex() != start.rowIndex());
        addShip(new Ship(type, start, vertical ? Orientation.V : Orientation.H));
    }

    public List<Ship> ships() {
        return List.copyOf(ships);
    }

    /** Radar finds on the enemy board; each appears after its own delay (when the pulse reaches it). */
    public void markDetected(Map<Coordinate, Long> delays) {
        long t = now();
        delays.forEach((c, d) -> detected.putIfAbsent(c, t + d));
        repaint();
    }

    public boolean isDetected(Coordinate c) {
        return detected.containsKey(c);
    }

    public void markScanned(Collection<Coordinate> area) {
        scanned.addAll(area);
        repaint();
    }

    /** Highlights cells (weapon area or an invalid placement), green if {@code ok}, red otherwise. */
    public void setPreview(Collection<Coordinate> previewCells, boolean ok) {
        setPreview(previewCells, ok, null);
    }

    public void setPreview(Collection<Coordinate> previewCells, boolean ok, Weapon weapon) {
        preview.clear();
        preview.addAll(previewCells);
        previewOk = ok;
        previewWeapon = weapon;
        ghost = null;
        repaint();
    }

    /** Shows a see-through hull where a ship would be placed. */
    public void setGhost(Ship ship, boolean ok) {
        preview.clear();
        ghost = ship;
        ghostOk = ok;
        repaint();
    }

    public void setInteractive(boolean interactive) {
        this.interactive = interactive;
        setCursor(Cursor.getPredefinedCursor(interactive ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
        if (!interactive) {
            preview.clear();
            ghost = null;
        }
        repaint();
    }

    public boolean isInteractive() {
        return interactive;
    }

    /** Darkens the board (enemy board while it is not our turn). */
    public void setDimmed(boolean dimmed) {
        this.dimmed = dimmed;
        repaint();
    }

    /** Glowing frame (enemy board on our turn). */
    public void setHighlight(boolean highlight) {
        this.highlight = highlight;
        repaint();
    }

    public void setCellClickListener(Consumer<Coordinate> listener) {
        this.clickListener = listener;
    }

    public void setRightClickListener(Consumer<Coordinate> listener) {
        this.rightClickListener = listener;
    }

    public void setHoverListener(Consumer<Coordinate> listener) {
        this.hoverListener = listener;
    }

    public Coordinate hoverCell() {
        return hover;
    }

    // ---------------------------------------------------------------- animations

    /**
     * Radar pulse in the style of an "Oracle Lens" sweep: a red ring bursts out from the centre of the area,
     * a sweep arm turns, and every ship cell lights up when the ring reaches it. Returns the reveal delay
     * for each found cell, so the caller can mark them with {@link #markDetected}.
     */
    public Map<Coordinate, Long> playRadar(List<Coordinate> area, Collection<Coordinate> found) {
        Animation a = new Animation(Animation.Kind.RADAR, area, null);
        animations.add(a);
        Map<Coordinate, Long> delays = new HashMap<>();
        Point2D centre = areaCentre(area);
        double maxDist = 0;
        for (Coordinate c : area) {
            maxDist = Math.max(maxDist, centre.distance(c.col() + 0.5, c.rowIndex() + 1.5));
        }
        for (Coordinate c : found) {
            double d = centre.distance(c.col() + 0.5, c.rowIndex() + 1.5) / Math.max(0.01, maxDist);
            delays.put(c, (long) (RADAR_SWEEP * easeOutInverse(Math.min(1, d))));
        }
        repaint();
        return delays;
    }

    /** Torpedo runs in from the board edge to the target row, then the 3x3 area explodes. */
    public void playTorpedo(List<Coordinate> area, Coordinate anchor) {
        animations.add(new Animation(Animation.Kind.TORPEDO, area, anchor));
        repaint();
    }

    /** Shell drop on one cell, then an explosion (hit) or a splash (miss). */
    public void playShot(Coordinate c, boolean hit) {
        animations.add(new Animation(hit ? Animation.Kind.SHOT_HIT : Animation.Kind.SHOT_MISS, List.of(c), c));
        repaint();
    }

    /** How long until a torpedo's explosion reaches a cell. */
    public static long torpedoImpactDelay() {
        return TORPEDO_RUN;
    }

    public static long shotImpactDelay() {
        return SHOT_TOTAL / 3;
    }

    public boolean isAnimating() {
        return !animations.isEmpty();
    }

    private static double easeOut(double t) {
        return 1 - Math.pow(1 - t, 3);
    }

    /** t such that easeOut(t) = v. */
    private static double easeOutInverse(double v) {
        return 1 - Math.cbrt(1 - v);
    }

    /** One running effect. */
    private static final class Animation {
        enum Kind { RADAR, TORPEDO, SHOT_HIT, SHOT_MISS }

        final Kind kind;
        final List<Coordinate> area;
        final Coordinate anchor;
        final long start = now();

        Animation(Kind kind, List<Coordinate> area, Coordinate anchor) {
            this.kind = kind;
            this.area = area;
            this.anchor = anchor;
        }

        long duration() {
            return switch (kind) {
                case RADAR -> RADAR_TOTAL;
                case TORPEDO -> TORPEDO_TOTAL;
                default -> SHOT_TOTAL;
            };
        }
    }

    // ---------------------------------------------------------------- geometry

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(Theme.BOARD_PREFERRED, Theme.BOARD_PREFERRED);
    }

    @Override
    public Dimension getMinimumSize() {
        return new Dimension(240, 240);
    }

    private int cellSize() {
        return Math.max(1, Math.min(getWidth(), getHeight()) / (Coordinate.SIZE + 1));
    }

    private int originX() {
        return (getWidth() - cellSize() * (Coordinate.SIZE + 1)) / 2;
    }

    private int originY() {
        return (getHeight() - cellSize() * (Coordinate.SIZE + 1)) / 2;
    }

    /** The board cell under a pixel, or null for the labels and the margin. */
    public Coordinate cellAt(int x, int y) {
        int size = cellSize();
        int col = (x - originX()) / size;
        int row = (y - originY()) / size;
        if (x < originX() || y < originY() || col < 1 || row < 1 || col > Coordinate.SIZE || row > Coordinate.SIZE) {
            return null;
        }
        return Coordinate.of(row - 1, col);
    }

    /** Centre of an area in cell units (column 1 starts at x = 1, row A at y = 1). */
    private static Point2D areaCentre(List<Coordinate> area) {
        double x = 0;
        double y = 0;
        for (Coordinate c : area) {
            x += c.col() + 0.5;
            y += c.rowIndex() + 1.5;
        }
        return new Point2D.Double(x / area.size(), y / area.size());
    }

    // ---------------------------------------------------------------- painting

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        long t = now();
        int s = cellSize();
        int ox = originX();
        int oy = originY();
        double shake = shakeOffset(t, s);
        g2.translate(shake, shake * 0.6);

        paintLabels(g2, ox, oy, s);
        Rectangle2D sea = new Rectangle2D.Double(ox + s, oy + s, s * 10.0, s * 10.0);
        paintSea(g2, sea, s, t);
        paintScanned(g2, ox, oy, s);
        for (Ship ship : ships) {
            ShipPainter.paint(g2, ship.type(), ship.orientation(), ox + ship.start().col() * (double) s,
                    oy + (ship.start().rowIndex() + 1) * (double) s, s,
                    isSunk(ship) ? ShipPainter.Style.WRECK : ShipPainter.Style.NORMAL);
        }
        paintCells(g2, ox, oy, s, t);
        paintDetected(g2, ox, oy, s, t);
        paintPreview(g2, ox, oy, s, t);
        paintAnimations(g2, ox, oy, s, t);

        g2.setClip(null);
        if (dimmed) {
            g2.setColor(new Color(4, 8, 16, 120));
            g2.fill(sea);
        }
        paintFrame(g2, sea, t);
        g2.dispose();
    }

    private double shakeOffset(long t, int s) {
        for (Animation a : animations) {
            long age = t - a.start;
            if (a.kind == Animation.Kind.TORPEDO && age > TORPEDO_RUN && age < TORPEDO_RUN + 260) {
                double strength = 1 - (age - TORPEDO_RUN) / 260.0;
                return Math.sin(age * 0.9) * s * 0.08 * strength;
            }
        }
        return 0;
    }

    private void paintLabels(Graphics2D g2, int ox, int oy, int s) {
        g2.setFont(Theme.LABEL_FONT.deriveFont((float) Math.max(10, s * 0.36)));
        g2.setColor(Theme.LABEL);
        FontMetrics fm = g2.getFontMetrics();
        for (int i = 1; i <= Coordinate.SIZE; i++) {
            String col = Integer.toString(i);
            g2.drawString(col, ox + i * s + (s - fm.stringWidth(col)) / 2,
                    oy + (s + fm.getAscent() - fm.getDescent()) / 2 + s / 6);
            String row = String.valueOf((char) ('A' + i - 1));
            g2.drawString(row, ox + (s - fm.stringWidth(row)) / 2 + s / 6,
                    oy + i * s + (s + fm.getAscent() - fm.getDescent()) / 2);
        }
    }

    private void paintSea(Graphics2D g2, Rectangle2D sea, int s, long t) {
        g2.setPaint(new GradientPaint(0, (float) sea.getY(), Theme.OCEAN_TOP, 0, (float) sea.getMaxY(),
                Theme.OCEAN_BOTTOM));
        g2.fill(sea);
        // Gentle moving light on the waves.
        Shape oldClip = g2.getClip();
        g2.clip(sea);
        g2.setStroke(new BasicStroke(Math.max(1f, s * 0.05f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        double phase = t / 900.0;
        for (int i = 0; i < 14; i++) {
            double y = sea.getY() + (i + 0.5) * sea.getHeight() / 14;
            Path2D wave = new Path2D.Double();
            wave.moveTo(sea.getX(), y);
            for (double x = 0; x <= sea.getWidth(); x += s / 3.0) {
                wave.lineTo(sea.getX() + x, y + Math.sin(x / s * 1.3 + phase + i * 0.7) * s * 0.07);
            }
            int a = (int) (14 + 10 * Math.sin(phase * 0.7 + i));
            g2.setColor(new Color(150, 200, 255, Math.max(4, a)));
            g2.draw(wave);
        }
        g2.setClip(oldClip);
        g2.setStroke(new BasicStroke(1f));
        g2.setColor(Theme.GRID);
        for (int i = 0; i <= Coordinate.SIZE; i++) {
            g2.draw(new Line2D.Double(sea.getX() + i * s, sea.getY(), sea.getX() + i * s, sea.getMaxY()));
            g2.draw(new Line2D.Double(sea.getX(), sea.getY() + i * s, sea.getMaxX(), sea.getY() + i * s));
        }
    }

    private void paintScanned(Graphics2D g2, int ox, int oy, int s) {
        g2.setColor(Theme.alpha(Theme.RADAR, 26));
        for (Coordinate c : scanned) {
            g2.fill(new Rectangle2D.Double(ox + c.col() * s + 1, oy + (c.rowIndex() + 1) * s + 1, s - 1, s - 1));
        }
    }

    private boolean isSunk(Ship ship) {
        for (Coordinate c : ship.cells()) {
            if (cell(c) != CellView.SUNK || now() < visibleFrom[c.rowIndex()][c.col() - 1]) {
                return false;
            }
        }
        return true;
    }

    private boolean hasHull(Coordinate c) {
        for (Ship ship : ships) {
            if (ship.occupies(c)) {
                return true;
            }
        }
        return false;
    }

    private void paintCells(Graphics2D g2, int ox, int oy, int s, long t) {
        for (int r = 0; r < Coordinate.SIZE; r++) {
            for (int c = 0; c < Coordinate.SIZE; c++) {
                CellView v = cells[r][c];
                if (v == CellView.WATER || t < visibleFrom[r][c]) {
                    continue;
                }
                double x = ox + (c + 1) * (double) s;
                double y = oy + (r + 1) * (double) s;
                Coordinate coord = Coordinate.of(r, c + 1);
                switch (v) {
                    case SHIP -> {
                        if (!hasHull(coord)) {
                            g2.setColor(Theme.SHIP);
                            g2.fill(new RoundRectangle2D.Double(x + s * 0.1, y + s * 0.18, s * 0.8, s * 0.64, 6, 6));
                        }
                    }
                    case MISS -> paintMiss(g2, x, y, s);
                    case HIT -> paintFire(g2, x, y, s, t, r * 10 + c, !hasHull(coord));
                    case SUNK -> {
                        if (!hasHull(coord)) {
                            g2.setColor(Theme.alpha(Theme.SUNK, 140));
                            g2.fill(new Rectangle2D.Double(x + 1, y + 1, s - 1, s - 1));
                        }
                        paintSmoke(g2, x, y, s, t, r * 10 + c);
                    }
                    default -> {
                    }
                }
            }
        }
    }

    private static void paintMiss(Graphics2D g2, double x, double y, int s) {
        double cx = x + s / 2.0;
        double cy = y + s / 2.0;
        g2.setStroke(new BasicStroke(Math.max(1f, s * 0.04f)));
        g2.setColor(Theme.alpha(Theme.MISS, 90));
        g2.draw(new Ellipse2D.Double(cx - s * 0.32, cy - s * 0.32, s * 0.64, s * 0.64));
        g2.setColor(Theme.alpha(Theme.MISS, 150));
        g2.draw(new Ellipse2D.Double(cx - s * 0.19, cy - s * 0.19, s * 0.38, s * 0.38));
        g2.setColor(Theme.MISS_DOT);
        g2.fill(new Ellipse2D.Double(cx - s * 0.07, cy - s * 0.07, s * 0.14, s * 0.14));
    }

    private static void paintFire(Graphics2D g2, double x, double y, int s, long t, int seed, boolean onWater) {
        double cx = x + s / 2.0;
        double cy = y + s / 2.0;
        if (onWater) {
            g2.setColor(Theme.alpha(Theme.HIT, 70));
            g2.fill(new Rectangle2D.Double(x + 1, y + 1, s - 1, s - 1));
        }
        double flicker = 1 + 0.12 * Math.sin(t / 90.0 + seed * 1.7) + 0.06 * Math.sin(t / 37.0 + seed);
        float r = (float) (s * 0.42 * flicker);
        g2.setPaint(new RadialGradientPaint(new Point2D.Double(cx, cy + s * 0.05), r,
                new float[] {0f, 0.35f, 0.7f, 1f},
                new Color[] {new Color(255, 245, 200, 240), Theme.FIRE, Theme.alpha(Theme.HIT, 200),
                    new Color(255, 60, 30, 0)}));
        g2.fill(new Ellipse2D.Double(cx - r, cy - r, r * 2, r * 2));
        g2.setColor(new Color(255, 255, 255, 220));
        g2.fill(new Ellipse2D.Double(cx - s * 0.06, cy - s * 0.06, s * 0.12, s * 0.12));
    }

    private static void paintSmoke(Graphics2D g2, double x, double y, int s, long t, int seed) {
        for (int i = 0; i < 2; i++) {
            double phase = ((t / 1400.0) + i * 0.5 + seed * 0.13) % 1.0;
            double r = s * (0.15 + 0.2 * phase);
            double cx = x + s * (0.45 + 0.15 * Math.sin(seed + i)) + phase * s * 0.15;
            double cy = y + s * 0.55 - phase * s * 0.45;
            g2.setColor(new Color(40, 40, 45, (int) (110 * (1 - phase))));
            g2.fill(new Ellipse2D.Double(cx - r, cy - r, r * 2, r * 2));
        }
    }

    private void paintDetected(Graphics2D g2, int ox, int oy, int s, long t) {
        for (Map.Entry<Coordinate, Long> e : detected.entrySet()) {
            Coordinate c = e.getKey();
            long age = t - e.getValue();
            if (age < 0 || cell(c) == CellView.HIT || cell(c) == CellView.SUNK) {
                continue;
            }
            double cx = ox + c.col() * (double) s + s / 2.0;
            double cy = oy + (c.rowIndex() + 1) * (double) s + s / 2.0;
            double pop = age < 260 ? 1 + 0.8 * (1 - age / 260.0) : 1;
            double pulse = 0.5 + 0.5 * Math.sin(t / 260.0 + c.col());
            // Glow
            float gr = (float) (s * 0.62 * pop);
            g2.setPaint(new RadialGradientPaint(new Point2D.Double(cx, cy), gr, new float[] {0f, 1f},
                    new Color[] {Theme.alpha(Theme.RADAR, 90 + 60 * pulse), Theme.alpha(Theme.RADAR, 0)}));
            g2.fill(new Ellipse2D.Double(cx - gr, cy - gr, gr * 2, gr * 2));
            // Diamond marker
            double d = s * 0.22 * pop;
            Path2D diamond = new Path2D.Double();
            diamond.moveTo(cx, cy - d);
            diamond.lineTo(cx + d, cy);
            diamond.lineTo(cx, cy + d);
            diamond.lineTo(cx - d, cy);
            diamond.closePath();
            g2.setColor(Theme.alpha(Theme.RADAR, 230));
            g2.fill(diamond);
            g2.setColor(new Color(255, 230, 220));
            g2.setStroke(new BasicStroke(Math.max(1f, s * 0.04f)));
            g2.draw(diamond);
            // Pulsing ring
            double ring = s * (0.28 + 0.14 * pulse) * pop;
            g2.setColor(Theme.alpha(Theme.RADAR_GLOW, 120 + 100 * (1 - pulse)));
            g2.draw(new Ellipse2D.Double(cx - ring, cy - ring, ring * 2, ring * 2));
        }
    }

    private void paintPreview(Graphics2D g2, int ox, int oy, int s, long t) {
        if (ghost != null) {
            ShipPainter.paint(g2, ghost.type(), ghost.orientation(), ox + ghost.start().col() * (double) s,
                    oy + (ghost.start().rowIndex() + 1) * (double) s, s,
                    ghostOk ? ShipPainter.Style.GHOST_OK : ShipPainter.Style.GHOST_BAD);
        }
        if (!preview.isEmpty()) {
            Color base = !previewOk ? Theme.PREVIEW_BAD
                    : previewWeapon == Weapon.RADAR ? Theme.RADAR
                    : previewWeapon == Weapon.TORPEDO ? Theme.TORPEDO : Theme.PREVIEW_OK;
            g2.setColor(Theme.alpha(base, previewWeapon == null ? 120 : 55));
            Rectangle2D bounds = null;
            for (Coordinate c : preview) {
                Rectangle2D r = new Rectangle2D.Double(ox + c.col() * s, oy + (c.rowIndex() + 1) * s, s, s);
                g2.fill(r);
                bounds = bounds == null ? r : bounds.createUnion(r);
            }
            if (previewWeapon != null && bounds != null) {
                float dash = (float) (t / 40.0 % 12);
                g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10,
                        new float[] {7f, 5f}, dash));
                g2.setColor(Theme.alpha(base, 230));
                g2.draw(bounds);
            }
        }
        if (interactive && hover != null && ghost == null && (preview.isEmpty() || previewWeapon != null)) {
            paintReticle(g2, ox + hover.col() * (double) s + s / 2.0, oy + (hover.rowIndex() + 1) * (double) s
                    + s / 2.0, s, t);
        }
    }

    private static void paintReticle(Graphics2D g2, double cx, double cy, int s, long t) {
        double r = s * (0.34 + 0.03 * Math.sin(t / 150.0));
        g2.setColor(Theme.RETICLE);
        g2.setStroke(new BasicStroke(Math.max(1.5f, s * 0.05f)));
        g2.draw(new Ellipse2D.Double(cx - r, cy - r, r * 2, r * 2));
        g2.draw(new Line2D.Double(cx - r * 1.3, cy, cx - r * 0.45, cy));
        g2.draw(new Line2D.Double(cx + r * 0.45, cy, cx + r * 1.3, cy));
        g2.draw(new Line2D.Double(cx, cy - r * 1.3, cx, cy - r * 0.45));
        g2.draw(new Line2D.Double(cx, cy + r * 0.45, cx, cy + r * 1.3));
    }

    private void paintFrame(Graphics2D g2, Rectangle2D sea, long t) {
        if (highlight) {
            double pulse = 0.5 + 0.5 * Math.sin(t / 300.0);
            for (int i = 3; i >= 1; i--) {
                g2.setStroke(new BasicStroke(i * 3f));
                g2.setColor(Theme.alpha(Theme.YOUR_TURN, (40 + 50 * pulse) / i));
                g2.draw(sea);
            }
        }
        g2.setStroke(new BasicStroke(1.5f));
        g2.setColor(highlight ? Theme.YOUR_TURN : Theme.BOARD_EDGE);
        g2.draw(sea);
    }

    private void paintAnimations(Graphics2D g2, int ox, int oy, int s, long t) {
        Shape oldClip = g2.getClip();
        g2.clip(new Rectangle2D.Double(ox + s, oy + s, s * 10.0, s * 10.0));
        for (Iterator<Animation> it = animations.iterator(); it.hasNext();) {
            Animation a = it.next();
            long age = t - a.start;
            if (age > a.duration()) {
                it.remove();
                continue;
            }
            switch (a.kind) {
                case RADAR -> paintRadar(g2, a, age, ox, oy, s);
                case TORPEDO -> paintTorpedo(g2, a, age, ox, oy, s);
                case SHOT_HIT, SHOT_MISS -> paintShot(g2, a, age, ox, oy, s);
            }
        }
        g2.setClip(oldClip);
    }

    private static void paintRadar(Graphics2D g2, Animation a, long age, int ox, int oy, int s) {
        Point2D centreCells = areaCentre(a.area);
        double cx = ox + centreCells.getX() * s;
        double cy = oy + centreCells.getY() * s;
        double maxR = 0;
        Rectangle2D bounds = null;
        for (Coordinate c : a.area) {
            Rectangle2D r = new Rectangle2D.Double(ox + c.col() * s, oy + (c.rowIndex() + 1) * s, s, s);
            bounds = bounds == null ? r : bounds.createUnion(r);
            maxR = Math.max(maxR, Point2D.distance(cx, cy, r.getCenterX(), r.getCenterY()) + s * 0.5);
        }
        double fadeOut = age < RADAR_SWEEP ? 1 : Math.max(0, 1 - (age - RADAR_SWEEP) / (double) (RADAR_TOTAL - RADAR_SWEEP));

        // Area outline with running dashes.
        g2.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, new float[] {8f, 6f},
                (float) (age / 30.0 % 14)));
        g2.setColor(Theme.alpha(Theme.RADAR, 220 * fadeOut));
        g2.draw(bounds);
        g2.setColor(Theme.alpha(Theme.RADAR, 30 * fadeOut));
        g2.fill(bounds);

        // Expanding lens: translucent disc with a bright, glowing rim and an echo ring.
        for (int echo = 0; echo < 2; echo++) {
            long local = age - echo * 200L;
            if (local <= 0) {
                continue;
            }
            double p = Math.min(1, local / (double) RADAR_SWEEP);
            double r = maxR * easeOut(p);
            double alpha = (echo == 0 ? 1 : 0.55) * (p < 1 ? 1 : fadeOut);
            if (r < 1) {
                continue;
            }
            if (echo == 0) {
                g2.setPaint(new RadialGradientPaint(new Point2D.Double(cx, cy), (float) r,
                        new float[] {0f, 0.75f, 1f},
                        new Color[] {Theme.alpha(Theme.RADAR, 10 * alpha), Theme.alpha(Theme.RADAR, 45 * alpha),
                            Theme.alpha(Theme.RADAR_GLOW, 110 * alpha)},
                        MultipleGradientPaint.CycleMethod.NO_CYCLE));
                g2.fill(new Ellipse2D.Double(cx - r, cy - r, r * 2, r * 2));
            }
            for (int w = 3; w >= 1; w--) {
                g2.setStroke(new BasicStroke(w * (echo == 0 ? 3.2f : 2f)));
                g2.setColor(Theme.alpha(w == 1 ? new Color(255, 225, 215) : Theme.RADAR, alpha * (w == 1 ? 240 : 70.0 / w)));
                g2.draw(new Ellipse2D.Double(cx - r, cy - r, r * 2, r * 2));
            }
        }

        // Rotating sweep arm with a fading wedge behind it.
        if (age < RADAR_TOTAL - 200) {
            double angle = -90 - age * 360.0 / 900;
            double r = maxR * easeOut(Math.min(1, age / (double) RADAR_SWEEP));
            for (int i = 0; i < 6; i++) {
                g2.setColor(Theme.alpha(Theme.RADAR, (60 - i * 10) * fadeOut));
                g2.fill(new Arc2D.Double(cx - r, cy - r, r * 2, r * 2, angle + i * 9, 9, Arc2D.PIE));
            }
            g2.setStroke(new BasicStroke(2f));
            g2.setColor(Theme.alpha(new Color(255, 220, 210), 220 * fadeOut));
            double rad = Math.toRadians(-angle);
            g2.draw(new Line2D.Double(cx, cy, cx + Math.cos(rad) * r, cy + Math.sin(rad) * r));
        }

        // Bright core flash at the start.
        if (age < 300) {
            double f = 1 - age / 300.0;
            float r = (float) (s * (0.4 + 0.6 * (1 - f)));
            g2.setPaint(new RadialGradientPaint(new Point2D.Double(cx, cy), r, new float[] {0f, 1f},
                    new Color[] {new Color(255, 240, 230, (int) (230 * f)), new Color(255, 80, 60, 0)}));
            g2.fill(new Ellipse2D.Double(cx - r, cy - r, r * 2, r * 2));
        }
    }

    private static void paintTorpedo(Graphics2D g2, Animation a, long age, int ox, int oy, int s) {
        Coordinate centreCell = a.area.get(a.area.size() / 2);
        double tx = ox + centreCell.col() * (double) s + s / 2.0;
        double ty = oy + (centreCell.rowIndex() + 1) * (double) s + s / 2.0;
        double startX = ox + s - s * 0.8;
        if (age < TORPEDO_RUN) {
            double p = age / (double) TORPEDO_RUN;
            double x = startX + (tx - startX) * (p * p * (3 - 2 * p));
            // Wake
            g2.setStroke(new BasicStroke((float) (s * 0.22), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.setPaint(new GradientPaint((float) startX, (float) ty, new Color(255, 255, 255, 0), (float) x,
                    (float) ty, new Color(220, 240, 255, 150)));
            g2.draw(new Line2D.Double(startX, ty, x - s * 0.3, ty));
            for (int i = 0; i < 6; i++) {
                double bx = x - s * (0.5 + i * 0.35);
                double by = ty + Math.sin(age / 40.0 + i) * s * 0.12;
                double br = s * (0.05 + 0.03 * (i % 3));
                g2.setColor(new Color(220, 240, 255, Math.max(0, 160 - i * 25)));
                g2.fill(new Ellipse2D.Double(bx - br, by - br, br * 2, br * 2));
            }
            // Body
            RoundRectangle2D body = new RoundRectangle2D.Double(x - s * 0.55, ty - s * 0.1, s * 0.6, s * 0.2,
                    s * 0.2, s * 0.2);
            g2.setPaint(new GradientPaint(0, (float) (ty - s * 0.1), new Color(190, 200, 210), 0,
                    (float) (ty + s * 0.1), new Color(70, 80, 95)));
            g2.fill(body);
            g2.setColor(Theme.TORPEDO);
            g2.fill(new Ellipse2D.Double(x - s * 0.06, ty - s * 0.08, s * 0.16, s * 0.16));
            return;
        }
        long boom = age - TORPEDO_RUN;
        double total = TORPEDO_TOTAL - TORPEDO_RUN;
        for (Coordinate c : a.area) {
            double cx = ox + c.col() * (double) s + s / 2.0;
            double cy = oy + (c.rowIndex() + 1) * (double) s + s / 2.0;
            double delay = Point2D.distance(cx, cy, tx, ty) / s * 70;
            double p = (boom - delay) / (total * 0.6);
            if (p <= 0 || p >= 1) {
                continue;
            }
            float r = (float) (s * (0.25 + 0.6 * easeOut(p)));
            int alpha = (int) (255 * (1 - p));
            g2.setPaint(new RadialGradientPaint(new Point2D.Double(cx, cy), r, new float[] {0f, 0.3f, 0.7f, 1f},
                    new Color[] {new Color(255, 255, 230, alpha), Theme.alpha(Theme.FIRE, alpha),
                        Theme.alpha(Theme.HIT, alpha * 0.8), new Color(120, 20, 10, 0)}));
            g2.fill(new Ellipse2D.Double(cx - r, cy - r, r * 2, r * 2));
        }
        double p = boom / total;
        double ring = s * (0.5 + 2.4 * easeOut(p));
        g2.setStroke(new BasicStroke((float) (s * 0.12 * (1 - p) + 1)));
        g2.setColor(new Color(255, 240, 220, (int) (200 * (1 - p))));
        g2.draw(new Ellipse2D.Double(tx - ring, ty - ring, ring * 2, ring * 2));
        if (boom < 160) {
            g2.setColor(new Color(255, 250, 235, (int) (150 * (1 - boom / 160.0))));
            g2.fill(new Rectangle2D.Double(tx - s * 1.5, ty - s * 1.5, s * 3, s * 3));
        }
    }

    private static void paintShot(Graphics2D g2, Animation a, long age, int ox, int oy, int s) {
        double cx = ox + a.anchor.col() * (double) s + s / 2.0;
        double cy = oy + (a.anchor.rowIndex() + 1) * (double) s + s / 2.0;
        long drop = SHOT_TOTAL / 3;
        if (age < drop) {
            double p = age / (double) drop;
            double r = s * (1.1 - 0.85 * p);
            g2.setStroke(new BasicStroke(2f));
            g2.setColor(Theme.alpha(Theme.RETICLE, 120 + 120 * p));
            g2.draw(new Ellipse2D.Double(cx - r, cy - r, r * 2, r * 2));
            g2.fill(new Ellipse2D.Double(cx - s * 0.06, cy - s * 0.06, s * 0.12, s * 0.12));
            return;
        }
        double p = (age - drop) / (double) (SHOT_TOTAL - drop);
        if (a.kind == Animation.Kind.SHOT_HIT) {
            float r = (float) (s * (0.3 + 0.7 * easeOut(p)));
            int alpha = (int) (255 * (1 - p));
            g2.setPaint(new RadialGradientPaint(new Point2D.Double(cx, cy), r, new float[] {0f, 0.4f, 1f},
                    new Color[] {new Color(255, 255, 220, alpha), Theme.alpha(Theme.FIRE, alpha),
                        new Color(255, 60, 30, 0)}));
            g2.fill(new Ellipse2D.Double(cx - r, cy - r, r * 2, r * 2));
            g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.setColor(Theme.alpha(Theme.FIRE, alpha));
            for (int i = 0; i < 8; i++) {
                double ang = i * Math.PI / 4 + 0.3;
                double r0 = s * 0.25 + s * 0.5 * p;
                double r1 = r0 + s * 0.25 * (1 - p);
                g2.draw(new Line2D.Double(cx + Math.cos(ang) * r0, cy + Math.sin(ang) * r0,
                        cx + Math.cos(ang) * r1, cy + Math.sin(ang) * r1));
            }
        } else {
            for (int i = 0; i < 3; i++) {
                double q = p - i * 0.15;
                if (q <= 0) {
                    continue;
                }
                double r = s * (0.15 + 0.6 * easeOut(Math.min(1, q)));
                g2.setStroke(new BasicStroke(2f));
                g2.setColor(new Color(210, 235, 255, (int) (220 * Math.max(0, 1 - q))));
                g2.draw(new Ellipse2D.Double(cx - r, cy - r, r * 2, r * 2));
            }
        }
    }
}
