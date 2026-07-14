package com.kinnara.kecakplugins.jkanban.kanban.graph;

import java.util.ArrayList;
import java.util.List;

public class ProcessNode {
    private String id;
    private String name;
    private ActivityType type;
    private List<ProcessTransition> outgoingTransitions = new ArrayList<>();

    public ProcessNode(String id, String name, ActivityType type) {
        this.id = id;
        this.name = name;
        this.type = type;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public boolean isWorkActivity() { return type == ActivityType.WORK_ACTIVITY; }
    public boolean isTool() { return type == ActivityType.TOOL; }
    public boolean isRoute() { return type == ActivityType.ROUTE; }
    public List<ProcessTransition> getOutgoingTransitions() { return outgoingTransitions; }
    public void addOutgoingTransition(ProcessTransition t) { outgoingTransitions.add(t); }

    @Override
    public String toString() {
        return "Activity[id=" + id + ", name=" + name + ", type=" + type
                + ", transitions=" + outgoingTransitions + "]";
    }
}
