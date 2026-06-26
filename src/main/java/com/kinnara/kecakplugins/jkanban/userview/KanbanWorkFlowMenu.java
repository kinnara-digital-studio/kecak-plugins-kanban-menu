package com.kinnara.kecakplugins.jkanban.userview;

import com.kinnara.kecakplugins.jkanban.kanban.KanbanBoard;
import com.kinnara.kecakplugins.jkanban.kanban.KanbanCard;
import com.kinnarastudio.commons.Try;
import org.apache.commons.lang3.StringEscapeUtils;
import org.joget.apps.app.dao.DatalistDefinitionDao;
import org.joget.apps.app.dao.FormDefinitionDao;
import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.model.DatalistDefinition;
import org.joget.apps.app.model.FormDefinition;
import org.joget.apps.app.model.PackageActivityForm;
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
import org.joget.workflow.model.service.WorkflowManager;
import org.joget.workflow.model.service.WorkflowUserManager;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.context.ApplicationContext;

import java.util.*;
import java.util.stream.Collectors;

public class KanbanWorkFlowMenu extends UserviewMenu {

    private static final String LABEL = "Kanban WorkFlow Menu";

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

        //getCurrentUser
        WorkflowUserManager workflowUserManager = (WorkflowUserManager) applicationContext.getBean("workflowUserManager");
        User currentUser = workflowUserManager.getCurrentUser();

        //for template
        PluginManager pluginManager = (PluginManager) applicationContext.getBean("pluginManager");

        //Properties
        String datalistId = getDatalistId();
        Map<String, String>[] options = getPropertyGrid("options");

        // Get Datalist
        DataList dataList = getDataList(datalistId);
        DataListCollection<Map<String, Object>> rows = dataList.getRows();
        List<Map<String, Object>> validRows = rows.stream()
                .filter(row -> row.get("id") != null)
                .filter(row -> !row.get("id").toString().isEmpty())
                .collect(Collectors.toList());

        List<KanbanCard> kanbanCards = new ArrayList<>();
        for (Map<String, Object> row : validRows) {
            kanbanCards.add(buildKanbanCard(row, appId, appVersion, appDefinition, currentUser));
        }

        //Make a Kanban Board
        List<KanbanBoard> boards = new ArrayList<>();
        if (options != null) {
            for (Map<String, String> option : options) {
                boards.add(new KanbanBoard(
                        option.get("value"),
                        option.get("label"),
                        option.get("colour")
                ));
            }
        }
        kanbanCards.forEach(kanban -> {
            boards.stream()
                    .filter(board -> board.getValue().equals(kanban.getStatus()))
                    .findFirst()
                    .ifPresent(board -> board.addCard(kanban));
        });

        Map<String, Object> dataModel = new HashMap<>();
        dataModel.put("boards", boardsJson(boards).toString());
        dataModel.put("className", getClassName());
        dataModel.put("appId", appId);
        dataModel.put("appVersion", appVersion);
        dataModel.put("editable", true);
        dataModel.put("statusField", getStatusField());

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

    private KanbanCard buildKanbanCard(Map<String, Object> row, String appId, String appVersion, AppDefinition appDefinition, User currentUser) {

        ApplicationContext appContext = AppUtil.getApplicationContext();
        AppService appService = (AppService) appContext.getBean("appService");

        WorkflowManager workflowManager = (WorkflowManager) appContext.getBean("workflowManager");
        PluginManager pluginManager = (PluginManager) appContext.getBean("pluginManager");
        DirectoryManager directoryManager = (DirectoryManager) appContext.getBean("directoryManager");

        String recordId = row.get("id").toString();
        String title = row.get(getTitleField()) != null ? row.get(getTitleField()).toString() : "";
        String status = row.get(getStatusField()) != null ? row.get(getStatusField()).toString() : "";
        String requesterName = row.get("createdBy") != null ? row.get("createdBy").toString() : "";

        User requesterUser = directoryManager.getUserByUsername(requesterName);
        String displayRequesterName = requesterUser != null
                ? requesterUser.getFirstName() + " " + requesterUser.getLastName()
                : requesterName;

        WorkflowAssignment assignment = workflowManager.getAssignmentByRecordId(recordId, getProcessDefId(), null, null);

        String activityId = "";
        String activityName = ResourceBundleUtil.getMessage("jkanban.noActivityYet");
        String currentAssigneeUserName = "";
        //String displayAssigneeName = "No Assignee";
        String displayAssigneeName = ResourceBundleUtil.getMessage("jkanban.noAssigneeYet");
        String formDefId = "";

        if (assignment != null) {
//            String activityDefId = assignment.getActivityDefId();
            activityId = assignment.getActivityId();
            activityName = assignment.getActivityName();
            currentAssigneeUserName = assignment.getAssigneeName();

            //LogUtil.info(getClassName(), "activityId: " + activityId);

//            PackageActivityForm packageActivityForm = appService.retrieveMappedForm(appId, appVersion, getProcessDefId(), activityDefId);
//            if (packageActivityForm != null) {
//                formDefId = packageActivityForm.getFormId();
//            }

            User assigneeUser = directoryManager.getUserByUsername(currentAssigneeUserName);
            if (assigneeUser != null) {
                displayAssigneeName = assigneeUser.getFirstName() + " " + assigneeUser.getLastName();
            }
        }

        //LogUtil.info(getClassName(), "formDefId: " + formDefId);

        boolean canEdit = Objects.equals(currentAssigneeUserName, currentUser.getUsername());

        if (formDefId.isEmpty()) {
            formDefId = getPropertyString("formDefId");
        }

        JSONObject form = new JSONObject();
        String nonce = "";
        if (formDefId != null && !formDefId.isEmpty()) {
            form = getJsonForm(formDefId, !canEdit);
            nonce = generateNonce(appDefinition, form.toString());
        }

        boolean canDrag = assignment != null
                && Objects.equals(currentAssigneeUserName, currentUser.getUsername());

        return new KanbanCard(
                recordId,
                title,
                status,
                displayRequesterName,
                displayAssigneeName,
                activityId,
                activityName,
                StringEscapeUtils.escapeHtml4(form.toString()),
                nonce,
                canDrag,
                canEdit
        );
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

    protected DataList getDataList(String dataListId) {
        ApplicationContext applicationContext = AppUtil.getApplicationContext();
        DatalistDefinitionDao datalistDefinitionDao = (DatalistDefinitionDao) applicationContext
                .getBean("datalistDefinitionDao");
        DataListService dataListService = (DataListService) applicationContext.getBean("dataListService");
        AppDefinition appDefinition = AppUtil.getCurrentAppDefinition();
        DatalistDefinition datalistDefinition = datalistDefinitionDao.loadById(dataListId, appDefinition);
        if (datalistDefinition == null) {
            LogUtil.warn(getClassName(), "DataList Definition [" + dataListId + "] not found");
            return null;
        }

        DataList dataList = dataListService.fromJson(datalistDefinition.getJson());
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
                        cardObj.put("isEditable", card.isEditable());
                        cardObj.put("form", card.getForm() != null ? card.getForm() : "");
                        cardObj.put("nonce", card.getNonce());
                        cardObj.put("canDrag", card.isCanDrag());
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

    private boolean isRequesterCanEdit(String status) {
        Map<String, String>[] options = getPropertyGrid("options");

        for (Map<String, String> option : options) {
            if (status.equalsIgnoreCase(option.get("value"))) {
                return "true".equalsIgnoreCase(option.get("canEdit"));
            }
        }
        return false;
    }

}
