package com.github.pvtitov.aichat.repository;

import com.github.pvtitov.aichat.model.ChatMessage;

import java.util.List;

public interface ChatHistoryRepository {
    void saveShortTerm(ChatMessage message);
    void saveMidTerm(ChatMessage message);
    void saveLongTerm(ChatMessage message);

    List<ChatMessage> findShortTermByBranch(int branch, String profileLogin);
    List<ChatMessage> findMidTermByBranch(int branch, String profileLogin);
    List<ChatMessage> findLongTermByBranch(int branch, String profileLogin);

    void deleteByBranch(int branch, String profileLogin);
    void deleteShortTermByBranch(int branch, String profileLogin);
    void deleteMidTermByBranch(int branch, String profileLogin);
    void deleteLongTermByBranch(int branch, String profileLogin);
    void deleteAll(String profileLogin);

    int getMaxBranch(String profileLogin);
    List<Integer> getBranches(String profileLogin);
    long getCumulativeTokens(int branch, String profileLogin);
}
