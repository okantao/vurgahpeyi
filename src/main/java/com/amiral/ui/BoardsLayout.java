package com.amiral.ui;

import com.amiral.model.Coordinate;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.LayoutManager;

/**
 * Lays out the two boards side by side: same size, same height on screen and equal free space on the left,
 * between them and on the right. Each column is a title, a square board and a strip below it.
 */
class BoardsLayout implements LayoutManager {

    private static final int MIN_MARGIN = Theme.GAP * 2;
    private static final int ROW_GAP = Theme.GAP / 2;
    private static final int CELLS = Coordinate.SIZE + 1;

    private final Component[][] columns;

    /** Each column is {title, board, bottom}. */
    BoardsLayout(Component[] left, Component[] right) {
        this.columns = new Component[][] {left, right};
    }

    @Override
    public void addLayoutComponent(String name, Component comp) {
        // Components are given in the constructor.
    }

    @Override
    public void removeLayoutComponent(Component comp) {
        // Components are given in the constructor.
    }

    private int titleHeight() {
        return Math.max(columns[0][0].getPreferredSize().height, columns[1][0].getPreferredSize().height);
    }

    private int bottomHeight() {
        return Math.max(columns[0][2].getPreferredSize().height, columns[1][2].getPreferredSize().height);
    }

    private int bottomWidth() {
        return Math.max(columns[0][2].getPreferredSize().width, columns[1][2].getPreferredSize().width);
    }

    @Override
    public Dimension preferredLayoutSize(Container parent) {
        return size(parent, Theme.BOARD_PREFERRED);
    }

    @Override
    public Dimension minimumLayoutSize(Container parent) {
        return size(parent, 240);
    }

    private Dimension size(Container parent, int board) {
        Insets in = parent.getInsets();
        int column = Math.max(board, bottomWidth());
        return new Dimension(2 * column + 3 * MIN_MARGIN + in.left + in.right,
                titleHeight() + board + bottomHeight() + 2 * ROW_GAP + 2 * MIN_MARGIN + in.top + in.bottom);
    }

    @Override
    public void layoutContainer(Container parent) {
        Insets in = parent.getInsets();
        int width = parent.getWidth() - in.left - in.right;
        int height = parent.getHeight() - in.top - in.bottom;
        int titleH = titleHeight();
        int bottomH = bottomHeight();
        int byWidth = (width - 3 * MIN_MARGIN) / 2;
        int byHeight = height - titleH - bottomH - 2 * ROW_GAP - 2 * MIN_MARGIN;
        // A multiple of the cell count, so the board fills its square exactly.
        int board = Math.max(CELLS, Math.min(byWidth, byHeight) / CELLS * CELLS);

        int margin = (width - 2 * board) / 3;
        int top = in.top + (height - (titleH + board + bottomH + 2 * ROW_GAP)) / 2;
        for (int i = 0; i < columns.length; i++) {
            int x = in.left + margin + i * (board + margin);
            Component[] col = columns[i];
            col[0].setBounds(x, top, board, titleH);
            col[1].setBounds(x, top + titleH + ROW_GAP, board, board);
            int bottomY = top + titleH + board + 2 * ROW_GAP;
            int w = Math.max(board, col[2].getPreferredSize().width);
            col[2].setBounds(x + (board - w) / 2, bottomY, w, col[2].getPreferredSize().height);
        }
    }
}
