package com.amiral.ui;

import com.amiral.model.Board;
import com.amiral.model.Coordinate;
import com.amiral.model.Orientation;
import com.amiral.model.RandomFleetGenerator;
import com.amiral.model.Ship;
import com.amiral.model.ShipType;
import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JRadioButton;

/**
 * "Ships allocation" box: manual or automatic placement of the local fleet on the "Your ships" board.
 */
public class ShipAllocationPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    private final transient BoardPanel ownBoard;
    private final transient Board placement = new Board();
    private final transient Random random;
    private final JRadioButton manual = new JRadioButton("Manual allocation");
    private final JRadioButton automatic = new JRadioButton("Automatic allocation", true);
    private final DefaultComboBoxModel<ShipType> remaining = new DefaultComboBoxModel<>();
    private final JComboBox<ShipType> shipBox = new JComboBox<>(remaining);
    private final JButton rotateButton = new JButton("Rotate");
    private final JLabel orientationLabel = new JLabel();
    private final JButton clearButton = new JButton("Clear allocation");
    private final JButton newButton = new JButton("New allocation");
    private final JButton readyButton = new JButton("Ready");
    private Orientation orientation = Orientation.H;
    private boolean locked;
    private boolean readyAllowed;
    private transient Consumer<Boolean> onFleetChanged = complete -> { };
    private transient Consumer<Board> onReady = b -> { };

    public ShipAllocationPanel(BoardPanel ownBoard) {
        this(ownBoard, new Random());
    }

    public ShipAllocationPanel(BoardPanel ownBoard, Random random) {
        this.ownBoard = ownBoard;
        this.random = random;
        setBorder(BorderFactory.createTitledBorder("Ships allocation"));
        setLayout(new GridBagLayout());

        ButtonGroup group = new ButtonGroup();
        group.add(manual);
        group.add(automatic);
        manual.addActionListener(e -> updateControls());
        automatic.addActionListener(e -> updateControls());

        shipBox.setRenderer(new DefaultListCellRenderer() {
            private static final long serialVersionUID = 1L;

            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                    boolean isSelected, boolean cellHasFocus) {
                Object text = value instanceof ShipType t ? t.label() : "All ships placed";
                return super.getListCellRendererComponent(list, text, index, isSelected, cellHasFocus);
            }
        });
        shipBox.setToolTipText("Ship to place on \"Your ships\"");
        rotateButton.setToolTipText("Change direction (R)");
        clearButton.setToolTipText("Remove all ships and switch to manual allocation");
        newButton.setToolTipText("Place all ships at random");
        readyButton.setToolTipText("Send your fleet and wait for the opponent");
        rotateButton.addActionListener(e -> rotate());
        clearButton.addActionListener(e -> clearAllocation());
        newButton.addActionListener(e -> newAllocation());
        readyButton.addActionListener(e -> {
            if (placement.isFleetComplete()) {
                onReady.accept(placement.copyPlacement());
            }
        });

        GridBagConstraints g = new GridBagConstraints();
        g.gridx = 0;
        g.gridwidth = 2;
        g.fill = GridBagConstraints.HORIZONTAL;
        g.weightx = 1;
        g.insets = new Insets(1, 2, 1, 2);
        add(manual, g);
        add(automatic, g);
        g.gridwidth = 1;
        g.gridy = 2;
        add(shipBox, g);
        g.gridx = 1;
        g.weightx = 0;
        add(rotateButton, g);
        g.gridx = 0;
        g.gridy = 3;
        g.gridwidth = 2;
        add(orientationLabel, g);
        g.gridy = 4;
        g.gridwidth = 1;
        g.weightx = 1;
        add(clearButton, g);
        g.gridx = 1;
        add(newButton, g);
        g.gridx = 0;
        g.gridy = 5;
        g.gridwidth = 2;
        add(readyButton, g);

        ownBoard.setHoverListener(this::showPreview);
        ownBoard.setCellClickListener(this::placeAt);
        ownBoard.setRightClickListener(this::removeAt);

        // At start-up the default (automatic) mode already has a fleet.
        newAllocation();
    }

    /** Generates a random fleet and switches to automatic mode. */
    public void newAllocation() {
        if (locked) {
            return;
        }
        placement.clear();
        RandomFleetGenerator.generate(random).ships().forEach(placement::placeShip);
        automatic.setSelected(true);
        fleetChanged();
    }

    /** Removes every ship and switches to manual mode. */
    public void clearAllocation() {
        if (locked) {
            return;
        }
        placement.clear();
        manual.setSelected(true);
        fleetChanged();
    }

    public void rotate() {
        if (locked || !manual.isSelected()) {
            return;
        }
        orientation = orientation.toggle();
        updateControls();
        showPreview(ownBoard.hoverCell());
    }

    private void placeAt(Coordinate c) {
        ShipType type = (ShipType) shipBox.getSelectedItem();
        if (locked || !manual.isSelected() || type == null) {
            return;
        }
        Ship ship = new Ship(type, c, orientation);
        if (placement.canPlace(ship)) {
            placement.placeShip(ship);
            fleetChanged();
        }
    }

    private void removeAt(Coordinate c) {
        if (locked || !manual.isSelected()) {
            return;
        }
        Optional<Ship> ship = placement.shipAt(c);
        if (ship.isPresent()) {
            placement.removeShip(ship.get().type());
            fleetChanged();
        }
    }

    private void showPreview(Coordinate c) {
        ShipType type = (ShipType) shipBox.getSelectedItem();
        if (c == null || locked || !manual.isSelected() || type == null) {
            ownBoard.setPreview(List.of(), true);
            return;
        }
        Ship ship = new Ship(type, c, orientation);
        if (ship.fitsOnBoard()) {
            ownBoard.setGhost(ship, placement.canPlace(ship));
        } else {
            // Show the part that is on the board, in red.
            List<Coordinate> visible = new java.util.ArrayList<>();
            for (int i = 0; i < type.length(); i++) {
                (orientation == Orientation.H ? c.offset(0, i) : c.offset(i, 0)).ifPresent(visible::add);
            }
            ownBoard.setPreview(visible, false);
        }
    }

    private void fleetChanged() {
        ShipType selected = (ShipType) shipBox.getSelectedItem();
        remaining.removeAllElements();
        for (ShipType t : ShipType.values()) {
            if (placement.shipOf(t).isEmpty()) {
                remaining.addElement(t);
            }
        }
        if (selected != null && remaining.getIndexOf(selected) >= 0) {
            remaining.setSelectedItem(selected);
        }
        redrawBoard();
        updateControls();
        showPreview(ownBoard.hoverCell());
        onFleetChanged.accept(placement.isFleetComplete());
    }

    /** Paints the current placement on the own board: clean sea with the ship hulls. */
    public void redrawBoard() {
        ownBoard.clear();
        ownBoard.setShips(placement.ships());
    }

    private void updateControls() {
        boolean manualMode = manual.isSelected();
        boolean editable = !locked;
        manual.setEnabled(editable);
        automatic.setEnabled(editable);
        shipBox.setEnabled(editable && manualMode && remaining.getSize() > 0);
        rotateButton.setEnabled(editable && manualMode);
        orientationLabel.setText("Direction: " + (orientation == Orientation.H ? "horizontal" : "vertical"));
        clearButton.setEnabled(editable);
        newButton.setEnabled(editable);
        readyButton.setEnabled(editable && readyAllowed && placement.isFleetComplete());
        ownBoard.setInteractive(editable && manualMode);
    }

    /** Locks placement while a game is running (or after Ready). */
    public void setLocked(boolean locked) {
        this.locked = locked;
        updateControls();
    }

    /** Ready needs a connected opponent. */
    public void setReadyAllowed(boolean allowed) {
        this.readyAllowed = allowed;
        updateControls();
    }

    public void setOnFleetChanged(Consumer<Boolean> listener) {
        this.onFleetChanged = listener;
    }

    public void setOnReady(Consumer<Board> listener) {
        this.onReady = listener;
    }

    public Board placement() {
        return placement;
    }

    public boolean isManual() {
        return manual.isSelected();
    }

    public Orientation orientation() {
        return orientation;
    }

    public boolean isLocked() {
        return locked;
    }

    JButton readyButton() {
        return readyButton;
    }

    JComboBox<ShipType> shipBox() {
        return shipBox;
    }
}
