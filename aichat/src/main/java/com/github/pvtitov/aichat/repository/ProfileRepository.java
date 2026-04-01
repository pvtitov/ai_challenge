package com.github.pvtitov.aichat.repository;

import com.github.pvtitov.aichat.model.Profile;

import java.util.Optional;

public interface ProfileRepository {
    void save(Profile profile);
    Optional<Profile> findByLogin(String login);
    void update(Profile profile);
}
