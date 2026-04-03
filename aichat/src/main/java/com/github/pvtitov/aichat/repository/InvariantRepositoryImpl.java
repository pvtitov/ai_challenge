package com.github.pvtitov.aichat.repository;

import com.github.pvtitov.aichat.model.Invariant;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import static com.github.pvtitov.aichat.constants.DatabaseConstants.*;

@Repository
public class InvariantRepositoryImpl implements InvariantRepository {

    private final JdbcTemplate jdbcTemplate;

    public InvariantRepositoryImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        createTable();
    }

    private void createTable() {
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS " + INVARIANTS_TABLE + " (" +
                             INVARIANTS_ID + " INTEGER PRIMARY KEY AUTOINCREMENT," +
                             INVARIANTS_TEXT + " TEXT NOT NULL," +
                             INVARIANTS_BRANCH + " INTEGER NOT NULL," +
                             INVARIANTS_PROFILE_LOGIN + " TEXT NOT NULL" +
                             ")");
    }

    @Override
    public void save(Invariant invariant) {
        if (invariant.getId() == null) {
            jdbcTemplate.update("INSERT INTO " + INVARIANTS_TABLE + " (" + INVARIANTS_TEXT + ", " + INVARIANTS_BRANCH + ", " + INVARIANTS_PROFILE_LOGIN + ") VALUES (?, ?, ?)",
                                invariant.getText(), invariant.getBranch(), invariant.getProfileLogin());
        } else {
            jdbcTemplate.update("UPDATE " + INVARIANTS_TABLE + " SET " + INVARIANTS_TEXT + " = ?, " + INVARIANTS_BRANCH + " = ?, " + INVARIANTS_PROFILE_LOGIN + " = ? WHERE " + INVARIANTS_ID + " = ?",
                                invariant.getText(), invariant.getBranch(), invariant.getProfileLogin(), invariant.getId());
        }
    }

    @Override
    public List<Invariant> findByBranch(int branch, String profileLogin) {
        return jdbcTemplate.query("SELECT * FROM " + INVARIANTS_TABLE + " WHERE " + INVARIANTS_BRANCH + " = ? AND " + INVARIANTS_PROFILE_LOGIN + " = ?",
                                  new InvariantRowMapper(), branch, profileLogin);
    }

    @Override
    public void deleteByBranch(int branch, String profileLogin) {
        jdbcTemplate.update("DELETE FROM " + INVARIANTS_TABLE + " WHERE " + INVARIANTS_BRANCH + " = ? AND " + INVARIANTS_PROFILE_LOGIN + " = ?",
                            branch, profileLogin);
    }

    @Override
    public void deleteAll(String profileLogin) {
        jdbcTemplate.update("DELETE FROM " + INVARIANTS_TABLE + " WHERE " + INVARIANTS_PROFILE_LOGIN + " = ?",
                            profileLogin);
    }

    private static class InvariantRowMapper implements RowMapper<Invariant> {
        @Override
        public Invariant mapRow(ResultSet rs, int rowNum) throws SQLException {
            Invariant invariant = new Invariant();
            invariant.setId(rs.getLong(INVARIANTS_ID));
            invariant.setText(rs.getString(INVARIANTS_TEXT));
            invariant.setBranch(rs.getInt(INVARIANTS_BRANCH));
            invariant.setProfileLogin(rs.getString(INVARIANTS_PROFILE_LOGIN));
            return invariant;
        }
    }
}
