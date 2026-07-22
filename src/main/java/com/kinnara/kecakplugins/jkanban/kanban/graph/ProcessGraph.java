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

    public void addNode(String id, String name, ActivityType type) {
        nodes.putIfAbsent(id, new ProcessNode(id, name, type));
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

    //find column Board
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

        // tambahkan end node boards dari setiap work activity yang sudah ketemu
        Set<String> addedEndNodes = new HashSet<>(); // cegah duplikat kalau 2 activity nunjuk end node yang sama
        for (Map.Entry<String, Integer> entry : visitedLevel.entrySet()) {
            ProcessNode node = nodes.get(entry.getKey());
            if (node == null || !node.isWorkActivity()) continue;

            int activityLevel = entry.getValue();
            List<ProcessNode> endNodes = findEndNodeBoards(node.getId());

            for (ProcessNode endNode : endNodes) {
                if (addedEndNodes.contains(endNode.getId())) continue; // sudah ditambahkan dari activity lain
                addedEndNodes.add(endNode.getId());
                result.add(new ColumnResult(endNode.getId(), endNode.getName(), activityLevel + 1));
            }
        }
        result.sort(Comparator.comparingInt(ColumnResult::getLevel));
        cachedColumns = result;
        return result;
    }

    public List<ProcessNode> findEndNodeBoards(String fromActivityId) {
        List<ProcessNode> results = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        collectEndNodeBoards(fromActivityId, visited, results);
        return results;
    }
    private void collectEndNodeBoards(String currentId, Set<String> visited, List<ProcessNode> results) {
        if (visited.contains(currentId)) return;
        visited.add(currentId);

        ProcessNode currentNode = nodes.get(currentId);
        if (currentNode == null) return;

        for (ProcessTransition t : currentNode.getOutgoingTransitions()) {
            ProcessNode targetNode = nodes.get(t.getTo());
            if (targetNode == null) continue;

            if (targetNode.isRoute()) {
                // tembus route seperti biasa
                collectEndNodeBoards(targetNode.getId(), visited, results);
            } else if (targetNode.isTool()) {
                // ini kandidat board -- cek apakah rantai setelahnya tool sampai dead-end
                if (isAllToolsUntilDeadEnd(targetNode.getId(), new HashSet<>())) {
                    results.add(targetNode);
                }
                // kalau tidak valid, tidak ditambahkan -- tidak tembus lebih jauh dari sini
                // (karena tool sendiri yang jadi kandidat, bukan tool di baliknya)
            }
            // kalau targetNode.isWorkActivity() -> bukan end node, tidak relevan
            // (BFS kolom biasa yang urus activity ini, bukan method ini)
        }
    }
    private boolean isAllToolsUntilDeadEnd(String nodeId, Set<String> chainVisited) {
        if (chainVisited.contains(nodeId)) return false;
        chainVisited.add(nodeId);

        ProcessNode node = nodes.get(nodeId);
        if (node == null) return false;

        // Cek apakah tool
        if (!node.isTool()) {
            return false;
        }
        //cek dead-end
        if (node.getOutgoingTransitions().isEmpty()) {
            return true;
        }

        for (ProcessTransition t : node.getOutgoingTransitions()) {
            ProcessNode nextNode = nodes.get(t.getTo());
            if (nextNode == null) return false;
            if (!isAllToolsUntilDeadEnd(nextNode.getId(), chainVisited)) {
                return false;
            }
        }
        return true;
    }

    public String findEndNodeBoardByStatus(String statusValue) {
        String otherwiseFallback = null;

        for (ProcessNode node : nodes.values()) {
            if (!node.isTool()) continue;
            if (!isAllToolsUntilDeadEnd(node.getId(), new HashSet<>())) continue;

            for (ProcessNode possibleSource : nodes.values()) {
                for (ProcessTransition t : possibleSource.getOutgoingTransitions()) {
                    if (!t.getTo().equals(node.getId())) continue;

                    if (t.isOtherwise()) {
                        otherwiseFallback = node.getId(); // simpan sebagai fallback
                    } else {
                        for (ProcessTransition.TransitionCondition cond : t.getConditions()) {
                            if (statusValue != null && statusValue.equals(cond.getValue())) {
                                return node.getId(); // MATCH langsung, return
                            }
                        }
                    }
                }
            }
        }
        return otherwiseFallback;
    }

    //drag and drop data validation
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
                    result.put(targetNode.getId(), getJsonObject(combined));
                } catch (Exception e) {
                    LogUtil.error(getClass().getName(), e, "Failed to build drag target JSON for " + targetNode.getId());
                }
            } else if (targetNode.isTool() && isAllToolsUntilDeadEnd(targetNode.getId(), new HashSet<>())) {
                try {
                    result.put(targetNode.getId(), getJsonObject(combined));
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
