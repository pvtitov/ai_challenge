package com.github.pvtitov.aichat.repository;

import com.github.pvtitov.aichat.model.Invariant;

import java.util.List;

public interface InvariantRepository {
    void save(Invariant invariant);
    List<Invariant> findByBranch(int branch, String profileLogin);
    void deleteByBranch(int branch, String profileLogin);
    void deleteAll(String profileLogin);
}
