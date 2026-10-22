package com.amiral.db;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** Access to the {@code chat_message} table. */
public class ChatRepository {

    public record ChatMessage(long id, Long gameId, String sender, String body, String sentAt) {
    }

    private final Database db;

    public ChatRepository(Database db) {
        this.db = db;
    }

    public void save(Long gameId, String sender, String body) throws SQLException {
        Jdbc.insert(db.connection(), "INSERT INTO chat_message(game_id, sender, body) VALUES (?, ?, ?)",
                gameId, sender, body);
    }

    public List<ChatMessage> findByGame(long gameId) throws SQLException {
        List<ChatMessage> list = new ArrayList<>();
        try (PreparedStatement ps = db.connection().prepareStatement(
                "SELECT id, game_id, sender, body, sent_at FROM chat_message WHERE game_id = ? ORDER BY sent_at, id")) {
            ps.setLong(1, gameId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new ChatMessage(rs.getLong(1), Jdbc.nullableLong(rs, "game_id"), rs.getString(3),
                            rs.getString(4), rs.getString(5)));
                }
            }
        }
        return list;
    }
}
