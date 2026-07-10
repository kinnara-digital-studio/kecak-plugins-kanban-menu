package com.kinnara.kecakplugins.jkanban.kanban.graph;

import java.util.ArrayList;
import java.util.List;

public class ProcessNode {
    private String id;
    private String name;
    private boolean isWorkActivity;
    private List<ProcessTransition> outgoingTransitions = new ArrayList<>();

    public ProcessNode(String id, String name, boolean isWorkActivity) {
        this.id = id;
        this.name = name;
        this.isWorkActivity = isWorkActivity;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public boolean isWorkActivity() { return isWorkActivity; }
    public List<ProcessTransition> getOutgoingTransitions() { return outgoingTransitions; }
    public void addOutgoingTransition(ProcessTransition t) { outgoingTransitions.add(t); }

    @Override
    public String toString() {
        return "Activity[id=" + id + ", name=" + name
                + ", isWork=" + isWorkActivity
                + ", transitions=" + outgoingTransitions + "]";
    }
}
