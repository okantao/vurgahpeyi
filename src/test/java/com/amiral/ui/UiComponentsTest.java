package com.amiral.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.amiral.controller.ConnectionState;
import com.amiral.controller.HistoryProvider;
import com.amiral.model.Coordinate;
import com.amiral.model.Orientation;
import com.amiral.model.ShipType;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

/** Swing components that work without a display (no frames or dialogs). */
class UiComponentsTest {

    private static void onEdt(Runnable r) throws Exception {
        SwingUtilities.invokeAndWait(r);
    }

    private static Coordinate c(String s) {
        return Coordinate.parse(s);
    }

    @Test
    void boardMapsPixelsToCells() throws Exception {
        onEdt(() -> {
            BoardPanel board = new BoardPanel();
            board.setSize(330, 330); // 30 px cells
            assertNull(board.cellAt(10, 10), "label corner");
            assertEquals(c("A1"), board.cellAt(45, 45));
            assertEquals(c("J10"), board.cellAt(325, 325));
            assertNull(board.cellAt(15, 200), "row labels");
            board.setCell(c("B2"), BoardPanel.CellView.HIT);
            assertEquals(BoardPanel.CellView.HIT, board.cell(c("B2")));
            board.clear();
            assertEquals(BoardPanel.CellView.WATER, board.cell(c("B2")));
        });
    }

    @Test
    void boardClicksOnlyWhenInteractive() throws Exception {
        onEdt(() -> {
            BoardPanel board = new BoardPanel();
            board.setSize(330, 330);
            List<Coordinate> clicks = new ArrayList<>();
            board.setCellClickListener(clicks::add);
            MouseEvent press = new MouseEvent(board, MouseEvent.MOUSE_PRESSED, 0, MouseEvent.BUTTON1_DOWN_MASK,
                    45, 45, 1, false, MouseEvent.BUTTON1);
            board.dispatchEvent(press);
            assertTrue(clicks.isEmpty());
            board.setInteractive(true);
            board.dispatchEvent(press);
            assertEquals(List.of(c("A1")), clicks);
        });
    }

    @Test
    void automaticAllocationAtStart() throws Exception {
        onEdt(() -> {
            ShipAllocationPanel panel = new ShipAllocationPanel(new BoardPanel(), new Random(5));
            assertFalse(panel.isManual());
            assertTrue(panel.placement().isFleetComplete());
            assertFalse(panel.readyButton().isEnabled(), "needs an opponent");
            panel.setReadyAllowed(true);
            assertTrue(panel.readyButton().isEnabled());
            panel.setLocked(true);
            assertFalse(panel.readyButton().isEnabled());
        });
    }

    @Test
    void manualPlacementRotateAndRemove() throws Exception {
        onEdt(() -> {
            BoardPanel board = new BoardPanel();
            board.setSize(330, 330);
            ShipAllocationPanel panel = new ShipAllocationPanel(board, new Random(5));
            List<Boolean> changes = new ArrayList<>();
            panel.setOnFleetChanged(changes::add);
            panel.setReadyAllowed(true);
            panel.clearAllocation();
            assertTrue(panel.isManual());
            assertTrue(board.isInteractive());
            assertEquals(5, panel.shipBox().getItemCount());

            int[] rows = {45, 75, 105, 135, 165};
            for (int i = 0; i < rows.length; i++) {
                assertFalse(panel.readyButton().isEnabled());
                MouseEvent press = new MouseEvent(board, MouseEvent.MOUSE_PRESSED, 0, 0, 45, rows[i], 1, false,
                        MouseEvent.BUTTON1);
                board.dispatchEvent(press);
            }
            assertTrue(panel.placement().isFleetComplete());
            assertTrue(changes.get(changes.size() - 1));
            assertTrue(panel.readyButton().isEnabled());
            assertEquals(0, panel.shipBox().getItemCount());
            assertEquals(BoardPanel.CellView.SHIP, board.cell(c("A5")));

            MouseEvent right = new MouseEvent(board, MouseEvent.MOUSE_PRESSED, 0, MouseEvent.BUTTON3_DOWN_MASK, 45,
                    45, 1, false, MouseEvent.BUTTON3);
            board.dispatchEvent(right);
            assertFalse(panel.placement().isFleetComplete());
            assertEquals(ShipType.CARRIER, panel.shipBox().getItemAt(0), "removed ship is back in the list");
            assertEquals(BoardPanel.CellView.WATER, board.cell(c("A1")));

            assertEquals(Orientation.H, panel.orientation());
            panel.rotate();
            assertEquals(Orientation.V, panel.orientation());
            panel.newAllocation();
            assertFalse(panel.isManual());
            assertTrue(panel.placement().isFleetComplete());
        });
    }

    @Test
    void invalidManualPlacementIsIgnored() throws Exception {
        onEdt(() -> {
            BoardPanel board = new BoardPanel();
            board.setSize(330, 330);
            ShipAllocationPanel panel = new ShipAllocationPanel(board, new Random(5));
            panel.clearAllocation();
            // Carrier at A8 horizontal would stick out of the board.
            board.dispatchEvent(new MouseEvent(board, MouseEvent.MOUSE_PRESSED, 0, 0, 255, 45, 1, false,
                    MouseEvent.BUTTON1));
            assertTrue(panel.placement().ships().isEmpty());
        });
    }

    @Test
    void connectionButtonsFollowTable() throws Exception {
        onEdt(() -> {
            ConnectionPanel p = new ConnectionPanel("192.168.1.10", "Kaan");
            assertTrue(p.isServerMode());
            assertEquals("My IP Address:", p.ipLabel().getText());
            assertEquals("192.168.1.10", p.ipField().getText());
            assertFalse(p.ipField().isEditable());
            p.refreshButtons(ConnectionState.IDLE);
            assertTrue(p.startButton().isEnabled());
            assertFalse(p.stopButton().isEnabled());
            p.refreshButtons(ConnectionState.LISTENING);
            assertFalse(p.startButton().isEnabled());
            assertTrue(p.stopButton().isEnabled());
            p.refreshButtons(ConnectionState.IN_GAME);
            assertFalse(p.startButton().isEnabled());
            assertTrue(p.stopButton().isEnabled());

            p.refreshButtons(ConnectionState.IDLE);
            p.selectClientMode();
            assertEquals("Server IP:", p.ipLabel().getText());
            assertEquals("Connect", p.startButton().getText());
            assertEquals("Disconnect", p.stopButton().getText());
            assertTrue(p.ipField().isEditable());
            assertTrue(p.startButton().isEnabled());
            p.refreshButtons(ConnectionState.CONNECTED);
            assertFalse(p.startButton().isEnabled());
            assertTrue(p.stopButton().isEnabled());
        });
    }

    @Test
    void portValidation() throws Exception {
        assertEquals(9090, ConnectionPanel.parsePort("9090").getAsInt());
        assertEquals(65535, ConnectionPanel.parsePort(" 65535 ").getAsInt());
        assertTrue(ConnectionPanel.parsePort("80").isEmpty());
        assertTrue(ConnectionPanel.parsePort("70000").isEmpty());
        assertTrue(ConnectionPanel.parsePort("abc").isEmpty());
        onEdt(() -> {
            ConnectionPanel p = new ConnectionPanel("127.0.0.1", "Kaan");
            p.setPortText("80");
            assertTrue(p.validatePort().isEmpty());
            assertFalse(p.portError().getText().isBlank());
            p.setPortText("9090");
            assertTrue(p.validatePort().isPresent());
            assertTrue(p.portError().getText().isBlank());
        });
    }

    @Test
    void outputKeepsLast500Lines() throws Exception {
        onEdt(() -> {
            OutputPanel out = new OutputPanel();
            for (int i = 1; i <= 510; i++) {
                out.log("line " + i);
            }
            assertEquals(500, out.lineCount());
            assertTrue(out.text().contains(" - line 510"));
            assertFalse(out.text().contains(" - line 10\n"));
            assertTrue(out.text().split("\n")[0].matches("\\d\\d/\\d\\d/\\d{4} \\d\\d:\\d\\d:\\d\\d - line 11"));
        });
    }

    @Test
    void chatSendsTrimmedTextAndLimitsLength() throws Exception {
        onEdt(() -> {
            ChatPanel chat = new ChatPanel();
            List<String> sent = new ArrayList<>();
            chat.setOnSend(sent::add);
            assertFalse(chat.input().isEnabled());
            chat.setChatEnabled(true);
            chat.input().setText("x".repeat(600));
            assertEquals(ChatPanel.MAX_LENGTH, chat.input().getText().length());
            chat.input().setText("  merhaba  ");
            chat.input().postActionEvent();
            chat.input().setText("   ");
            chat.input().postActionEvent();
            assertEquals(List.of("merhaba"), sent);
            chat.appendMessage("Me", "selam", true);
            chat.appendMessage("TestServer", "hi", false);
            assertTrue(chat.historyText().contains("Me: selam"));
            assertTrue(chat.historyText().contains("TestServer: hi"));
        });
    }

    @Test
    void statusBarAndStatsText() throws Exception {
        onEdt(() -> {
            StatusBar bar = new StatusBar();
            assertEquals("Idle", bar.status());
            bar.setStatus("Your turn");
            assertEquals("Your turn", bar.status());
        });
        assertEquals("Games: 4 · Wins: 2 · Losses: 1 · Win rate: %67",
                HistoryDialog.formatStats(new HistoryProvider.Stats(4, 2, 1, 1, 66.67)));
    }
}
