<!DOCTYPE html>
<html lang="en">
  <head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1" />
    <title>Kanban</title>
    <link rel="stylesheet" href="${request.contextPath}/plugin/${className}/node_modules/jkanban/dist/jkanban.min.css" />
    <link href="https://fonts.googleapis.com/css?family=Lato" rel="stylesheet" />

    <style>
      body {
        font-family: "Lato", sans-serif;
        margin: 0;
        padding: 0;
        background: #f0f2f5;
      }

      #myKanban {
        overflow-x: auto;
        padding: 20px;
      }

      #kanban-loading {
        padding: 60px;
        text-align: center;
        color: #888;
        font-size: 16px;
      }

      .kanban-board-header {
        border-radius: 4px 4px 0 0;
      }

      .btn {
        padding: 6px 12px;
        border: none;
        border-radius: 4px;
        cursor: pointer;
        font-size: 14px;
        color: white;
      }
      .btn-primary { background: #2196F3; }
      .btn-primary:hover { background: #1976D2; }

      .itemform {
        margin-bottom: 10px;
      }
      .itemform textarea {
        width: 100%;
        border: 1px solid #ccc;
        border-radius: 4px;
        padding: 5px;
        box-sizing: border-box;
      }
      .itemform .form-group {
        margin-bottom: 5px;
      }
    </style>
  </head>
  <body>

    <div id="kanban-loading">Loading Kanban data...</div>
    <div id="myKanban" style="display:none;"></div>

    <script src="${request.contextPath}/plugin/${className}/node_modules/jkanban/dist/jkanban.js"></script>
    <script src="${request.contextPath}/js/jquery.min.js"></script>
    <script>

      var labelField  = "${label!''}";

      var statusField = "${status!''}";

      var apiUrl = "${request.contextPath}/web/json/data/app/${appId}/datalist/${dataListId}";


      var boardsConfig = [
        <#if boards?? && boards?has_content>
          <#list boards as board>
            { id: "${board.value!''}", title: "${board.label!''}", colour: "${board.colour!''}" }<#if board?has_next>,</#if>
          </#list>
        <#else>
          { id: "todo",       title: "To Do",       colour: "#2196F3" },
          { id: "inprogress", title: "In Progress", colour: "#FFC107" },
          { id: "done",       title: "Done",        colour: "#4CAF50" }
        </#if>
      ];

      var kanbanBoard = null;

      function buildKanban(items) {

        var boards = boardsConfig.map(function(boardCfg) {
          var boardItems = [];

          items.forEach(function(record) {
            var itemStatus = record[statusField] || "";
            if (itemStatus === boardCfg.id) {
              boardItems.push({
                id:    String(record["id"] || record["_id"] || record["$value"] || ""),
                title: String(record[labelField] || "(no label)")
              });
            }
          });

          return {
            id:    boardCfg.id,
            title: boardCfg.title,
            item:  boardItems
          };
        });

        document.getElementById("myKanban").innerHTML = "";

        kanbanBoard = new jKanban({
          element: "#myKanban",
          gutter: "10px",
          widthBoard: "300px",
          dragBoards: false,
          dropEl: function(el, target, source, sibling) {
            var targetBoardId = target && target.parentElement
              ? target.parentElement.getAttribute("data-id")
              : null;
            var sourceBoardId = source && source.parentElement
              ? source.parentElement.getAttribute("data-id")
              : null;

            if (!targetBoardId || !sourceBoardId) return;
            if (targetBoardId === sourceBoardId) return;

            updateItemStatus(el, targetBoardId, sourceBoardId);
          },
          boards: boards
        });

        boardsConfig.forEach(function(b) {
          if (b.colour) {
            var boardHeader = document.querySelector('.kanban-board[data-id="' + b.id + '"] .kanban-board-header');
            if (boardHeader) {
              boardHeader.style.backgroundColor = b.colour;
              <#--  boardHeader.style.color = "#ffffff";  -->
            }
          }
        });

        document.getElementById("kanban-loading").style.display = "none";
        document.getElementById("myKanban").style.display       = "block";
      }

      function updateItemStatus(el, targetBoardId, sourceBoardId) {
        var itemId = el.getAttribute("data-eid");

        var targetBoard = boardsConfig.find(function(b) { return b.id === targetBoardId; });
        var targetBoardTitle = targetBoard ? targetBoard.title : targetBoardId;

        console.log("Status changed", { id: itemId, from: sourceBoardId, to: targetBoardId, boardTitle: targetBoardTitle });

        var formData = {};
        formData[statusField]   = targetBoardId; 

        var updateUrl = "${request.contextPath}/web/json/data/app/${appId}/form/${formId!''}/" + itemId;

        jQuery.ajax({
          url:         updateUrl,
          method:      "PUT",
          contentType: "application/json",
          data:        JSON.stringify(formData),
          dataType:    "json",
          success: function(resp) {
            console.log("Status updated", resp);
          },
          error: function(xhr, status, error) {
            console.error("Failed to update status", { itemId: itemId, error: error });

            var itemData = {
              id: el.getAttribute("data-eid"),
              title: el.innerHTML
            };

            kanbanBoard.removeElement(itemData.id);
            kanbanBoard.addElement(sourceBoardId, itemData);
          }
        });
      }

      jQuery.ajax({
        url:      apiUrl,
        method:   "GET",
        dataType: "json",
        success: function(response) {
          var items = (response && response.data) ? response.data : [];
          buildKanban(items);
        },
        error: function(xhr, status, error) {
          document.getElementById("kanban-loading").textContent =
            "Failed to load Kanban data: " + error;
          console.error("Kanban AJAX error:", status, error, xhr.responseText);
        }
      });

    </script>
  </body>
</html>
