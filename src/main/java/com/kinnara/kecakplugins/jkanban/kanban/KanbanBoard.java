package com.kinnara.kecakplugins.jkanban.kanban;

import java.util.ArrayList;
import java.util.List;

public class KanbanBoard {
    private final String value;
    private final String label;
    private final String colour;
    
    private final List<KanbanCard> cards = new ArrayList<>();

    public KanbanBoard(String value, String label, String colour) {
        this.value = value;
        this.label  = label;
        this.colour = colour;
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
}
