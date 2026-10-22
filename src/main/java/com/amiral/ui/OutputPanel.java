package com.amiral.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultCaret;
import javax.swing.text.Element;

/** "Output" box: time-stamped log lines, newest at the bottom, at most 500 lines. */
public class OutputPanel extends JPanel {

    private static final long serialVersionUID = 1L;
    public static final int MAX_LINES = 500;
    public static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm:ss");

    private final JTextArea area = new JTextArea();

    public OutputPanel() {
        super(new BorderLayout());
        setBorder(BorderFactory.createTitledBorder("Output"));
        area.setEditable(false);
        area.setFont(Theme.MONO_FONT);
        area.setBackground(Theme.PANEL);
        ((DefaultCaret) area.getCaret()).setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);
        add(new JScrollPane(area), BorderLayout.CENTER);
        setPreferredSize(new Dimension(800, Theme.OUTPUT_HEIGHT));
    }

    /** Appends "MM/dd/yyyy HH:mm:ss - message". */
    public void log(String message) {
        log(LocalDateTime.now(), message);
    }

    public void log(LocalDateTime time, String message) {
        area.append(FORMAT.format(time) + " - " + message + "\n");
        trim();
        area.setCaretPosition(area.getDocument().getLength());
    }

    private void trim() {
        int extra = area.getLineCount() - 1 - MAX_LINES;
        if (extra <= 0) {
            return;
        }
        Element root = area.getDocument().getDefaultRootElement();
        int end = root.getElement(extra - 1).getEndOffset();
        try {
            area.getDocument().remove(0, end);
        } catch (BadLocationException e) {
            throw new IllegalStateException(e);
        }
    }

    public String text() {
        return area.getText();
    }

    /** Number of log lines currently kept. */
    public int lineCount() {
        return area.getLineCount() - 1;
    }
}
