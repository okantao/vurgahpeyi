package com.amiral.ui;

import com.amiral.model.Weapon;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.RoundRectangle2D;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import javax.swing.SwingConstants;

/** Weapon bar under the enemy board: shot, radar (3) and torpedo (2), plus what the enemy has left. */
public class WeaponPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    private final Map<Weapon, JToggleButton> buttons = new EnumMap<>(Weapon.class);
    private final Map<Weapon, Integer> left = new EnumMap<>(Weapon.class);
    private final JLabel enemyLabel = new JLabel();
    private Weapon selected = Weapon.SHOT;
    private boolean active;
    private transient Consumer<Weapon> onChange = w -> { };

    public WeaponPanel() {
        super(new FlowLayout(FlowLayout.CENTER, Theme.GAP, 2));
        setOpaque(false);
        JPanel row = new JPanel(new GridLayout(1, 3, Theme.GAP, 0));
        row.setOpaque(false);
        ButtonGroup group = new ButtonGroup();
        for (Weapon w : Weapon.values()) {
            JToggleButton b = new JToggleButton();
            b.setIcon(new WeaponIcon(w, false));
            b.setDisabledIcon(new WeaponIcon(w, true));
            b.setHorizontalAlignment(SwingConstants.LEFT);
            b.setIconTextGap(6);
            b.setFocusable(false);
            b.setToolTipText(tooltip(w));
            b.addActionListener(e -> select(w));
            group.add(b);
            row.add(b);
            buttons.put(w, b);
        }
        enemyLabel.setForeground(Theme.TEXT_DIM);
        enemyLabel.setBorder(BorderFactory.createEmptyBorder(0, Theme.GAP, 0, 0));
        add(row);
        add(enemyLabel);
        reset();
    }

    private static String tooltip(Weapon w) {
        return switch (w) {
            case SHOT -> "Normal shot on one cell (1)";
            case RADAR -> "Radar: shows enemy ship cells in a 4x4 area, uses your turn (2)";
            case TORPEDO -> "Torpedo: hits every cell of a 3x3 area, uses your turn (3)";
        };
    }

    /** Full stock for a new game, shot selected. */
    public final void reset() {
        setCounts(Weapon.RADAR.uses(), Weapon.TORPEDO.uses(), Weapon.RADAR.uses(), Weapon.TORPEDO.uses());
        select(Weapon.SHOT);
    }

    public void setCounts(int myRadars, int myTorpedoes, int enemyRadars, int enemyTorpedoes) {
        left.put(Weapon.RADAR, myRadars);
        left.put(Weapon.TORPEDO, myTorpedoes);
        enemyLabel.setText("Enemy: radar " + enemyRadars + " · torpedo " + enemyTorpedoes);
        if (selected != Weapon.SHOT && left.get(selected) <= 0) {
            select(Weapon.SHOT);
        }
        refresh();
    }

    /** Buttons work only on our turn. */
    public void setActive(boolean active) {
        this.active = active;
        refresh();
    }

    public void select(Weapon w) {
        if (w != Weapon.SHOT && left.getOrDefault(w, 0) <= 0) {
            return;
        }
        selected = w;
        buttons.get(w).setSelected(true);
        onChange.accept(w);
    }

    public Weapon selected() {
        return selected;
    }

    public int left(Weapon w) {
        return w == Weapon.SHOT ? Integer.MAX_VALUE : left.getOrDefault(w, 0);
    }

    public void setOnChange(Consumer<Weapon> listener) {
        this.onChange = listener;
    }

    private void refresh() {
        for (Weapon w : Weapon.values()) {
            JToggleButton b = buttons.get(w);
            String count = w == Weapon.SHOT ? "∞" : Integer.toString(left.getOrDefault(w, 0));
            b.setText("<html><b>" + w.displayName() + "</b> <span style='color:#8EA3BA'>" + count + "</span></html>");
            b.setEnabled(active && (w == Weapon.SHOT || left.getOrDefault(w, 0) > 0));
        }
    }

    JToggleButton button(Weapon w) {
        return buttons.get(w);
    }

    /** Small drawn icons: crosshair, radar sweep, torpedo. */
    static final class WeaponIcon implements Icon {
        private final Weapon weapon;
        private final boolean disabled;

        WeaponIcon(Weapon weapon, boolean disabled) {
            this.weapon = weapon;
            this.disabled = disabled;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.translate(x, y);
            Color main = disabled ? Theme.TEXT_DIM.darker() : switch (weapon) {
                case SHOT -> Theme.RETICLE;
                case RADAR -> Theme.RADAR;
                case TORPEDO -> Theme.TORPEDO;
            };
            g2.setColor(main);
            g2.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            switch (weapon) {
                case SHOT -> {
                    g2.draw(new Ellipse2D.Double(4, 4, 14, 14));
                    g2.draw(new Line2D.Double(11, 0, 11, 7));
                    g2.draw(new Line2D.Double(11, 15, 11, 22));
                    g2.draw(new Line2D.Double(0, 11, 7, 11));
                    g2.draw(new Line2D.Double(15, 11, 22, 11));
                }
                case RADAR -> {
                    g2.draw(new Ellipse2D.Double(1, 1, 20, 20));
                    g2.draw(new Ellipse2D.Double(6, 6, 10, 10));
                    g2.setColor(Theme.alpha(main, 120));
                    g2.fill(new Arc2D.Double(1, 1, 20, 20, 40, 60, Arc2D.PIE));
                    g2.setColor(main);
                    g2.draw(new Line2D.Double(11, 11, 19, 4));
                }
                case TORPEDO -> {
                    g2.fill(new RoundRectangle2D.Double(1, 8, 17, 6, 6, 6));
                    g2.draw(new Line2D.Double(18, 11, 22, 11));
                    g2.draw(new Line2D.Double(1, 7, 1, 15));
                }
            }
            g2.dispose();
        }

        @Override
        public int getIconWidth() {
            return 23;
        }

        @Override
        public int getIconHeight() {
            return 23;
        }
    }
}
