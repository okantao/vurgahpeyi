package com.amiral.ui;

import java.awt.BorderLayout;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;

/** One-line status at the bottom of the window. */
public class StatusBar extends JPanel {

    private static final long serialVersionUID = 1L;

    private final JLabel label = new JLabel("Idle");

    public StatusBar() {
        super(new BorderLayout());
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.GRID),
                BorderFactory.createEmptyBorder(2, Theme.GAP, 2, Theme.GAP)));
        add(label, BorderLayout.WEST);
    }

    public void setStatus(String text) {
        label.setText(text);
    }

    public String status() {
        return label.getText();
    }
}
