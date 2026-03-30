package com.github.pvtitov.aichat.repository;

import com.github.pvtitov.aichat.model.ChatMessage;

import java.util.List;

public interface ChatHistoryRepository {

    void save(ChatMessage message);

    List<ChatMessage> findByBranch(int branch);

    void deleteByBranch(int branch);

    int getMaxBranch();

    List<Integer> getBranches();

    long getCumulativeTokens(int branch);
}
