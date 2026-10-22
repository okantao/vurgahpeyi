package com.amiral.ui;

import com.amiral.model.Coordinate;
import java.awt.AlphaComposite;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

/** A custom-painted 10x10 board with row/column labels, hover tracking and click callbacks. */
public class BoardPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    /** What a cell shows. */
    public enum CellView { WATER, SHIP, HIT, SUNK, MISS, PREVIEW_OK, PREVIEW_BAD }

    private final CellView[][] cells = new CellView[Coordinate.SIZE][Coordinate.SIZE];
    private final Set<Coordinate> preview = new HashSet<>();
    private boolean previewOk;
    private boolean interactive;
    private boolean dimmed;
    private Coordinate hover;
    private transient Consumer<Coordinate> clickListener = c -> { };
    private transient Consumer<Coordinate> rightClickListener = c -> { };
    private transient Consumer<Coordinate> hoverListener = c -> { };

    public BoardPanel() {
        clear();
        setOpaque(true);
        setBackground(java.awt.Color.WHITE);
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

    public final void clear() {
        for (CellView[] row : cells) {
            java.util.Arrays.fill(row, CellView.WATER);
        }
        preview.clear();
        repaint();
    }

    public void setCell(Coordinate c, CellView v) {
        cells[c.rowIndex()][c.col() - 1] = v;
        repaint();
    }

    public CellView cell(Coordinate c) {
        return cells[c.rowIndex()][c.col() - 1];
    }

    /** Shows a placement preview over the cells (green if {@code ok}, red otherwise). Empty clears it. */
    public void setPreview(Collection<Coordinate> previewCells, boolean ok) {
        preview.clear();
        preview.addAll(previewCells);
        previewOk = ok;
        repaint();
    }

    public void setInteractive(boolean interactive) {
        this.interactive = interactive;
        setCursor(Cursor.getPredefinedCursor(interactive ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
        if (!interactive) {
            preview.clear();
        }
        repaint();
    }

    public boolean isInteractive() {
        return interactive;
    }

    /** Paints the board faded (used for the enemy board when it is not our turn). */
    public void setDimmed(boolean dimmed) {
        this.dimmed = dimmed;
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

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(Theme.BOARD_PREFERRED, Theme.BOARD_PREFERRED);
    }

    @Override
    public Dimension getMinimumSize() {
        return new Dimension(220, 220);
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

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int size = cellSize();
        int ox = originX();
        int oy = originY();

        g2.setFont(Theme.LABEL_FONT);
        g2.setColor(Theme.LABEL);
        FontMetrics fm = g2.getFontMetrics();
        for (int i = 1; i <= Coordinate.SIZE; i++) {
            String col = Integer.toString(i);
            g2.drawString(col, ox + i * size + (size - fm.stringWidth(col)) / 2,
                    oy + (size + fm.getAscent() - fm.getDescent()) / 2);
            String row = String.valueOf((char) ('A' + i - 1));
            g2.drawString(row, ox + (size - fm.stringWidth(row)) / 2,
                    oy + i * size + (size + fm.getAscent() - fm.getDescent()) / 2);
        }

        if (dimmed) {
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.6f));
        }
        for (int r = 0; r < Coordinate.SIZE; r++) {
            for (int c = 0; c < Coordinate.SIZE; c++) {
                Coordinate coord = Coordinate.of(r, c + 1);
                CellView view = cells[r][c];
                if (preview.contains(coord)) {
                    view = previewOk ? CellView.PREVIEW_OK : CellView.PREVIEW_BAD;
                }
                paintCell(g2, view, ox + (c + 1) * size, oy + (r + 1) * size, size);
            }
        }
        if (interactive && hover != null && preview.isEmpty()) {
            g2.setColor(Theme.LABEL);
            g2.drawRect(ox + hover.col() * size + 1, oy + (hover.rowIndex() + 1) * size + 1, size - 2, size - 2);
        }
        g2.dispose();
    }

    private static void paintCell(Graphics2D g2, CellView view, int x, int y, int size) {
        g2.setColor(switch (view) {
            case WATER -> Theme.WATER;
            case SHIP -> Theme.SHIP;
            case HIT -> Theme.HIT;
            case SUNK -> Theme.SUNK;
            case MISS -> Theme.MISS;
            case PREVIEW_OK -> Theme.PREVIEW_OK;
            case PREVIEW_BAD -> Theme.PREVIEW_BAD;
        });
        g2.fillRect(x, y, size, size);
        g2.setColor(Theme.GRID);
        g2.drawRect(x, y, size, size);
        int dot = Math.max(4, size / 4);
        if (view == CellView.HIT) {
            g2.setColor(java.awt.Color.WHITE);
            g2.fillOval(x + (size - dot) / 2, y + (size - dot) / 2, dot, dot);
        } else if (view == CellView.MISS) {
            g2.setColor(Theme.MISS_DOT);
            g2.fillOval(x + (size - dot) / 2, y + (size - dot) / 2, dot, dot);
        }
    }
}
