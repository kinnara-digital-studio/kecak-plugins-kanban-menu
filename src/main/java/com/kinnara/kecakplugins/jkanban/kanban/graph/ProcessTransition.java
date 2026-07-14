package com.kinnara.kecakplugins.jkanban.kanban.graph;

import java.util.ArrayList;
import java.util.List;

public class ProcessTransition {
    private String id;
    private String from;
    private String to;
    private String conditionType;
    private List<TransitionCondition> conditions = new ArrayList<>();

    public ProcessTransition(String id, String from, String to) {
        this.id = id;
        this.from = from;
        this.to = to;
    }

    public String getId() { return id; }
    public String getFrom() { return from; }
    public String getTo() { return to; }
    public String getConditionType() { return conditionType; }
    public void setConditionType(String conditionType) { this.conditionType = conditionType; }
    public List<TransitionCondition> getConditions() { return conditions; }

    public boolean isOtherwise() {
        return "OTHERWISE".equals(conditionType);
    }

    @Override
    public String toString() {
        return "Transition[id=" + id + ", from=" + from + ", to=" + to
                + ", conditionType=" + conditionType + ", conditions=" + conditions + "]";
    }

    public static class TransitionCondition {
        private String variable;
        private String operator;
        private String value;
        private String join;

        public TransitionCondition(String variable, String operator, String value, String join) {
            this.variable = variable;
            this.operator = operator;
            this.value = value;
            this.join = join;
        }

        public String getVariable() { return variable; }
        public String getOperator() { return operator; }
        public String getValue() { return value; }
        public String getJoin() { return join; }

        @Override
        public String toString() {
            return variable + " " + operator + " '" + value + "'";
        }
    }
}
