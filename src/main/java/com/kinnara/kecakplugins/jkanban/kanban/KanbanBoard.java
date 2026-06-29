package com.kinnara.kecakplugins.jkanban.kanban;

import java.util.ArrayList;
import java.util.List;

public class KanbanBoard {
    private final String value;
    private final String label;
    private final String colour;

    private String formEditable;
    private String nonceEditable;
    private String formReadOnly;
    private String nonceReadOnly;

    private final List<KanbanCard> cards = new ArrayList<>();

    public KanbanBoard(String value, String label, String colour, 
                       String formEditable, String nonceEditable, 
                       String formReadOnly, String nonceReadOnly) {
        this.value = value;
        this.label  = label;
        this.colour = colour;
        this.formEditable = formEditable != null ? formEditable : "";
        this.nonceEditable = nonceEditable != null ? nonceEditable : "";
        this.formReadOnly = formReadOnly != null ? formReadOnly : "";
        this.nonceReadOnly = nonceReadOnly != null ? nonceReadOnly : "";
    }

    public String getLabel() {
        return label;
    }

    public String getColour() {
        return colour;
    }

    public String getValue() {
        return value;
    }

    public List<KanbanCard> getCards() {
        return cards;
    }

    public void addCard(KanbanCard card) { cards.add(card); }

    public String getFormEditable() { return formEditable; }
    public String getNonceEditable() { return nonceEditable; }
    public String getFormReadOnly() { return formReadOnly; }
    public String getNonceReadOnly() { return nonceReadOnly; }

    public void setFormEditable(String formEditable) {
        this.formEditable = formEditable;
    }

    public void setNonceEditable(String nonceEditable) {
        this.nonceEditable = nonceEditable;
    }

    public void setNonceReadOnly(String nonceReadOnly) {
        this.nonceReadOnly = nonceReadOnly;
    }

    public void setFormReadOnly(String formReadOnly) {
        this.formReadOnly = formReadOnly;
    }
}
