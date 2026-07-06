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
		.kanban-board-header {
			border-radius: 4px 4px 0 0;
		}
		#kanban-loading {
			padding: 60px;
			text-align: center;
			color: #888;
			font-size: 16px;
		}
		.kanban-item {
			background: #fff;
			border-radius: 4px;
			padding: 10px;
			margin-bottom: 10px;
			box-shadow: 0 1px 3px rgba(0,0,0,0.12);
			cursor: pointer;
			position: relative;
		}
		.kanban-item-editable {
			background: #fff;
			box-shadow: -5px -5px 0px 0px rgba(0, 0, 0, 0.15);
			padding: 8px;
			margin: -11px;
			border-radius: 6px;
		}
		.kanban-item-readonly {
			background: #f0f0f0;
			padding: 8px;
			margin: -11px;
			border-radius: 6px;
		}
		.kanban-item-readonly .card-title-text {
			color: #999;
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
			padding-right: 20px;
		}
		.card-title-text {
			word-break: break-word;
		}
		
		.btn {
			padding: 6px 12px;
			border: none;
			border-radius: 4px;
			cursor: pointer;
			font-size: 14px;
			color: white;
		}
		.kanban-add-btn {
			width: 100%;
			margin-top: 10px;
			padding: 8px;
			background: #e0e0e0;
			color: #333;
			font-weight: bold;
		}
		.kanban-add-btn:hover { background: #d5d5d5; }

		/* Item Actions Menu */
		.item-actions {
			position: absolute;
			top: 10px;
			right: 10px;
			z-index: 10;
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
	<script type="application/json" id="kanbanRawData">${boardsData! '[]'}</script>
	<div id="kanban-loading" style="display:none;">Loading Kanban data...</div>
	<div id="myKanban"></div>

	<script src="${request.contextPath}/plugin/${className}/node_modules/jkanban/dist/jkanban.js"></script>
	<script src="${request.contextPath}/js/jquery.min.js"></script>
	<script>
		var labelField  = "${label!''}";
		var statusField = "${status!''}";
		var canMoveField = "${canMove!''}";
		var hasPermissionToEdit = ${editable?c};
		var kanbanBoard = null;
		var canMoveMap = {};
		
		var jsonForm = $('input#${elementUniqueKey}-jsonForm').val() ? JSON.parse($('input#${elementUniqueKey}-jsonForm').val()) : {};
		var nonce = '${nonce!}';
		var appVersion = "${appVersion}";
		var appId = "${appId!''}";
		var formId = "${formId!''}";
		
		var datalistRowActions = [
			<#if rowActions??>
				<#list rowActions as action>
					{
						id: "${action.id!''}",
						label: "${action.label!''}"
					}<#if action?has_next>,</#if>
				</#list>
			</#if>
		];

		function buildKanban(boardsData) {
			canMoveMap = {};
			
			var boardsConfig = boardsData.map(function(board) {
				var items = board.cards.map(function(card) {
					var cardId = card.id;
					canMoveMap[cardId] = card.canDrag;

					return {
						id: cardId,
						title: createItemHtml(card.title || '', card.isEditable)
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
				dragItems: hasPermissionToEdit,
				itemAddOptions: {
					enabled: hasPermissionToEdit,
					content: '+ Add Item',
					class: 'btn kanban-add-btn',
					footer: true
				},
				click: function(el) {
					var cardId = el.getAttribute("data-eid");
					openCardForm(cardId);
				},
				buttonClick: function(el, boardId) {
					if (!hasPermissionToEdit) return;

					var data = {};
					data[statusField] = boardId;
					var height = "800";
					var width = "900";
					var args = {};
					
					popupForm(formId, appId, appVersion, jsonForm, nonce, args, data, height, width, true);
				},
				dropEl: function(el, target, source, sibling) {
					if (!hasPermissionToEdit) return;

					var targetBoardId = target && target.parentElement ? target.parentElement.getAttribute("data-id") : null;
					var sourceBoardId = source && source.parentElement ? source.parentElement.getAttribute("data-id") : null;

					if (targetBoardId === null || sourceBoardId === null) return;
					if (targetBoardId === sourceBoardId) return;

					var itemId = el.getAttribute("data-eid");

					if (canMoveMap[itemId] === false) {
						var itemData = {
							id: el.getAttribute("data-eid"),
							title: el.innerHTML
						};
						kanbanBoard.removeElement(itemData.id);
						kanbanBoard.addElement(sourceBoardId, itemData);
						return;
					}

					var cleanTargetId = (targetBoardId === "null") ? "" : targetBoardId;
					var cleanSourceId = (sourceBoardId === "null") ? "" : sourceBoardId;

					updateItemStatus(el, cleanTargetId, cleanSourceId);
				},
				boards: boardsConfig
			});

			document.querySelectorAll('.kanban-item').forEach(function(el) {
				var id = el.getAttribute('data-eid');
				if (canMoveMap[id] === false) {
					el.style.cursor = 'default';
					el.title = 'Item ini tidak dapat dipindah';
				}
			});

			boardsData.forEach(function(b) {
				if (b.colour) {
					var boardContainer = document.querySelector('.kanban-board[data-id="' + b.value + '"]');
					var header = boardContainer ? boardContainer.querySelector('.kanban-board-header') : null;

					if (boardContainer) {
						boardContainer.style.borderRadius = "10px";
						var fadedColor = b.colour;
						if (fadedColor.length === 7 && fadedColor.startsWith("#")) {
							fadedColor = fadedColor + "33"; // 20% opacity
						}
						boardContainer.style.backgroundColor = fadedColor;
					}

					if (header) {
						header.style.backgroundColor = b.colour;
						header.style.borderRadius = "10px 10px 0 0";
						header.style.color = "#ffffff";
					}
				}
			});

			document.getElementById("kanban-loading").style.display = "none";
			document.getElementById("myKanban").style.display = "block";
		}

        function createItemHtml(label, isEditable) {
            var actionsHtml = '';
            if (hasPermissionToEdit && isEditable) {
                var dropdownHtml = '<a class="dropdown-item" onclick="confirmDelete(event, this)">Delete</a>';
                if (datalistRowActions && datalistRowActions.length > 0) {
                    datalistRowActions.forEach(function(action) {
                        if (action.id.toLowerCase() !== 'edit' && action.id.toLowerCase() !== 'delete') {
                            dropdownHtml += '<a class="dropdown-item" onclick="triggerAction(event, \'' + action.id + '\', this)">' + action.label + '</a>';
                        }
                    });
                }
                actionsHtml = '<div class="item-actions">' +
                                '<button type="button" class="btn-dots" onclick="toggleDropdown(event, this)">&#8942;</button>' +
                                '<div class="dropdown-menu">' + dropdownHtml + '</div>' +
                              '</div>';
            }

            var cardClass = (hasPermissionToEdit && isEditable) ? "kanban-item-editable" : "kanban-item-readonly";
            var html = "<div class='card-title'>";
            html += "  <span class='card-title-text'>" + label + "</span>";
            html += "</div>";

            return "<div class='" + cardClass + "'>" + html + actionsHtml + "</div>";
        }

		function openCardForm(cardId) {
			var data = { id: cardId };
			var height = "800";
			var width = "900";
			var args = {};
			var isEditable = hasPermissionToEdit;
			popupForm(formId, appId, appVersion, jsonForm, nonce, args, data, height, width, isEditable);
		}

		function popupForm(elementId, appId, appVersion, jsonForm, nonce, args, data, height, width, isEditable) {
			let label = isEditable ? 'Submit' : 'Close';
			let formUrl = '${request.contextPath}/web/app/' + appId + '/' + appVersion + '/form/embed?_submitButtonLabel=' + label;
			let frameId = args.frameId = 'Frame_' + elementId;

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
            let result = JSON.parse(args.result);
            let frameId = args.frameId;

            if (JPopup) {
                  JPopup.hide(frameId);
            }

            if (result && result.id) {
                var updatedId = result.id;
                var updatedLabel = result[labelField] || "(no label)";
                var updatedStatus = result[statusField] || "";

                var rawCanMove = result[canMoveField];
                canMoveMap[updatedId] = (rawCanMove === "false" || rawCanMove === false) ? false : true;

                var itemData = {
                    id: updatedId,
                    title: createItemHtml(updatedLabel)
                };

                kanbanBoard.removeElement(updatedId);
                if (updatedStatus) {
                    kanbanBoard.addElement(updatedStatus, itemData);

                    setTimeout(function() {
                        var el = document.querySelector('.kanban-item[data-eid="' + updatedId + '"]');
                        if (el) {
                            var canMove = canMoveMap[updatedId];
                            if (canMove === false) {
                                el.style.cursor = 'default';
                                el.title = 'Item cannot be moved';
                            } else {
                                el.style.cursor = 'grab';
                                el.removeAttribute('title');
                            }
                        }
                    }, 0);
                }
            }
        }

		function updateItemStatus(el, targetBoardId, sourceBoardId) {
			var itemId = el.getAttribute("data-eid");
			var formData = {};
			formData[statusField] = targetBoardId; 
			var updateUrl = "${request.contextPath}/web/json/data/app/${appId}/form/${formId!''}/" + itemId;

			jQuery.ajax({
				url: updateUrl,
				method: "PUT",
				contentType: "application/json",
				data: JSON.stringify(formData),
				dataType: "json",
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
					var revertBoardId = sourceBoardId === "" ? "null" : sourceBoardId;
					kanbanBoard.addElement(revertBoardId, itemData);
				}
			});
		}

		function triggerAction(event, actionId, btn) {
			if (!hasPermissionToEdit) return;
			event.stopPropagation();
			var menu = btn.closest('.dropdown-menu');
			if (menu) menu.classList.remove('show');
			
			var kanbanItem = btn.closest('.kanban-item');
			var itemId = kanbanItem.getAttribute("data-eid");
			var actionUrl = "${request.contextPath}/web/json/data/app/${appId}/" + appVersion + "/datalist/${dataListId!''}/action/" + actionId + "?id=" + itemId;
			
			if (actionId === 'edit') {
				// Fallback generic edit logic if not overridden
				return;
			}
			
			if (confirm("Are you sure you want to execute this action?")) {
				jQuery.ajax({
					url: actionUrl,
					method: "POST",
					contentType: "application/json",
					dataType: "json",
					success: function(resp, status, xhr) {
						if (resp.url === "REFERER"){
							window.location.href = document.referrer ;
						} else {
							window.location.href = resp.url;
						}
					},
					error: function(xhr) {
						alert("Failed to execute action.");
					}
				});
			}
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

		function confirmDelete(event, btn) {
			if (!hasPermissionToEdit) return;
			event.stopPropagation();
			var menu = btn.closest('.dropdown-menu');
			if (menu) menu.classList.remove('show');

			if (confirm("Are you sure you want to delete this item?")) {
				var kanbanItem = btn.closest('.kanban-item');
				var itemId = kanbanItem.getAttribute("data-eid");
				var deleteUrl = "${request.contextPath}/web/json/data/app/${appId}/form/${formId!''}/" + itemId;

				jQuery.ajax({
					url: deleteUrl,
					method: "DELETE",
					contentType: "application/json",
					dataType: "json",
					success: function(resp) {
						kanbanBoard.removeElement(itemId);
					},
					error: function(xhr, status, error) {
						alert("Failed to delete item. Please try again.");
					}
				});
			}
		}

		var initialRawData = JSON.parse(document.getElementById('kanbanRawData').textContent || '[]');
		buildKanban(initialRawData);

	</script>
</body>
</html>
