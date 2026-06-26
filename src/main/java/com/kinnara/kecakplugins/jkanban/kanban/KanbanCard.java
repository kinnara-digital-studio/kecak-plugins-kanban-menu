package com.kinnara.kecakplugins.jkanban.kanban;

public class KanbanCard {

    private final String recordId;
    private final String title;
    private final String status;
    private final String requesterName;
    private final String currentAssigneeName;
    private final String activityId;
    private final String activityName;
    private final String form;
    private final String nonce;
    private final boolean canDrag;
    private final boolean isEditable;

    public KanbanCard(String recordId, String title, String status, String requesterName,
                      String currentAssigneeName, String activityId,
                      String activityName,
                      String form,
                      String nonce, boolean canDrag, boolean isEditable
    ) {
        this.recordId = recordId;
        this.title = title;
        this.status = status;
        this.requesterName = requesterName;
        this.currentAssigneeName = currentAssigneeName;
        this.activityId = activityId;
        this.activityName = activityName;
        this.form = form;
        this.nonce = nonce;
        this.canDrag = canDrag;
        this.isEditable = isEditable;
    }

    public String getCurrentAssigneeName() {
        return currentAssigneeName;
    }

    public String getTitle() {
        return title;
    }

    public String getStatus() {
        return status;
    }

    public String getActivityName() {
        return activityName;
    }

    public String getRequesterName() {
        return requesterName;
    }

    public String getForm() {
        return form;
    }

    public String getRecordId() {
        return recordId;
    }

    public String getNonce() {
        return nonce;
    }

    public String getActivityId() {
        return activityId;
    }

    public boolean isCanDrag() {
        return canDrag;
    }

    public boolean isEditable() {
        return isEditable;
    }

//    public String getAssignmentId() {
//        return assignmentId;
//    }
//
//    public String getRecordId() {
//        return recordId;
//    }
}

