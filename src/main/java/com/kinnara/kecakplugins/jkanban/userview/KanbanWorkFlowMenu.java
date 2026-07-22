package com.kinnara.kecakplugins.jkanban.userview;

import com.kinnara.kecakplugins.jkanban.datalist.KanbanWorkflowDataListBinder;
import com.kinnara.kecakplugins.jkanban.model.KanbanBoard;
import com.kinnara.kecakplugins.jkanban.model.KanbanCard;
import com.kinnarastudio.commons.Try;
import org.apache.commons.lang3.StringEscapeUtils;
import org.joget.apps.app.dao.DatalistDefinitionDao;
import org.joget.apps.app.dao.FormDefinitionDao;
import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.model.DatalistDefinition;
import org.joget.apps.app.model.FormDefinition;
import org.joget.apps.app.service.AppService;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.datalist.model.DataList;
import org.joget.apps.datalist.model.DataListCollection;
import org.joget.apps.datalist.service.DataListService;
import org.joget.apps.form.model.Element;
import org.joget.apps.form.model.FormData;
import org.joget.apps.form.service.FormService;
import org.joget.apps.form.service.FormUtil;
import org.joget.apps.userview.lib.InboxMenu;
import org.joget.apps.userview.model.UserviewMenu;
import org.joget.commons.util.LogUtil;
import org.joget.commons.util.ResourceBundleUtil;
import org.joget.commons.util.SecurityUtil;
import org.joget.directory.model.User;
import org.joget.directory.model.service.DirectoryManager;
import org.joget.plugin.base.PluginManager;
import org.joget.workflow.model.WorkflowAssignment;
import org.joget.workflow.model.WorkflowProcess;
import org.joget.workflow.model.service.WorkflowManager;
import org.joget.workflow.model.service.WorkflowUserManager;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.context.ApplicationContext;

import java.util.*;
import java.util.stream.Collectors;

public class KanbanWorkFlowMenu extends UserviewMenu {

    private static final String LABEL = "Kanban Workflow Menu";

    @Override
    public String getCategory() {
        return "Kecak";
    }

    @Override
    public String getIcon() {
        return "<i class=\"fas fa-table\"></i>";
    }

    @Override
    public String getRenderPage() {
        ApplicationContext applicationContext = AppUtil.getApplicationContext();
        AppDefinition appDefinition = AppUtil.getCurrentAppDefinition();
        String appId = appDefinition.getAppId();
        String appVersion = appDefinition.getVersion().toString();

        WorkflowUserManager workflowUserManager = (WorkflowUserManager) applicationContext.getBean("workflowUserManager");
        User currentUser = workflowUserManager.getCurrentUser();
        PluginManager pluginManager = (PluginManager) applicationContext.getBean("pluginManager");
        DirectoryManager directoryManager = (DirectoryManager) applicationContext.getBean("directoryManager");
        AppService appService = (AppService) applicationContext.getBean("appService");
        WorkflowManager workflowManager = (WorkflowManager) applicationContext.getBean("workflowManager");
        WorkflowProcess process = appService.getWorkflowProcessForApp(appId, appVersion, getProcessDefId());

        String processDefId = process != null ? process.getId() : null;
        String datalistId = getDatalistId();

        DataList dataList = getDataList(datalistId);
        String primaryKeyColumn = dataList.getBinder().getPrimaryKeyColumnName();
        DataListCollection<Map<String, Object>> rows = dataList.getRows();
        List<Map<String, Object>> validRows = rows.stream()
                .filter(row -> row.get(primaryKeyColumn) != null)
                .filter(row -> !row.get(primaryKeyColumn).toString().isEmpty())
                .collect(Collectors.toList());

        String globalFormDefId = getPropertyString("formDefId");

        String formEditableStr = "";
        String nonceEditable = "";
        String formReadOnlyStr = "";
        String nonceReadOnly = "";

        if (globalFormDefId != null && !globalFormDefId.isEmpty()) {
            JSONObject formEditable = getJsonForm(globalFormDefId, false);
            nonceEditable = generateNonce(appDefinition, formEditable.toString());
            formEditableStr = StringEscapeUtils.escapeHtml4(formEditable.toString());

            JSONObject formReadOnly = getJsonForm(globalFormDefId, true);
            nonceReadOnly = generateNonce(appDefinition, formReadOnly.toString());
            formReadOnlyStr = StringEscapeUtils.escapeHtml4(formReadOnly.toString());
        }

        List<KanbanBoard> boards = new ArrayList<>();
        Map<String, KanbanBoard> boardLookup = new LinkedHashMap<>();
        Map<String, String>[] options = getPropertyGrid("options");
        if (options.length >= 1) {
            for (Map<String, String> option : options) {
                String boardId = option.get("value");
                KanbanBoard board = new KanbanBoard(
                        boardId,
                        option.get("label"),
                        option.get("colour")
                );
                boards.add(board);
                boardLookup.put(boardId, board);
            }
        }
        Map<String, User> userCache = new HashMap<>();
        for (Map<String, Object> row : validRows) {
            String status = row.get(getStatusField()) != null ? row.get(getStatusField()).toString() : "";

            KanbanBoard targetBoard = boardLookup.get(status);
            if (targetBoard == null) {
                continue;
            }

            String recordId = row.get("id").toString();
            String title = row.get(getTitleField()) != null ? row.get(getTitleField()).toString() : "";
            String requesterName = row.get("createdBy") != null ? row.get("createdBy").toString() : "";

            User requesterUser;
            if (userCache.containsKey(requesterName)) {
                requesterUser = userCache.get(requesterName);
            } else {
                requesterUser = directoryManager.getUserByUsername(requesterName);
                userCache.put(requesterName, requesterUser);
            }
            String displayRequesterName = requesterUser != null
                    ? requesterUser.getFirstName() + " " + requesterUser.getLastName()
                    : requesterName;

            String activityId = "";
            String activityName = ResourceBundleUtil.getMessage("jkanban.noActivityYet");
            String currentAssigneeUserName = "";
            String displayAssigneeName = ResourceBundleUtil.getMessage("jkanban.noAssigneeYet");
            boolean canDrag = false;

            if (processDefId != null) {
                WorkflowAssignment assignment = workflowManager.getAssignmentByRecordId(recordId, processDefId, null, null);

                if (assignment != null) {
                    activityId = assignment.getActivityId();
                    activityName = assignment.getActivityName();
                    currentAssigneeUserName = assignment.getAssigneeName();

                    User assigneeUser;
                    if (userCache.containsKey(currentAssigneeUserName)) {
                        assigneeUser = userCache.get(currentAssigneeUserName);
                    } else {
                        assigneeUser = directoryManager.getUserByUsername(currentAssigneeUserName);
                        userCache.put(currentAssigneeUserName, assigneeUser);
                    }
                    if (assigneeUser != null) {
                        displayAssigneeName = assigneeUser.getFirstName() + " " + assigneeUser.getLastName();
                    }
                    canDrag = Objects.equals(currentAssigneeUserName, currentUser.getUsername());
                }
            }

            boolean canEdit = Objects.equals(currentAssigneeUserName, currentUser.getUsername());

            KanbanCard card = new KanbanCard(
                    recordId, title, status, displayRequesterName,
                    displayAssigneeName, activityId, activityName, canDrag, canEdit
            );
            targetBoard.addCard(card);
        }

        Map<String, Object> dataModel = new HashMap<>();
        dataModel.put("boards", boardsJson(boards).toString());
        dataModel.put("className", getClassName());
        dataModel.put("appId", appId);
        dataModel.put("appVersion", appVersion);
        dataModel.put("editable", true);
        dataModel.put("statusField", getStatusField());
        dataModel.put("formEditable", formEditableStr);
        dataModel.put("nonceEditable", nonceEditable);
        dataModel.put("formReadOnly", formReadOnlyStr);
        dataModel.put("nonceReadOnly", nonceReadOnly);

        return pluginManager.getPluginFreeMarkerTemplate(dataModel, getClassName(), "/templates/KanbanWorkFlowMenu.ftl", null);
    }

    @Override
    public boolean isHomePageSupported() {
        return false;
    }

    @Override
    public String getDecoratedMenu() {
        return null;
    }

    @Override
    public String getName() {
        return LABEL;
    }

    @Override
    public String getVersion() {
        PluginManager pluginManager = (PluginManager) AppUtil.getApplicationContext().getBean("pluginManager");
        ResourceBundle resourceBundle = pluginManager.getPluginMessageBundle(getClassName(), "/messages/BuildNumber");
        String buildNumber = resourceBundle.getString("buildNumber");
        return buildNumber;
    }

    @Override
    public String getDescription() {
        return getClass().getPackage().getImplementationTitle();
    }

    @Override
    public String getLabel() {
        return LABEL;
    }

    @Override
    public String getClassName() {
        return getClass().getName();
    }

    @Override
    public String getPropertyOptions() {
        Object[] args = new Object[]{
                InboxMenu.class.getName()
        };
        return AppUtil.readPluginResource(getClassName(), "/properties/userview/KanbanWorkFlowMenu.json", args, true, "");
    }

    private String getStatusField() {
        return getPropertyString("statusField");
    }
    private String getTitleField() {
        return getPropertyString("titleField");
    }
    private String getProcessDefId() {
        return getPropertyString("processDefId");
    }
    private String getDatalistId() {
        return getPropertyString("dataListId");
    }
    protected String getFormDefId() {
        return getPropertyString("formDefId");
    }

    protected DataList getDataList(String dataListId) {
        ApplicationContext applicationContext = AppUtil.getApplicationContext();
        DatalistDefinitionDao datalistDefinitionDao = (DatalistDefinitionDao) applicationContext
                .getBean("datalistDefinitionDao");
        DataListService dataListService = (DataListService) applicationContext.getBean("dataListService");
        AppDefinition appDefinition = AppUtil.getCurrentAppDefinition();

        String jsonDataList;
        if(dataListId.isEmpty()) {
            String processDefId = isRunningProcessOnly() ? getProcessDefId() : "";
            Object[] args =  new Object[]{
                    KanbanWorkflowDataListBinder.class.getName(),
                    getFormDefId(),
                    processDefId,
                    getTitleField(),
                    getStatusField()
            };
            jsonDataList = AppUtil.readPluginResource(getClassName(), "/definitions/datalist/KanbanWorkflowDataList.json", args, true, "");
        } else {
            DatalistDefinition datalistDefinition = datalistDefinitionDao.loadById(dataListId, appDefinition);
            if (datalistDefinition == null) {
                LogUtil.warn(getClassName(), "DataList Definition [" + dataListId + "] not found");
                return null;
            }

            jsonDataList = datalistDefinition.getJson();
        }

        DataList dataList = dataListService.fromJson(jsonDataList);
        if (dataList == null) {
            LogUtil.warn(getClassName(), "DataList [" + dataListId + "] not found");
            return null;
        }

        dataList.setPageSize(DataList.MAXIMUM_PAGE_SIZE);
        return dataList;
    }

    protected JSONObject getJsonForm(String formDefId, boolean readonly) {
        ApplicationContext appContext = AppUtil.getApplicationContext();
        FormService formService = (FormService) appContext.getBean("formService");
        FormDefinitionDao formDefinitionDao = (FormDefinitionDao) appContext.getBean("formDefinitionDao");
        AppDefinition appDef = AppUtil.getCurrentAppDefinition();

        return Optional.of(formDefId)
                .map(s -> formDefinitionDao.loadById(s, appDef))
                .map(FormDefinition::getJson)
                .map(formService::createElementFromJson)
                .map(Try.toPeek(form -> {
                    FormUtil.setReadOnlyProperty(form, readonly, readonly);
                    Element statusField = FormUtil.findElement(getStatusField(), form, new FormData());
                    if (statusField != null) {
                        FormUtil.setReadOnlyProperty(statusField);
                    }
                })) // object form
                .map(formService::generateElementJson)
                .map(Try.onFunction(JSONObject::new))
                .orElseGet(JSONObject::new);
    }

    private JSONArray boardsJson(List<KanbanBoard> boards) {
        JSONArray boardsArray = new JSONArray();

        for (KanbanBoard board : boards) {
            JSONObject boardObj = new JSONObject();
            try {
                boardObj.put("value", board.getValue());
                boardObj.put("label", board.getLabel());
                boardObj.put("colour", board.getColour());

                JSONArray cardsArray = new JSONArray();
                for (KanbanCard card : board.getCards()) {
                    JSONObject cardObj = new JSONObject();
                    try {
                        cardObj.put("id", card.getRecordId());
                        cardObj.put("title", card.getTitle());
                        cardObj.put("status", card.getStatus());
                        cardObj.put("requesterName", card.getRequesterName());
                        cardObj.put("currentAssigneeName", card.getCurrentAssigneeName());
                        cardObj.put("activityId", card.getActivityId());
                        cardObj.put("activityName", card.getActivityName());
                        cardObj.put("canDrag", card.isCanDrag());
                        cardObj.put("isEditable", card.isEditable());
                    } catch (Exception e) {
                        LogUtil.error(getClassName(), e, "Error building card JSON");
                    }
                    cardsArray.put(cardObj);
                }

                boardObj.put("cards", cardsArray);
            } catch (Exception e) {
                LogUtil.error(getClassName(), e, "Error building board JSON");
            }
            boardsArray.put(boardObj);
        }

        return boardsArray;
    }

    protected String generateNonce(AppDefinition appDefinition, String jsonForm) {
        return SecurityUtil.generateNonce(
                new String[]{"EmbedForm", appDefinition.getAppId(), appDefinition.getVersion().toString(), jsonForm},
                1);
    }

    protected boolean isRunningProcessOnly() {
        return "true".equalsIgnoreCase(getPropertyString("isRunningProcessOnly"));
    }
}
