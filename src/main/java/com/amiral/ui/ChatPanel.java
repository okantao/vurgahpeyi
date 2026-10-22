package com.amiral.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JTextPane;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultCaret;
import javax.swing.text.DocumentFilter;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

/** "Chat History" box: coloured read-only history plus an input line (Enter or Send). */
public class ChatPanel extends JPanel {

    private static final long serialVersionUID = 1L;
    public static final int MAX_LENGTH = 500;

    private final JTextPane history = new JTextPane();
    private final JTextField input = new JTextField();
    private final JButton send = new JButton("Send");
    private transient Consumer<String> onSend = s -> { };

    public ChatPanel() {
        super(new BorderLayout(Theme.GAP / 2, Theme.GAP / 2));
        setBorder(BorderFactory.createTitledBorder("Chat History"));
        history.setEditable(false);
        ((DefaultCaret) history.getCaret()).setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);
        JScrollPane scroll = new JScrollPane(history);
        scroll.setPreferredSize(new Dimension(200, 120));
        add(scroll, BorderLayout.CENTER);

        ((AbstractDocument) input.getDocument()).setDocumentFilter(new LengthFilter(MAX_LENGTH));
        input.setToolTipText("Message (max " + MAX_LENGTH + " characters), Enter to send");
        send.setToolTipText("Send the message");
        input.addActionListener(e -> submit());
        send.addActionListener(e -> submit());
        JPanel bottom = new JPanel(new BorderLayout(Theme.GAP / 2, 0));
        bottom.add(input, BorderLayout.CENTER);
        bottom.add(send, BorderLayout.EAST);
        add(bottom, BorderLayout.SOUTH);
        setChatEnabled(false);
    }

    private void submit() {
        String text = input.getText().trim();
        if (text.isEmpty() || !input.isEnabled()) {
            return;
        }
        input.setText("");
        onSend.accept(text);
    }

    /** Adds a line "sender: body" in blue for our messages and red for the enemy's. */
    public void appendMessage(String sender, String body, boolean mine) {
        StyledDocument doc = history.getStyledDocument();
        SimpleAttributeSet name = new SimpleAttributeSet();
        StyleConstants.setForeground(name, mine ? Theme.CHAT_ME : Theme.CHAT_ENEMY);
        StyleConstants.setBold(name, true);
        SimpleAttributeSet text = new SimpleAttributeSet();
        StyleConstants.setForeground(text, Color.BLACK);
        try {
            doc.insertString(doc.getLength(), sender + ": ", name);
            doc.insertString(doc.getLength(), body + "\n", text);
        } catch (BadLocationException e) {
            throw new IllegalStateException(e);
        }
        history.setCaretPosition(doc.getLength());
    }

    public void clearHistory() {
        history.setText("");
    }

    public void setChatEnabled(boolean enabled) {
        input.setEnabled(enabled);
        send.setEnabled(enabled);
    }

    public void setOnSend(Consumer<String> listener) {
        this.onSend = listener;
    }

    public String historyText() {
        return history.getText();
    }

    JTextField input() {
        return input;
    }

    /** Rejects input beyond {@code max} characters. */
    static final class LengthFilter extends DocumentFilter {
        private final int max;

        LengthFilter(int max) {
            this.max = max;
        }

        @Override
        public void insertString(FilterBypass fb, int offset, String text, AttributeSet attr)
                throws BadLocationException {
            replace(fb, offset, 0, text, attr);
        }

        @Override
        public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs)
                throws BadLocationException {
            int room = max - (fb.getDocument().getLength() - length);
            if (text == null || room <= 0) {
                if (length > 0) {
                    fb.remove(offset, length);
                }
                return;
            }
            fb.replace(offset, length, text.length() > room ? text.substring(0, room) : text, attrs);
        }
    }
}
