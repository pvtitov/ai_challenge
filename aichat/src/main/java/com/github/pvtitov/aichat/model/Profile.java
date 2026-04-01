package com.github.pvtitov.aichat.model;

public class Profile {

    private String login;
    private String profileData;
    private int currentBranch;

    public Profile() {
        this.currentBranch = 1; // Default branch
    }

    public Profile(String login, String profileData) {
        this.login = login;
        this.profileData = profileData;
        this.currentBranch = 1; // Default branch
    }

    public String getLogin() {
        return login;
    }

    public void setLogin(String login) {
        this.login = login;
    }

    public String getProfileData() {
        return profileData;
    }

    public void setProfileData(String profileData) {
        this.profileData = profileData;
    }

    public int getCurrentBranch() {
        return currentBranch;
    }

    public void setCurrentBranch(int currentBranch) {
        this.currentBranch = currentBranch;
    }
}
