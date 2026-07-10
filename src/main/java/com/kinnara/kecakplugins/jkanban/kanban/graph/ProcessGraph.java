package com.kinnara.kecakplugins.jkanban.kanban.graph;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.joget.commons.util.LogUtil;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.*;

public class ProcessGraph {

    private Map<String, ProcessNode> nodes = new LinkedHashMap<>();
    private List<ColumnResult> cachedColumns = null;
    private JSONObject cachedDragTargets = null;

    public void addNode(String id, String name, boolean isWorkActivity) {
        nodes.putIfAbsent(id, new ProcessNode(id, name, isWorkActivity));
    }
    public Map<String, ProcessNode> getNodes() { return nodes; }

    public void addTransitionDetail(ProcessTransition t) {
        ProcessNode fromNode = nodes.get(t.getFrom());
        if (fromNode != null) {
            fromNode.addOutgoingTransition(t);
        }
    }
    public List<ProcessTransition> getOutgoingTransitions(String activityId) {
        ProcessNode node = nodes.get(activityId);
        return node != null ? node.getOutgoingTransitions() : new ArrayList<>();
    }
    public List<ProcessTransition> getAllTransitions() {
        List<ProcessTransition> all = new ArrayList<>();
        for (ProcessNode n : nodes.values()) {
            all.addAll(n.getOutgoingTransitions());
        }
        return all;
    }

    public List<ColumnResult> computeColumnsBfs() {
        if (cachedColumns != null) {
            return cachedColumns;
        }

        List<ColumnResult> result = new ArrayList<>();

        Set<String> allTargets = new HashSet<>();
        for (ProcessNode n : nodes.values()) {
            for (ProcessTransition t : n.getOutgoingTransitions()) {
                allTargets.add(t.getTo());
            }
        }
        String startId = null;
        for (String id : nodes.keySet()) {
            if (!allTargets.contains(id)) {
                startId = id;
                break;
            }
        }
        if (startId == null) {
            return result;
        }

        Map<String, Integer> visitedLevel = new LinkedHashMap<>();
        Queue<String> queue = new LinkedList<>();

        visitedLevel.put(startId, 0);
        queue.add(startId);

        while (!queue.isEmpty()) {
            String currentId = queue.poll();
            int currentLevel = visitedLevel.get(currentId);
            ProcessNode currentNode = nodes.get(currentId);
            if (currentNode == null) continue;

            for (ProcessTransition t : currentNode.getOutgoingTransitions()) {
                String nextId = t.getTo();
                if (visitedLevel.containsKey(nextId)) {
                    continue;
                }
                ProcessNode nextNode = nodes.get(nextId);
                if (nextNode == null) continue;

                int nextLevel = nextNode.isWorkActivity() ? currentLevel + 1 : currentLevel;
                visitedLevel.put(nextId, nextLevel);
                queue.add(nextId);
            }
        }

        for (Map.Entry<String, Integer> entry : visitedLevel.entrySet()) {
            ProcessNode node = nodes.get(entry.getKey());
            if (node != null && node.isWorkActivity()) {
                result.add(new ColumnResult(node.getId(), node.getName(), entry.getValue()));
            }
        }
        result.sort(Comparator.comparingInt(ColumnResult::getLevel));

        cachedColumns = result;
        return result;
    }

    public JSONObject computeDragTargetsJson(String fromActivityId) {
        JSONObject result = new JSONObject();
        try {
            Set<String> visited = new HashSet<>();
            collectDragTargetsJson(fromActivityId, new ArrayList<>(), visited, result);
        } catch (Exception e) {
            LogUtil.error(getClass().getName(), e, "Failed to compute drag targets for " + fromActivityId);
        }
        return result;
    }

    public JSONObject getAllDragTargetsJson() {
        if (cachedDragTargets != null) {
            return cachedDragTargets;
        }

        JSONObject dragTargetsJson = new JSONObject();
        for (ProcessNode node : nodes.values()) {
            if (node.isWorkActivity()) {
                try {
                    dragTargetsJson.put(node.getId(), computeDragTargetsJson(node.getId()));
                } catch (Exception e) {
                    LogUtil.error(getClass().getName(), e, "Failed to build drag target JSON for " + node.getId());
                }
            }
        }
        cachedDragTargets = dragTargetsJson;
        return cachedDragTargets;
    }

    private void collectDragTargetsJson(String currentId,
                                        List<ProcessTransition.TransitionCondition> accumulatedConditions,
                                        Set<String> visited,
                                        JSONObject result) {
        if (visited.contains(currentId)) return;
        visited.add(currentId);

        ProcessNode currentNode = nodes.get(currentId);
        if (currentNode == null) return;

        for (ProcessTransition t : currentNode.getOutgoingTransitions()) {
            ProcessNode targetNode = nodes.get(t.getTo());
            if (targetNode == null) continue;

            List<ProcessTransition.TransitionCondition> combined = new ArrayList<>(accumulatedConditions);
            combined.addAll(t.getConditions());

            if (targetNode.isWorkActivity()) {
                try {
                    JSONObject targetInfo = getJsonObject(combined);
                    result.put(targetNode.getId(), targetInfo);
                } catch (Exception e) {
                    LogUtil.error(getClass().getName(), e, "Failed to build drag target JSON for " + targetNode.getId());
                }
            } else {
                collectDragTargetsJson(targetNode.getId(), combined, visited, result);
            }
        }
    }

    private static @NonNull JSONObject getJsonObject(List<ProcessTransition.TransitionCondition> combined) throws JSONException {
        JSONObject targetInfo = new JSONObject();
        JSONArray conditionsArr = new JSONArray();
        for (ProcessTransition.TransitionCondition cond : combined) {
            JSONObject condObj = new JSONObject();
            condObj.put("variable", cond.getVariable());
            condObj.put("value", cond.getValue());
            conditionsArr.put(condObj);
        }
        targetInfo.put("conditions", conditionsArr);
        return targetInfo;
    }

    public String debugPrint() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== NODES (").append(nodes.size()).append(") ===\n");
        for (ProcessNode n : nodes.values()) {
            sb.append(n.toString()).append("\n");
        }
        return sb.toString();
    }
}
