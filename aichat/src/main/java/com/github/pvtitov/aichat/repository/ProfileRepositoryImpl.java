package com.github.pvtitov.aichat.repository;

import com.github.pvtitov.aichat.constants.DatabaseConstants;
import com.github.pvtitov.aichat.model.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

@Repository
public class ProfileRepositoryImpl implements ProfileRepository {

    private final JdbcTemplate jdbcTemplate;

    public ProfileRepositoryImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void save(Profile profile) {
        String sql = "INSERT INTO " + DatabaseConstants.PROFILE_TABLE + " (login, profile_data, current_branch) VALUES (?, ?, ?)";
        jdbcTemplate.update(sql, profile.getLogin(), profile.getProfileData(), profile.getCurrentBranch());
    }

    @Override
    public Optional<Profile> findByLogin(String login) {
        String sql = "SELECT * FROM " + DatabaseConstants.PROFILE_TABLE + " WHERE login = ?";
        return jdbcTemplate.query(sql, rs -> {
            if (rs.next()) {
                return Optional.of(mapRowToProfile(rs, 0));
            }
            return Optional.empty();
        }, login);
    }

    @Override
    public void update(Profile profile) {
        String sql = "UPDATE " + DatabaseConstants.PROFILE_TABLE + " SET profile_data = ?, current_branch = ? WHERE login = ?";
        jdbcTemplate.update(sql, profile.getProfileData(), profile.getCurrentBranch(), profile.getLogin());
    }

    private Profile mapRowToProfile(ResultSet rs, int rowNum) throws SQLException {
        Profile profile = new Profile();
        profile.setLogin(rs.getString("login"));
        profile.setProfileData(rs.getString("profile_data"));
        profile.setCurrentBranch(rs.getInt("current_branch"));
        return profile;
    }
}
