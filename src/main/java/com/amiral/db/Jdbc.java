package com.amiral.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;

/** Small JDBC helpers shared by the repositories. */
final class Jdbc {

    private Jdbc() {
    }

    /** Runs an INSERT and returns the generated id. */
    static long insert(Connection c, String sql, Object... params) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bind(ps, params);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("No generated key for: " + sql);
                }
                return keys.getLong(1);
            }
        }
    }

    static int update(Connection c, String sql, Object... params) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            bind(ps, params);
            return ps.executeUpdate();
        }
    }

    static void bind(PreparedStatement ps, Object... params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            Object p = params[i];
            if (p == null) {
                ps.setNull(i + 1, Types.NULL);
            } else if (p instanceof Integer n) {
                ps.setInt(i + 1, n);
            } else if (p instanceof Long n) {
                ps.setLong(i + 1, n);
            } else {
                ps.setString(i + 1, p.toString());
            }
        }
    }

    static Integer nullableInt(ResultSet rs, String column) throws SQLException {
        int v = rs.getInt(column);
        return rs.wasNull() ? null : v;
    }

    static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long v = rs.getLong(column);
        return rs.wasNull() ? null : v;
    }
}
