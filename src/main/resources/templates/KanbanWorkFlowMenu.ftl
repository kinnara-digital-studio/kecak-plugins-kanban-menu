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
      }

      .card-details {
        font-size: 12px;
        color: #666;
      }
      
      .card-details p {
        margin: 4px 0;
      }
    </style>
  </head>
  <body>
    <div id="myKanban"></div>

    <script src="${request.contextPath}/plugin/${className}/node_modules/jkanban/dist/jkanban.js"></script>
    <script>
      var boardsRawData = ${boards! '[]'};
      var cardFormMap = {};

      console.log("jKanban raw data:", boardsRawData);

      var boardsConfig = boardsRawData.map(function(board) {
          var items = board.cards.map(function(card, index) {
              var cardId = card.id;

              if (card.form && card.form !== "") {
                  try {
                      var textarea = document.createElement('textarea');
                      textarea.innerHTML = card.form;
                      var decoded = textarea.value;
                      cardFormMap[cardId] = {
                          form: JSON.parse(decoded),
                          nonce: card.nonce || "",
                          activityId: card.activityId || "",
                          canDrag: card.canDrag || false
                      };
                  } catch(e) {
                      console.warn("Failed to parse form for card " + cardId, e);
                      cardFormMap[cardId] = { form: {}, nonce: "", activityId: "" };
                  }
              } else {
                  cardFormMap[cardId] = { form: {}, nonce: "", activityId: "" };
              }

              var iconHtml = card.isEditable 
                    ? "<i class='fas fa-pencil-alt' style='float:right; color:#888; font-size:12px; margin-top:2px;' title='Edit'></i>" 
                    : "<i class='fas fa-eye' style='float:right; color:#888; font-size:12px; margin-top:2px;' title='View'></i>";

              var html = "";
              html += "<div class='card-title'>" + (card.title || '') + iconHtml + "</div>";
              html += "<div class='card-details'>";
              html += "<p><b>Activity:</b> " + (card.activityName || '') + "</p>";
              html += "<p><b>Requester:</b> " + (card.requesterName || '') + "</p>";
              html += "<p><b>Assignee:</b> " + (card.currentAssigneeName || '') + "</p>";
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

      var kanbanBoard = new jKanban({
          element: "#myKanban",
          gutter: "10px",
          widthBoard: "300px",
          dragBoards: false,
          dragItems: true,
          click: function(el) {
              var cardId = el.getAttribute("data-eid");
              openCardForm(cardId)
          },
          dropEl: function(el, target, source, sibling) {
              var cardId = el.getAttribute("data-eid");
              var entry = cardFormMap[cardId] || {};

              if (!entry.canDrag) {
                  revertCard(cardId, el, source.parentElement.getAttribute("data-id"));
                  return;
              }

              var targetBoardId = target.parentElement.getAttribute("data-id");
              var sourceBoardId = source.parentElement.getAttribute("data-id");
              if (targetBoardId === sourceBoardId) return;

              moveCard(cardId, targetBoardId, sourceBoardId, el);
          },
          boards: boardsConfig
      });

      // Apply background colors to board headers
      boardsConfig.forEach(function(board) {
          if (board.colour) {
              var header = document.querySelector('.kanban-board[data-id="' + board.id + '"] .kanban-board-header');
              if (header) {
                  header.style.backgroundColor = board.colour;
              }
          }
      });

      // Helper Function
      function popupForm(elementId, appId, appVersion, jsonForm, nonce, args, data, height, width) {
          var isEditable = ${editable?c};
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
              _json : JSON.stringify(jsonForm ? jsonForm : {}),
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
          var entry = cardFormMap[cardId] || {};
          var jsonForm = entry.form || {};
          var nonce = entry.nonce || "";

          if (!jsonForm || Object.keys(jsonForm).length === 0) {
              alert("There is No Activity or Form In This Card");
              return;
          }

          var appId = "${appId!''}";
          var appVersion = "${appVersion!''}";
          var data = { id: cardId };
          var height = "800";
          var width = "900";
          var args = {};

          popupForm(cardId, appId, appVersion, jsonForm, nonce, args, data, height, width);
      }

      function moveCard(cardId, targetBoardId, sourceBoardId, el) {
          var entry = cardFormMap[cardId] || {};
          var activityId = entry.activityId || "";

          if (!activityId) {
              alert("This Card Cannot Move, No activity active.");
              revertCard(cardId, el, sourceBoardId);
              return;
          }

          var submitUrl = "${request.contextPath}/web/json/data/assignment/" + activityId;

          jQuery.ajax({
              url: submitUrl,
              method: "POST",
              contentType: "application/json",
              data: JSON.stringify({
                  "${statusField!'status'}": targetBoardId
              }),
              dataType: "json",
              success: function(resp) {
                  if (resp.validation_error) {
                      var errors = Object.values(resp.validation_error).join("\n");
                      revertCard(cardId, el, sourceBoardId);
                      return;
                  }
                  console.log("Card moved successfully", resp);
              },
              error: function(xhr) {
                  console.error("Failed to move card", xhr);
                  revertCard(cardId, el, sourceBoardId);
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
