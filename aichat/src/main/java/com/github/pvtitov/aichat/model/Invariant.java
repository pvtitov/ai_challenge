package com.github.pvtitov.aichat.model;

public class Invariant {
    private Long id;
    private String text;
    private int branch;
    private String profileLogin;

    public Invariant() {
    }

    public Invariant(String text, int branch, String profileLogin) {
        this.text = text;
        this.branch = branch;
        this.profileLogin = profileLogin;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public int getBranch() {
        return branch;
    }

    public void setBranch(int branch) {
        this.branch = branch;
    }

    public String getProfileLogin() {
        return profileLogin;
    }

    public void setProfileLogin(String profileLogin) {
        this.profileLogin = profileLogin;
    }

    @Override
    public String toString() {
        return "Invariant{" +
               "id=" + id +
               ", text='" + text + "'" +
               ", branch=" + branch +
               ", profileLogin='" + profileLogin + "'" +
               '}';
    }
}
