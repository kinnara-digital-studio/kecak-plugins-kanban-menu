# Kanban WorkFlow Menu Plugin

A dynamic, interactive Userview Menu plugin for Kecak Workflow that visualizes your workflow processes as a Kanban Board. 

This plugin transforms your standard Datalist and Workflow assignments into an intuitive drag-and-drop interface, allowing users to move tasks across different stages easily.

## Features
- **Dynamic Board Generation**: Boards (columns) are generated dynamically based on the configured properties.
- **Drag-and-Drop Capability**: Drag a card from one board to another to automatically update its status and trigger the workflow transition.
- **Context-Aware Permissions**: 
  - If the logged-in user is the **Current Assignee** of the task, they can drag the card and open the **Editable Form**.
  - If the user is not the assignee or the process is completed, the card becomes **Read-Only** (indicated by an eye icon) and cannot be dragged. Opening it will show a Read-Only form with a "Close" button instead of "Submit".
- **Form Popup**: Clicking a card opens a modal (JPopup) displaying the specific form for that stage without leaving the Kanban page.
- **Single or Multi-Form Mode**: Choose to use a single global form for all boards, or specify a distinct form for each individual board stage.
## Configuration Properties

When setting up the plugin in the Userview Builder, configure the following:

- **Process**: Select the Workflow Process you want to integrate.
- **DataList**: Select the datalist that contains the records for this workflow.
- **Status Field**: The column ID in the datalist/form that dictates which board the card belongs to (e.g., `status`).
- **Title Field**: The column ID used as the display title for the Kanban card.
- **Single Form Checkbox**: Check this if you want all boards to use the same form.
- **Form (if Single Form)**: Select the global form.
- **Board Options (Grid)**:
  - **Value**: The exact value of the `Status Field` that maps to this board.
  - **Label**: The display name of the board.
  - **Colour**: The hex color code for the board header (e.g., `#2196F3`).

---

## CRITICAL REQUIREMENT: Form Validations

To ensure your Kanban board works harmoniously with your Workflow, **you MUST apply strict validations to your status field in form builder**.

### Why is this important?
In a standard Kecak environment, a user clicks "Complete" on an assignment, and the system moves to the next node. However, in this Kanban plugin, dragging a card from `To Do` to `In Progress` immediately triggers an assignment update via AJAX (`POST` to `/web/json/data/assignment/{activityId}`). 

If the form mapped to that stage has **no validations** (e.g., 'Status Invalid' validators), the drag-and-drop action will succeed, and the workflow will transition to the wrong node and make flow not working correctly.

### Best Practices:
1. **Status Alignment**: If your workflow relies on specific status values to route correctly (e.g., routing to `Approved` or `Rejected`), make sure the Kanban drag-and-drop action logic aligns perfectly with your Process Tool routing logic.
2. **Backend Validation Error Handling**: The Kanban plugin is equipped to catch backend validation errors. If a user drags a card but fails a backend validation, the card will automatically "bounce back" (revert) to its original board, protecting your data integrity. 

