package com.github.pvtitov.aichat.dto.state;

import com.github.pvtitov.aichat.model.Profile;

public class ChatState {
    private ConversationState conversationState = new ConversationState();
    private Profile currentProfile;

    public ConversationState getConversationState() {
        return conversationState;
    }

    public void setConversationState(ConversationState conversationState) {
        this.conversationState = conversationState;
    }

    public Profile getCurrentProfile() {
        return currentProfile;
    }

    public void setCurrentProfile(Profile currentProfile) {
        this.currentProfile = currentProfile;
    }
}
