package com.amiral.ui;

import com.amiral.controller.ConnectionState;
import com.amiral.controller.GameMode;
import com.amiral.model.ComputerPlayer;
import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.OptionalInt;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTextField;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/** "Connection Configuration" box: server, client or computer opponent; IP, port, name and start/stop. */
public class ConnectionPanel extends JPanel {

    private static final long serialVersionUID = 1L;
    public static final int DEFAULT_PORT = 9090;

    /** What the user asked for. */
    public interface Actions {
        void start(GameMode mode, String host, int port, String nickname, ComputerPlayer.Difficulty difficulty);

        void stop(GameMode mode);
    }

    private final JRadioButton serverRadio = new JRadioButton("Play as server", true);
    private final JRadioButton clientRadio = new JRadioButton("Play as client");
    private final JRadioButton computerRadio = new JRadioButton("Play vs computer");
    private final JComboBox<ComputerPlayer.Difficulty> difficulty =
            new JComboBox<>(ComputerPlayer.Difficulty.values());
    private final JLabel ipLabel = new JLabel("My IP Address:");
    private final JTextField ipField = new JTextField();
    private final JLabel portLabel = new JLabel("Port:");
    private final JTextField portField = new JTextField(Integer.toString(DEFAULT_PORT));
    private final JLabel portError = new JLabel(" ");
    private final JTextField nickField = new JTextField();
    private final JButton startButton = new JButton("Start server");
    private final JButton stopButton = new JButton("Stop server");
    private final Border normalBorder;
    private final String localIp;
    private ConnectionState state = ConnectionState.IDLE;
    private transient Actions actions;

    public ConnectionPanel(String localIp, String defaultNickname) {
        this.localIp = localIp;
        setBorder(BorderFactory.createTitledBorder("Connection Configuration"));
        setLayout(new GridBagLayout());
        normalBorder = portField.getBorder() != null ? portField.getBorder() : UIManager.getBorder("TextField.border");

        ButtonGroup group = new ButtonGroup();
        group.add(serverRadio);
        group.add(clientRadio);
        group.add(computerRadio);
        serverRadio.addActionListener(e -> modeChanged());
        clientRadio.addActionListener(e -> modeChanged());
        computerRadio.addActionListener(e -> modeChanged());
        difficulty.setSelectedItem(ComputerPlayer.Difficulty.NORMAL);
        difficulty.setRenderer(new DefaultListCellRenderer() {
            private static final long serialVersionUID = 1L;

            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                    boolean isSelected, boolean cellHasFocus) {
                Object text = value instanceof ComputerPlayer.Difficulty d ? d.label() : value;
                return super.getListCellRendererComponent(list, text, index, isSelected, cellHasFocus);
            }
        });
        ipField.setText(localIp);
        nickField.setText(defaultNickname);
        portError.setForeground(Theme.ERROR_BORDER);
        portError.setFont(portError.getFont().deriveFont(11f));

        serverRadio.setToolTipText("Host the game and wait for an opponent");
        clientRadio.setToolTipText("Join a game hosted by another player");
        computerRadio.setToolTipText("Play against the computer on this machine");
        difficulty.setToolTipText("Easy: random shots · Normal: hunts your ships · Hard: probability map");
        portField.setToolTipText("TCP port, 1024-65535");
        nickField.setToolTipText("Your name shown to the opponent (1-20 characters)");
        startButton.setToolTipText("Start the server / connect / start the game");
        stopButton.setToolTipText("Stop the server / disconnect / end the game");

        portField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                validatePort();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                validatePort();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                validatePort();
            }
        });
        startButton.addActionListener(e -> {
            GameMode mode = mode();
            OptionalInt port = mode == GameMode.AI ? OptionalInt.of(DEFAULT_PORT) : validatePort();
            String nick = nickname();
            if (port.isPresent() && actions != null && !nick.isEmpty()) {
                actions.start(mode, ipField.getText().trim(), port.getAsInt(), nick,
                        (ComputerPlayer.Difficulty) difficulty.getSelectedItem());
            }
        });
        stopButton.addActionListener(e -> {
            if (actions != null) {
                actions.stop(mode());
            }
        });

        GridBagConstraints g = new GridBagConstraints();
        g.gridx = 0;
        g.gridwidth = 2;
        g.fill = GridBagConstraints.HORIZONTAL;
        g.weightx = 1;
        g.insets = new Insets(1, 2, 1, 2);
        add(serverRadio, g);
        add(clientRadio, g);
        g.gridwidth = 1;
        g.gridy = 2;
        add(computerRadio, g);
        g.gridx = 1;
        add(difficulty, g);
        g.gridx = 0;
        g.gridy = 3;
        g.weightx = 0;
        add(ipLabel, g);
        g.gridx = 1;
        g.weightx = 1;
        add(ipField, g);
        g.gridx = 0;
        g.gridy = 4;
        g.weightx = 0;
        add(portLabel, g);
        g.gridx = 1;
        g.weightx = 1;
        add(portField, g);
        g.gridx = 0;
        g.gridy = 5;
        g.gridwidth = 2;
        add(portError, g);
        g.gridy = 6;
        g.gridwidth = 1;
        g.weightx = 0;
        add(new JLabel("Nickname:"), g);
        g.gridx = 1;
        g.weightx = 1;
        add(nickField, g);
        g.gridx = 0;
        g.gridy = 7;
        g.insets = new Insets(6, 2, 2, 2);
        add(startButton, g);
        g.gridx = 1;
        add(stopButton, g);

        modeChanged();
    }

    public void setActions(Actions actions) {
        this.actions = actions;
    }

    public GameMode mode() {
        return serverRadio.isSelected() ? GameMode.SERVER : clientRadio.isSelected() ? GameMode.CLIENT : GameMode.AI;
    }

    private void modeChanged() {
        GameMode mode = mode();
        ipLabel.setText(mode == GameMode.CLIENT ? "Server IP:" : "My IP Address:");
        ipField.setEditable(mode == GameMode.CLIENT);
        if (mode == GameMode.CLIENT) {
            ipField.setText(ipField.getText().equals(localIp) ? "127.0.0.1" : ipField.getText());
        } else {
            ipField.setText(localIp);
        }
        startButton.setText(switch (mode) {
            case SERVER -> "Start server";
            case CLIENT -> "Connect";
            case AI -> "Start game";
        });
        stopButton.setText(switch (mode) {
            case SERVER -> "Stop server";
            case CLIENT -> "Disconnect";
            case AI -> "End game";
        });
        if (mode == GameMode.AI) {
            portField.setBorder(normalBorder);
            portError.setText(" ");
        } else {
            validatePort();
        }
        refreshButtons(state);
    }

    /** Validates the port field; marks it red when invalid. */
    public OptionalInt validatePort() {
        OptionalInt port = parsePort(portField.getText());
        if (port.isPresent()) {
            portField.setBorder(normalBorder);
            portError.setText(" ");
        } else {
            portField.setBorder(BorderFactory.createLineBorder(Theme.ERROR_BORDER, 2));
            portError.setText("Port must be a number 1024-65535");
        }
        return port;
    }

    public static OptionalInt parsePort(String text) {
        try {
            int p = Integer.parseInt(text.trim());
            return p >= 1024 && p <= 65535 ? OptionalInt.of(p) : OptionalInt.empty();
        } catch (NumberFormatException e) {
            return OptionalInt.empty();
        }
    }

    private String nickname() {
        String n = nickField.getText().trim();
        return n.length() > 20 ? n.substring(0, 20) : n;
    }

    /** Enables the buttons according to ROADMAP table 7.4 (plus the computer mode). */
    public final void refreshButtons(ConnectionState s) {
        this.state = s;
        boolean idle = s == ConnectionState.IDLE;
        boolean network = mode() != GameMode.AI;
        startButton.setEnabled(idle);
        stopButton.setEnabled(!idle && s != ConnectionState.CONNECTING);
        serverRadio.setEnabled(idle);
        clientRadio.setEnabled(idle);
        computerRadio.setEnabled(idle);
        difficulty.setEnabled(idle && !network);
        portField.setEnabled(idle && network);
        portLabel.setEnabled(network);
        nickField.setEnabled(idle);
        ipField.setEnabled(network && (idle || mode() == GameMode.SERVER));
        ipLabel.setEnabled(network);
    }

    public boolean isServerMode() {
        return serverRadio.isSelected();
    }

    public void selectClientMode() {
        clientRadio.setSelected(true);
        modeChanged();
    }

    public void selectComputerMode() {
        computerRadio.setSelected(true);
        modeChanged();
    }

    public void setPortText(String text) {
        portField.setText(text);
    }

    JButton startButton() {
        return startButton;
    }

    JButton stopButton() {
        return stopButton;
    }

    JLabel ipLabel() {
        return ipLabel;
    }

    JTextField ipField() {
        return ipField;
    }

    JLabel portError() {
        return portError;
    }

    JComboBox<ComputerPlayer.Difficulty> difficultyBox() {
        return difficulty;
    }
}
