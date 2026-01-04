package com.ecm.core.sanity;

import lombok.Builder;
import lombok.Builder.Default;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
public class SanityCheckReport {
    private String checkName;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Status status;
    @Default
    private List<String> issues = new ArrayList<>();
    @Default
    private List<String> fixes = new ArrayList<>();
    private int itemsChecked;
    private int issuesFound;
    private int itemsFixed;

    public enum Status {
        SUCCESS, WARNING, ERROR, IN_PROGRESS
    }
    
    public void addIssue(String issue) {
        this.issues.add(issue);
        this.issuesFound++;
        if (this.status == Status.SUCCESS) {
            this.status = Status.WARNING;
        }
    }
    
    public void addFix(String fix) {
        this.fixes.add(fix);
        this.itemsFixed++;
    }
}
