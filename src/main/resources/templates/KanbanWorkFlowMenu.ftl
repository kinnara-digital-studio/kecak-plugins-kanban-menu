<!DOCTYPE html>
<html lang="en">
  <head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1" />
    <title>Kanban WorkFlow</title>
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

      .kanban-board-header {
        border-radius: 4px 4px 0 0;
      }

      .kanban-item {
        background: #fff;
        border-radius: 4px;
        padding: 10px;
        margin-bottom: 10px;
        box-shadow: 0 1px 3px rgba(0,0,0,0.12);
        cursor: pointer;
      }

      .card-title {
        font-weight: bold;
        margin-bottom: 8px;
        color: #333;
        font-size: 14px;
        display: flex;
        justify-content: space-between;
        align-items: flex-start;
        gap: 8px;
      }

      .card-title-text {
        word-break: break-word;
      }

      .card-icon {
        color: #888;
        font-size: 12px;
        margin-top: 2px;
        flex-shrink: 0;
      }

      .card-details {
        font-size: 12px;
        color: #666;
      }
      .card-details p {
          margin: 4px 0;
          display: flex;
          align-items: center;
          gap: 6px;
      }
      .card-details i {
          width: 14px;
          text-align: center;
          color: #aaa;
          flex-shrink: 0;
      }
    </style>
  </head>
  <body>
    <script type="application/json" id="kanbanRawData">${boards! '[]'}</script>
    <div id="myKanban"></div>

    <script src="${request.contextPath}/plugin/${className}/node_modules/jkanban/dist/jkanban.js"></script>
    <script>
      var cardFormMap = {};
      var kanbanBoard = null;
      var boardFormMap = {};

      var initialRawData = JSON.parse(document.getElementById('kanbanRawData').textContent || '[]');
      initKanban(initialRawData);

      // Initialize Function
      function initKanban(boardsData) {
          cardFormMap = {};
          boardFormMap = {};

          var parseForm = function(rawString) {
              if (!rawString || rawString === "") return { formRaw: "{}"};
              try {
                  var textarea = document.createElement('textarea');
                  textarea.innerHTML = rawString;
                  var decoded = textarea.value;
                  return { formRaw: decoded};
              } catch(e) {
                  console.warn("Failed to parse form", e);
                  return { formRaw: "{}"};
              }
          };

          var boardsConfig = boardsData.map(function(board) {
              var boardId = board.value;
              boardFormMap[boardId] = {
                  editable: {
                      formRaw: parseForm(board.formEditable).formRaw,
                      nonce: board.nonceEditable || ""
                  },
                  readOnly: {
                      formRaw: parseForm(board.formReadOnly).formRaw,
                      nonce: board.nonceReadOnly || ""
                  }
              };

              var items = board.cards.map(function(card, index) {
                  var cardId = card.id;

                  cardFormMap[cardId] = {
                      status: card.status || boardId,
                      isEditable: card.isEditable || false,
                      activityId: card.activityId || "",
                      canDrag: card.canDrag || false
                  };

                  var iconHtml = card.isEditable 
                        ? "<i class='fas fa-pencil-alt card-icon' title='Edit'></i>" 
                        : "<i class='fas fa-eye card-icon' title='View'></i>";

                  var html = "";
                  html += "<div class='card-title'>";
                  html += "  <span class='card-title-text'>" + (card.title || '') + "</span>" + iconHtml;
                  html += "</div>";
                  html += "<div class='card-details'>";
                  html += "<p><i class='fas fa-user-circle'></i> " + (card.requesterName || '') + "</p>";
                  html += "<p><i class='fas fa-file-alt'></i> " + (card.activityName || '') + "</p>";
                  html += "<p><i class='fas fa-inbox'></i> " + (card.currentAssigneeName || '') + "</p>";
                  html += "</div>";

                  return {
                      id: cardId,
                      title: html
                  };
              });

              return {
                  id: board.value,
                  title: board.label,
                  colour: board.colour,
                  item: items
              };
          });

          document.getElementById("myKanban").innerHTML = "";

          kanbanBoard = new jKanban({
              element: "#myKanban",
              gutter: "10px",
              widthBoard: "300px",
              dragBoards: false,
              dragItems: true,
              click: function(el) {
                  var cardId = el.getAttribute("data-eid");
                  openCardForm(cardId);
              },
              dropEl: function(el, target, source, sibling) {
                  var cardId = el.getAttribute("data-eid");
                  var entry = cardFormMap[cardId] || {};

                  var targetBoardId = target.parentElement.getAttribute("data-id");
                  var sourceBoardId = source.parentElement.getAttribute("data-id");
                  if (targetBoardId === sourceBoardId) return;

                  moveCard(cardId, targetBoardId, sourceBoardId, el);
              },
              boards: boardsConfig
          });

          boardsConfig.forEach(function(board) {
              if (board.colour) {
                  var boardContainer = document.querySelector('.kanban-board[data-id="' + board.id + '"]');
                  var header = boardContainer ? boardContainer.querySelector('.kanban-board-header') : null;

                  if (boardContainer) {
                      boardContainer.style.borderRadius = "10px";
                      var fadedColor = board.colour;
                      if (fadedColor.length === 7 && fadedColor.startsWith("#")) {
                          fadedColor = fadedColor + "33"; // 20% opacity
                      }
                      boardContainer.style.backgroundColor = fadedColor;
                  }

                  if (header) {
                      header.style.backgroundColor = board.colour;
                      header.style.borderRadius = "10px 10px 0 0";
                      header.style.color = "#ffffff";
                  }
              }
          });
      }

      // Helper Function
      function refreshKanbanBoard() {
          jQuery.ajax({
              url: window.location.href,
              method: "GET",
              success: function(html) {
                  var parser = new DOMParser();
                  var doc = parser.parseFromString(html, "text/html");
                  var rawDataStr = doc.getElementById('kanbanRawData').textContent;
                  if (rawDataStr) {
                      var newData = JSON.parse(rawDataStr);
                      initKanban(newData);
                      console.log("Kanban board dynamically refreshed!");
                  }
              },
              error: function() {
                  console.error("Error Refresh Kanban Item Data");
              }
          });
      }

      function popupForm(elementId, appId, appVersion, formRaw, nonce, args, data, height, width, isEditable) {
          var label = isEditable ? 'Submit' : 'Close';
          var formUrl = '${request.contextPath}/web/app/' + appId + '/' + appVersion + '/form/embed?_submitButtonLabel=' + label;
          var frameId = args.frameId = 'Frame_' + elementId;

          if (data) {
              for (var key in data) {
                  if (data.hasOwnProperty(key) && data[key]) {
                      if (formUrl.indexOf("?") !== -1) {
                          formUrl += "&";
                      } else {
                          formUrl += "?";
                      }
                      formUrl += encodeURIComponent(key) + "=" + encodeURIComponent(data[key]);
                  }
              }
          }
          formUrl += UI.userviewThemeParams();

          var params = {
              _json : formRaw,
              _callback : 'onSubmitted',
              _setting : JSON.stringify(args ? args : {}).replace(/"/g, "'"),
              _jsonrow : JSON.stringify(data ? data : {}),
              _nonce : nonce
          };
          JPopup.show(frameId, formUrl, params, "", width, height);
      }

      function onSubmitted(args) {
          // Reload halaman setelah submit
          window.location.reload();
      }

      function openCardForm(cardId) {
          var cardData = cardFormMap[cardId] || {};
          var boardId = cardData.status;
          var boardForms = boardFormMap[boardId];
          
          if (!boardForms) {
              alert("No board configuration found for this card.");
              return;
          }
          
          var formConfig = cardData.isEditable ? boardForms.editable : boardForms.readOnly;
          var formRaw = formConfig.formRaw || "{}";
          var nonce = formConfig.nonce || "";

          if (!formRaw || formRaw === "{}" || formRaw === "") {
              alert("There is No Activity or Form In This Card");
              return;
          }

          var appId = "${appId!''}";
          var appVersion = "${appVersion!''}";
          var data = { id: cardId };
          var height = "800";
          var width = "900";
          var args = {};

          popupForm(cardId, appId, appVersion, formRaw, nonce, args, data, height, width, cardData.isEditable);
      }

      function moveCard(cardId, targetBoardId, sourceBoardId, el) {
          var entry = cardFormMap[cardId] || {};
          var activityId = entry.activityId || "";

          if (!entry.canDrag || !activityId) {
              alert("This Card Cannot Move, No Assignee or No Activity yet");
              revertCard(cardId, el, sourceBoardId);
              return;
          }

          var submitUrl = "${request.contextPath}/web/json/data/assignment/" + activityId;

          el.style.opacity = '0.5';

          jQuery.ajax({
              url: submitUrl,
              method: "POST",
              contentType: "application/json",
              data: JSON.stringify({
                  "${statusField!'status'}": targetBoardId
              }),
              dataType: "json",
              success: function(resp) {
                  var isError = resp.validation_error || resp.status === "error" || resp.error || (resp.errors && Object.keys(resp.errors).length > 0);
                  if (isError) {
                      var errors = "Error occurred";
                      if (resp.validation_error) {
                          errors = Object.values(resp.validation_error).join("\n");
                      } else if (resp.errors && typeof resp.errors === 'object') {
                          errors = Object.values(resp.errors).join("\n");
                      } else if (typeof resp.error === 'string') {
                          errors = resp.error;
                      } else if (resp.message) {
                          errors = resp.message;
                      }

                      revertCard(cardId, el, sourceBoardId);
                      alert(errors);
                      el.style.opacity = '1';
                      return;
                  }
                  setTimeout(refreshKanbanBoard, 1000);
              },
              error: function(xhr) {
                  console.error("Failed to move card", xhr);
                  revertCard(cardId, el, sourceBoardId);
                  el.style.opacity = '1';
              }
          });
      }

      function revertCard(cardId, el, sourceBoardId) {
          var itemData = {
              id: cardId,
              title: el.innerHTML
          };
          kanbanBoard.removeElement(cardId);
          kanbanBoard.addElement(sourceBoardId, itemData);
      }
    </script>
  </body>
</html>
