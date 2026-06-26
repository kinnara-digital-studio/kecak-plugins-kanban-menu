package com.kinnara.kecakplugins.jkanban.kanban;

public class KanbanCard {

    private final String recordId;
    private final String title;
    private final String status;
    private final String requesterName;
    private final String currentAssigneeName;
    private final String activityId;
    private final String activityName;
    private final boolean canDrag;
    private final boolean canEdit;


    public KanbanCard(String recordId, String title, String status, String requesterName,
                      String currentAssigneeName, String activityId,
                      String activityName, boolean canDrag, boolean canEdit
    ) {
        this.recordId = recordId;
        this.title = title;
        this.status = status;
        this.requesterName = requesterName;
        this.currentAssigneeName = currentAssigneeName;
        this.activityId = activityId;
        this.activityName = activityName;
        this.canDrag = canDrag;
        this.canEdit = canEdit;
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

    public String getRecordId() {
        return recordId;
    }

    public String getActivityId() {
        return activityId;
    }

    public boolean isCanDrag() {
        return canDrag;
    }

    public boolean isEditable() {
        return canEdit;
    }

//    public String getAssignmentId() {
//        return assignmentId;
//    }
//
//    public String getRecordId() {
//        return recordId;
//    }
}

