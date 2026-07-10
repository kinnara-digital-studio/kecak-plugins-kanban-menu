package com.kinnara.kecakplugins.jkanban.kanban.graph;

public class ColumnResult {
    private String activityId;
    private String activityName;
    private int level;

    public ColumnResult(String activityId, String activityName, int level) {
        this.activityId = activityId;
        this.activityName = activityName;
        this.level = level;
    }

    public String getActivityId() { return activityId; }
    public String getActivityName() { return activityName; }
    public int getLevel() { return level; }

    @Override
    public String toString() {
        return "Column[activityId=" + activityId + ", name=" + activityName + ", level=" + level + "]";
    }
}
