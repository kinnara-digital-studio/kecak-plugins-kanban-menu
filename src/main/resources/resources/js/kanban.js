var kanban = new jKanban({
    element: "#kanban",
    gutter: "10px",
    widthBoard: "250px",
    boards: [
        { id: "1", title: "To Do", item: [] },
        { id: "2", title: "In Progress", item: [] },
        { id: "3", title: "Done", item: [] }
    ]
});