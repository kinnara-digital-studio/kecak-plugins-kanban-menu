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
      .btn-default { background: #9e9e9e; }
      .btn-default:hover { background: #757575; }

      .kanban-add-btn {
        width: 100%;
        margin-top: 10px;
        padding: 8px;
        background: #e0e0e0;
        color: #333;
        font-weight: bold;
      }
      .kanban-add-btn:hover { background: #d5d5d5; }

      .itemform {
        margin-bottom: 10px;
        padding: 10px;
        background: #fff;
        border-radius: 4px;
        box-shadow: 0 1px 3px rgba(0,0,0,0.12);
      }
      .itemform textarea {
        width: 100%;
        border: 1px solid #ccc;
        border-radius: 4px;
        padding: 5px;
        box-sizing: border-box;
        resize: vertical;
      }
      .itemform .form-group {
        margin-bottom: 5px;
      }

      /* Item Actions Menu */
      .kanban-item {
        position: relative;
      }
      .item-content {
        padding-right: 25px; /* space for the dots */
      }
      .item-actions {
        position: absolute;
        top: 10px;
        right: 10px;
      }
      .dropdown-menu {
        display: none;
        position: absolute;
        right: 0;
        top: 25px;
        background: white;
        box-shadow: 0 2px 5px rgba(0,0,0,0.2);
        border: 1px solid #ccc;
        border-radius: 4px;
        z-index: 1000;
        min-width: 100px;
        padding: 5px 0;
      }
      .dropdown-menu.show {
        display: block;
      }
      .dropdown-item {
        display: block;
        padding: 8px 15px;
        text-decoration: none;
        color: #333;
        cursor: pointer;
        font-size: 14px;
      }
      .dropdown-item:hover {
        background: #f0f0f5;
        color: #2196F3;
      }
      .btn-dots {
        background: transparent;
        border: none;
        cursor: pointer;
        font-size: 18px;
        padding: 0 5px;
        color: #888;
        line-height: 1;
      }
      .btn-dots:hover {
        color: #333;
      }
    </style>
  </head>
  <body>

    <input type='hidden' id='${elementUniqueKey}-jsonForm' value="${jsonForm!}" >
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

      function popupForm(elementId, appId, appVersion, jsonForm, nonce, args, data, height, width) {
          let formUrl = '${request.contextPath}/web/app/' + appId + '/' + appVersion + '/form/embed?_submitButtonLabel=Submit';
          let frameId = args.frameId = 'Frame_' + elementId;

          if (data && data.id) {
              if (formUrl.indexOf("?") != -1) {
                  formUrl += "&";
              } else {
                  formUrl += "?";
              }
              formUrl += "id=" + data.id;
          }
          formUrl += UI.userviewThemeParams();

          var params = {
              _json : JSON.stringify(jsonForm ? jsonForm : {}),
              _callback : 'onSubmitted',
              _setting : JSON.stringify(args ? args : {}).replace(/"/g, "'"),
              _jsonrow : JSON.stringify(data ? data : {}),
              _nonce : nonce
          };
          JPopup.show(frameId, formUrl, params, "", width, height);
      }

      function onSubmitted(args) {
          let result = JSON.parse(args.result);
          let frameId = args.frameId;

          if (JPopup) {
            JPopup.hide(frameId);
          }

          if (result && result.id) {
            var updatedId = result.id;
            var updatedLabel = result[labelField] || "(no label)";
            var updatedStatus = result[statusField] || "";

            var itemData = {
              id: updatedId,
              title: createItemHtml(updatedLabel)
            };

            kanbanBoard.removeElement(updatedId);
            if (updatedStatus) {
              kanbanBoard.addElement(updatedStatus, itemData);
            }
          }
      }

      var jsonForm = $('input#${elementUniqueKey}-jsonForm').val() ? JSON.parse($('input#${elementUniqueKey}-jsonForm').val()) : {};
      var nonce = '${nonce!}';
      var appVersion = "${appVersion}";

      function createItemHtml(label) {
        return '<div class="item-content">' + label + '</div>' +
               '<div class="item-actions">' +
                 '<button type="button" class="btn-dots" onclick="toggleDropdown(event, this)">&#8942;</button>' +
                 '<div class="dropdown-menu">' +
                   '<a class="dropdown-item" onclick="showEditForm(event, this)">Edit</a>' +
                   '<a class="dropdown-item" onclick="confirmDelete(event, this)">Delete</a>' +
                 '</div>' +
               '</div>';
      }

      function toggleDropdown(event, btn) {
        event.stopPropagation();
        var menu = btn.nextElementSibling;
        var isShowing = menu.classList.contains('show');
        
        document.querySelectorAll('.dropdown-menu.show').forEach(function(m) {
          m.classList.remove('show');
        });

        if (!isShowing) {
          menu.classList.add('show');
        }
      }

      document.addEventListener('click', function() {
        document.querySelectorAll('.dropdown-menu.show').forEach(function(m) {
          m.classList.remove('show');
        });
      });

      function showEditForm(event, btn) {
        event.stopPropagation();
        var menu = btn.closest('.dropdown-menu');
        if (menu) menu.classList.remove('show');

        var kanbanItem = btn.closest('.kanban-item');
        var itemId = kanbanItem.getAttribute("data-eid");

        // Use popupForm explicitly with data containing the item id
        var data = { id: itemId };
        var height = "800";
        var width = "900";
        var args = {}; 
        
        var appId = "${appId!''}";
        var formId = "${formId!''}";
        
        popupForm(formId, appId, appVersion, jsonForm, nonce, args, data, height, width);
      }

      function confirmDelete(event, btn) {
        event.stopPropagation();
        var menu = btn.closest('.dropdown-menu');
        if (menu) menu.classList.remove('show');

        if (confirm("Are you sure you want to delete this item?")) {
          var kanbanItem = btn.closest('.kanban-item');
          var sourceBoardId = kanbanItem.closest('.kanban-board').getAttribute("data-id");
          deleteItem(kanbanItem, sourceBoardId);
        }
      }

      function buildKanban(items) {

        var boards = boardsConfig.map(function(boardCfg) {
          var boardItems = [];

          items.forEach(function(record) {
            var itemStatus = record[statusField] || "";
            if (itemStatus === boardCfg.id) {
              boardItems.push({
                id:    String(record["id"] || record["_id"] || record["$value"] || ""),
                title: createItemHtml(String(record[labelField] || "(no label)"))
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
          itemAddOptions: {
            enabled: true,
            content: '+ Add Item',
            class: 'btn kanban-add-btn',
            footer: true
          },
          buttonClick: function(el, boardId) {

            var data = {};
            data[statusField] = boardId;

            var height = "800";
            var width = "900";
            var args = {};
            
            var appId = "${appId!''}";
            var formId = "${formId!''}";
            
            popupForm(formId, appId, appVersion, jsonForm, nonce, args, data, height, width);
          },

            <#--  var existingForm = el.parentNode.querySelector('.itemform');
            if (existingForm) {
              existingForm.querySelector("textarea").focus();
              return;
            }

            var formItem = document.createElement("form");
            formItem.setAttribute("class", "itemform");
            formItem.innerHTML =
              '<div class="form-group"><textarea placeholder="Enter item label" required></textarea></div>' +
              '<div class="form-group" style="display:flex; gap: 5px;">' +
              '<button type="submit" class="btn btn-primary">Submit</button>' +
              '<button type="button" class="btn btn-default btn-cancel">Cancel</button>' +
              '</div>';


            kanbanBoard.addForm(boardId, formItem);
            
            formItem.querySelector("textarea").focus();

            formItem.addEventListener("submit", function(e) {
              e.preventDefault();
              var text = e.target[0].value;
              var submitBtn = formItem.querySelector('button[type="submit"]');
              submitBtn.disabled = true;
              submitBtn.textContent = "Saving...";
              
              addItem(text, boardId, formItem);
            });
            
            formItem.querySelector(".btn-cancel").addEventListener("click", function() {
              formItem.parentNode.removeChild(formItem);
            });
          -->
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

      function addItem(label, sourceBoardId, formElement){
        var formData = {};
        formData[labelField] = label;
        formData[statusField] = sourceBoardId;

        var createUrl = "${request.contextPath}/web/json/data/app/${appId}/form/${formId!''}"

        jQuery.ajax({
          url:         createUrl,
          method:      "POST",
          contentType: "application/json",
          data:        JSON.stringify(formData),
          dataType:    "json",
          success: function(resp) {
            console.log("Item Created", resp);
            var newId = (resp && resp.id) ? resp.id : ("new_" + Math.random().toString(36).substr(2, 9));
            
            kanbanBoard.addElement(sourceBoardId, {
              id: newId,
              title: createItemHtml(label)
            });

            if (formElement && formElement.parentNode) {
              formElement.parentNode.removeChild(formElement);
            }
          },
          error: function(xhr, status, error) {
            console.error("Failed to create item", { error: error });
            alert("Failed to create item. Please try again.");
            
            if (formElement) {
              var submitBtn = formElement.querySelector('button[type="submit"]');
              submitBtn.disabled = false;
              submitBtn.textContent = "Submit";
            }
          }
        });
        
      }

      function deleteItem(el, sourceBoardId){
        var itemId = el.getAttribute("data-eid");
        var deleteUrl = "${request.contextPath}/web/json/data/app/${appId}/form/${formId!''}/" + itemId;

        jQuery.ajax({
          url:         deleteUrl,
          method:      "DELETE",
          contentType: "application/json",
          dataType:    "json",
          success: function(resp) {
            console.log("Item Deleted", resp);
            kanbanBoard.removeElement(itemId);
          },
          error: function(xhr, status, error) {
            console.error("Failed to delete item", { error: error });
            alert("Failed to delete item. Please try again.");
          }
        });
        
      }

      function editItem(el, sourceBoardId, label, originalHtml){
        var itemId = el.getAttribute("data-eid");

        var formData = {};
        formData[labelField] = label;
        formData[statusField] = sourceBoardId;

        var editUrl = "${request.contextPath}/web/json/data/app/${appId}/form/${formId!''}/" + itemId;

        jQuery.ajax({
          url:         editUrl,
          method:      "PUT",
          contentType: "application/json",
          data:        JSON.stringify(formData),
          dataType:    "json",
          success: function(resp) {
            console.log("Item Updated", resp);
            kanbanBoard.removeElement(itemId);        
            kanbanBoard.addElement(sourceBoardId, {
              id: itemId,
              title: createItemHtml(label)
            });
          },
          error: function(xhr, status, error) {
            console.error("Failed to update item", { error: error });
            alert("Failed to update item. Please try again.");
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
