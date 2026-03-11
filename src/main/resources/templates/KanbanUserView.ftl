<!DOCTYPE html>
<html lang="en">
  <head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1" />

    <title>Title</title>
    <link rel="stylesheet" href="${request.contextPath}/plugin/${className}/node_modules/jkanban/dist/jkanban.min.css" />
    <link
      href="https://fonts.googleapis.com/css?family=Lato"
      rel="stylesheet"
    />

    <style>
      body {
        font-family: "Lato";
        margin: 0;
        padding: 0;
      }

      #myKanban {
        overflow-x: auto;
        padding: 20px 0;
      }

      .success {
        background: #00b961;
      }

      .info {
        background: #2a92bf;
      }

      .warning {
        background: #f4ce46;
      }


      .error {
        background: #fb7d44;
      }

      .custom-button {
        background-color: #4CAF50;
        border: none;
        color: white;
        padding: 6px 12px;
        margin: 10px;
        text-align: center;
        text-decoration: none;
        display: inline-block;
        font-size: 14px;
        border-radius: 4px;
        cursor: pointer;
      }

      .custom-button:hover {
        background-color: #45a049;
      }

      .control-panel {
        background: #f8f9fa;
        padding: 15px 20px;
        border-bottom: 1px solid #e7e7e7;
        margin-bottom: 20px;
        display: flex;
        gap: 20px;
        align-items: center;
        flex-wrap: wrap;
      }

      .control-group {
        display: flex;
        gap: 8px;
        align-items: center;
      }

      .control-input {
        padding: 6px 10px;
        border: 1px solid #ccc;
        border-radius: 4px;
        font-size: 14px;
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
      
      .btn-success { background: #4CAF50; }
      .btn-success:hover { background: #388E3C; }
      
      .btn-danger { background: #f44336; }
      .btn-danger:hover { background: #d32f2f; }
      
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
    <div class="control-panel">
      <div class="control-group">
        <input type="text" id="newBoardTitle" class="control-input" placeholder="Board Title..." />
        <button id="addBoardBtn" class="btn btn-primary">Add Board</button>
      </div>

      <div class="control-group">
        <select id="boardSelect" class="control-input">
          <option value="_todo">To Do</option>
          <option value="_working">Working</option>
          <option value="_done">Done</option>
        </select>
        <button id="removeBoardBtn" class="btn btn-danger">Remove Selected Board</button>
      </div>
    </div>
    
    <div id="myKanban"></div>

    <script src="${request.contextPath}/plugin/${className}/node_modules/jkanban/dist/jkanban.js"></script>
    <script>
      var KanbanTest = new jKanban({
        element: "#myKanban",
        gutter: "10px",
        widthBoard: "450px",
        <#--  itemHandleOptions:{
          enabled: true,
        },  -->
        click: function(el) {
          console.log("Trigger on all items click!");
        },
        dropEl: function(el, target, source, sibling){
          console.log(target.parentElement.getAttribute('data-id'));
          console.log(el, target, source, sibling)
        },
        buttonClick: function(el, boardId) {
          console.log(el);
          console.log(boardId);
          // create a form to enter element
          var formItem = document.createElement("form");
          formItem.setAttribute("class", "itemform");
          formItem.innerHTML =
            '<div class="form-group"><textarea class="form-control" rows="2" autofocus></textarea></div><div class="form-group"><button type="submit" class="btn btn-primary btn-xs pull-right">Submit</button><button type="button" id="CancelBtn" class="btn btn-default btn-xs pull-right">Cancel</button></div>';

          KanbanTest.addForm(boardId, formItem);
          formItem.addEventListener("submit", function(e) {
            e.preventDefault();
            var text = e.target[0].value;
            KanbanTest.addElement(boardId, {
              title: text
            });
            formItem.parentNode.removeChild(formItem);
          });
          document.getElementById("CancelBtn").onclick = function() {
            formItem.parentNode.removeChild(formItem);
          };
        },
        itemAddOptions: {
          enabled: true,
          content: '+ Add New Card',
          class: 'custom-button',
          footer: true
        },
        boards: [
          {
            id: "_todo",
            title: "To Do",
            class: "info,good",
            <#--  dragTo: ["_working"],  -->
            item: [
              {
                id: "_test_delete",
                title: "Try drag this (Look the console)",
                drag: function(el, source) {
                  console.log("START DRAG: " + el.dataset.eid);
                },
                dragend: function(el) {
                  console.log("END DRAG: " + el.dataset.eid);
                },
                drop: function(el) {
                  console.log("DROPPED: " + el.dataset.eid);
                }
              },
              {
                title: "Try Click This!",
                click: function(el) {
                  alert("click");
                },
                class: ["peppe", "bello"]
              }
            ]
          },
          {
            id: "_working",
            title: "Working",
            class: "warning",
            item: [
              {
                title: "Do Something!"
              },
              {
                title: "Run?"
              }
            ]
          },
          {
            id: "_done",
            title: "Done",
            class: "success",
            <#--  dragTo: ["_working"],  -->
            item: [
              {
                title: "All right"
              },
              {
                title: "Ok!"
              }
            ]
          }
        ]
      });

      function updateBoardSelect() {
          var select = document.getElementById("boardSelect");
          select.innerHTML = '';
          var boards = document.querySelectorAll('.kanban-board');
          boards.forEach(function(board) {
              var id = board.getAttribute('data-id');
              var title = board.querySelector('.kanban-title-board').textContent;
              var option = document.createElement('option');
              option.value = id;
              option.textContent = title;
              select.appendChild(option);
          });
      }

      document.getElementById("addBoardBtn").addEventListener("click", function() {
        var titleInput = document.getElementById("newBoardTitle");
        var title = titleInput.value.trim();
        
        if(title === "") {
            alert("Please enter a board title");
            return;
        }

        var newId = "_" + title.toLowerCase().replace(/\s+/g, "");
        
        KanbanTest.addBoards([
          {
            id: newId,
            title: title,
            item: []
          }
        ]);
        
        titleInput.value = "";
        updateBoardSelect();
      });

      document.getElementById("removeBoardBtn").addEventListener("click", function() {
        var select = document.getElementById("boardSelect");
        if(select.value) {
            KanbanTest.removeBoard(select.value);
            updateBoardSelect();
        }
      });
    </script>
  </body>
</html>
