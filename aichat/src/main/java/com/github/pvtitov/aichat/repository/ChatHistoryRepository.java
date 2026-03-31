package com.github.pvtitov.aichat.repository;

import com.github.pvtitov.aichat.model.ChatMessage;

import java.util.List;

public interface ChatHistoryRepository {
    void saveShortTerm(ChatMessage message);
    void saveMidTerm(ChatMessage message);
    void saveLongTerm(ChatMessage message);

    List<ChatMessage> findShortTermByBranch(int branch);
    List<ChatMessage> findMidTermByBranch(int branch);
    List<ChatMessage> findLongTermByBranch(int branch);

    void deleteByBranch(int branch);
    void deleteShortTermByBranch(int branch);
    void deleteMidTermByBranch(int branch);
    void deleteLongTermByBranch(int branch);
    void deleteAll();

    int getMaxBranch();
    List<Integer> getBranches();
    long getCumulativeTokens(int branch);
}
