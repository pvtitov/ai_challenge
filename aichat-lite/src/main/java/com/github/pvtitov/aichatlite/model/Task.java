package com.github.pvtitov.aichatlite.model;

import java.util.List;

public class Task {
    
    private Long id;
    private Long dialogMessageId;
    private String title;
    private List<String> requirements;
    private List<String> invariants;
    private Verification verification;
    
    public static class Verification {
        private boolean verified;
        private String summary;
        
        public Verification() {
        }
        
        public Verification(boolean verified, String summary) {
            this.verified = verified;
            this.summary = summary;
        }
        
        public boolean isVerified() {
            return verified;
        }
        
        public void setVerified(boolean verified) {
            this.verified = verified;
        }
        
        public String getSummary() {
            return summary;
        }
        
        public void setSummary(String summary) {
            this.summary = summary;
        }
    }
    
    public Task() {
    }
    
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public Long getDialogMessageId() {
        return dialogMessageId;
    }
    
    public void setDialogMessageId(Long dialogMessageId) {
        this.dialogMessageId = dialogMessageId;
    }
    
    public String getTitle() {
        return title;
    }
    
    public void setTitle(String title) {
        this.title = title;
    }
    
    public List<String> getRequirements() {
        return requirements;
    }
    
    public void setRequirements(List<String> requirements) {
        this.requirements = requirements;
    }
    
    public List<String> getInvariants() {
        return invariants;
    }
    
    public void setInvariants(List<String> invariants) {
        this.invariants = invariants;
    }
    
    public Verification getVerification() {
        return verification;
    }
    
    public void setVerification(Verification verification) {
        this.verification = verification;
    }
}
