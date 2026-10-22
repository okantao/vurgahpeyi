package com.amiral.ui;

import com.amiral.controller.HistoryProvider;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.Locale;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.table.DefaultTableModel;

/** Non-modal window with win statistics, past games and the moves of the selected game. */
public class HistoryDialog extends JDialog {

    private static final long serialVersionUID = 1L;
    static final int GAME_LIMIT = 200;

    private final transient HistoryProvider provider;
    private final JLabel statsLabel = new JLabel();
    private final DefaultTableModel games = readOnlyModel("ID", "Mode", "Started", "Ended", "Remote IP", "Result");
    private final DefaultTableModel moves = readOnlyModel("Turn", "Player", "Weapon", "Cell", "Result", "Time");
    private final JLabel movesTitle = new JLabel("Double-click a game to see its moves");

    public HistoryDialog(Frame owner, HistoryProvider provider) {
        super(owner, "History", false);
        this.provider = provider;
        JTable gameTable = new JTable(games);
        gameTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        gameTable.setAutoCreateRowSorter(true);
        gameTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int row = gameTable.getSelectedRow();
                if (e.getClickCount() == 2 && row >= 0) {
                    long id = (Long) games.getValueAt(gameTable.convertRowIndexToModel(row), 0);
                    showMoves(id);
                }
            }
        });
        JTable moveTable = new JTable(moves);

        statsLabel.setFont(Theme.LABEL_FONT);
        statsLabel.setBorder(BorderFactory.createEmptyBorder(Theme.GAP, Theme.GAP, Theme.GAP, Theme.GAP));
        JButton refresh = new JButton("Refresh");
        refresh.setToolTipText("Reload from the database");
        refresh.addActionListener(e -> reload());
        JPanel top = new JPanel(new BorderLayout());
        top.add(statsLabel, BorderLayout.CENTER);
        top.add(refresh, BorderLayout.EAST);

        JPanel bottom = new JPanel(new BorderLayout());
        bottom.add(movesTitle, BorderLayout.NORTH);
        bottom.add(new JScrollPane(moveTable), BorderLayout.CENTER);
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, new JScrollPane(gameTable), bottom);
        split.setResizeWeight(0.6);

        JPanel content = new JPanel(new BorderLayout());
        content.add(top, BorderLayout.NORTH);
        content.add(split, BorderLayout.CENTER);
        setContentPane(content);
        setPreferredSize(new Dimension(760, 520));
        pack();
        setLocationRelativeTo(owner);
        reload();
    }

    private static DefaultTableModel readOnlyModel(String... columns) {
        return new DefaultTableModel(columns, 0) {
            private static final long serialVersionUID = 1L;

            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }

            @Override
            public Class<?> getColumnClass(int column) {
                return column == 0 && columns[0].equals("ID") ? Long.class : Object.class;
            }
        };
    }

    /** Reloads statistics and games (newest first). */
    public final void reload() {
        statsLabel.setText(formatStats(provider.stats()));
        games.setRowCount(0);
        for (HistoryProvider.GameRow g : provider.games(GAME_LIMIT)) {
            games.addRow(new Object[] {g.id(), g.mode(), g.started(), nullToDash(g.ended()), nullToDash(g.remoteIp()),
                g.result()});
        }
        moves.setRowCount(0);
        movesTitle.setText("Double-click a game to see its moves");
    }

    void showMoves(long gameId) {
        moves.setRowCount(0);
        List<HistoryProvider.MoveRow> list = provider.moves(gameId);
        for (HistoryProvider.MoveRow m : list) {
            moves.addRow(new Object[] {m.turn(), m.shooter(), m.weapon(), m.coord(), m.result(), m.time()});
        }
        movesTitle.setText("Moves of game " + gameId + " (" + list.size() + ")");
    }

    static String formatStats(HistoryProvider.Stats s) {
        return String.format(Locale.ROOT, "Games: %d · Wins: %d · Losses: %d · Win rate: %%%.0f",
                s.games(), s.wins(), s.losses(), s.winRate());
    }

    private static String nullToDash(String s) {
        return s == null || s.isEmpty() ? "-" : s;
    }

    String statsText() {
        return statsLabel.getText();
    }

    int gameRowCount() {
        return games.getRowCount();
    }

    int moveRowCount() {
        return moves.getRowCount();
    }
}
