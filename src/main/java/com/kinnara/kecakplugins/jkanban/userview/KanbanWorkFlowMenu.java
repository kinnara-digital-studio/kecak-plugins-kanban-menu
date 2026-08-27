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
import org.joget.apps.app.model.PackageActivityForm;
import org.joget.apps.app.service.AppService;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.datalist.model.DataList;
import org.joget.apps.datalist.model.DataListCollection;
import org.joget.apps.datalist.service.DataListService;
import org.joget.apps.form.model.Element;
import org.joget.apps.form.model.Form;
import org.joget.apps.form.model.FormData;
import org.joget.apps.form.model.FormRowSet;
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
import org.joget.plugin.base.PluginWebSupport;
import org.joget.workflow.model.WorkflowActivity;
import org.joget.workflow.model.WorkflowAssignment;
import org.joget.workflow.model.WorkflowProcess;
import org.joget.workflow.model.service.WorkflowManager;
import org.joget.workflow.model.service.WorkflowUserManager;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.context.ApplicationContext;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class KanbanWorkFlowMenu extends UserviewMenu implements PluginWebSupport {

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
            String displayAssigneeName = ResourceBundleUtil.getMessage("jkanban.noAssigneeYet");
            boolean canDrag = false;
            boolean canEdit = true;

            if (processDefId != null) {
                WorkflowAssignment assignment = workflowManager.getAssignmentByRecordId(recordId, processDefId, null, null);
                if (assignment != null) {
                    activityId = assignment.getActivityId();
                    activityName = assignment.getActivityName();

                    WorkflowActivity runningActivity = workflowManager.getRunningActivityInfo(activityId);
                    if (runningActivity != null) {
                        String[] assignees = runningActivity.getAssignmentUsers();
                        if (assignees != null && assignees.length > 0) {
                            List<String> displayNames = new ArrayList<>();
                            for (String username : assignees) {
                                if (username.equals(currentUser.getUsername())) {
                                    canDrag = true;
                                    canEdit = true;
                                }
                                User assigneeUser;
                                if (userCache.containsKey(username)) {
                                    assigneeUser = userCache.get(username);
                                } else {
                                    assigneeUser = directoryManager.getUserByUsername(username);
                                    userCache.put(username, assigneeUser);
                                }
                                if (assigneeUser != null) {
                                    displayNames.add(assigneeUser.getFirstName() + " " + assigneeUser.getLastName());
                                } else {
                                    displayNames.add(username);
                                }
                            }
                            displayAssigneeName = String.join(", ", displayNames);
                        }
                    }
                }
            }

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

    @Override
    public void webService(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String action = request.getParameter("action");
        if ("moveCard".equals(action)) {
            handleMoveCard(request, response);
        } else {
            response.setStatus(404);
        }
    }

    private void handleMoveCard(HttpServletRequest request, HttpServletResponse response) throws IOException {
        JSONObject result = new JSONObject();
        response.setContentType("application/json");

        try {
            String activityId   = request.getParameter("activityId");
            String targetStatus = request.getParameter("status");
            String statusField  = request.getParameter("statusField");

            ApplicationContext appContext = AppUtil.getApplicationContext();
            WorkflowManager workflowManager = (WorkflowManager) appContext.getBean("workflowManager");
            AppService appService = (AppService) appContext.getBean("appService");
            WorkflowUserManager workflowUserManager = (WorkflowUserManager) appContext.getBean("workflowUserManager");
            User currentUser = workflowUserManager.getCurrentUser();

            // ── 1. Ambil assignment & validasi ──────────────────
            WorkflowAssignment assignment = workflowManager.getAssignment(activityId);
            if (assignment == null) {
                result.put("error", "Assignment not found or already completed");
                response.setStatus(400);
                result.write(response.getWriter());
                return;
            }

            WorkflowActivity runningActivity = workflowManager.getRunningActivityInfo(activityId);
            boolean isAssignee = runningActivity != null &&
                    Arrays.asList(runningActivity.getAssignmentUsers()).contains(currentUser.getUsername());
            if (!isAssignee) {
                result.put("error", "You are not the assignee of this activity");
                response.setStatus(403);
                result.write(response.getWriter());
                return;
            }

            // ── 2. Siapkan AppDefinition & FormData ──────────────
            AppDefinition appDefinition = appService.getAppDefinitionForWorkflowActivity(activityId);
            AppUtil.setCurrentAppDefinition(appDefinition);

            FormData formData = new FormData();
            formData.setActivityId(assignment.getActivityId());
            formData.setProcessId(assignment.getProcessId());

            WorkflowProcess process = workflowManager.getProcess(assignment.getProcessId());
            if (process != null) {
                formData.setPrimaryKeyValue(process.getRecordId());
            }

            // ── 3. Ambil form assignment (otomatis load nilai lama via loadBinderData) ──
            PackageActivityForm packageActivityForm = appService.viewAssignmentForm(appDefinition, assignment, formData, "");
            if (packageActivityForm == null) {
                result.put("error", "Assignment has not been mapped to a form");
                response.setStatus(400);
                result.write(response.getWriter());
                return;
            }
            Form form = packageActivityForm.getForm();

            // ── 4. Override HANYA field status ────────────────────────────
            LogUtil.info(getClassName(), "targetStatus from request: [" + targetStatus + "]");
            LogUtil.info(getClassName(), "statusField from request: [" + statusField + "]");
            
            Element statusElement = FormUtil.findElement(statusField, form, formData);
            LogUtil.info(getClassName(), "statusElement found: " + (statusElement != null));
            
            if (statusElement != null) {
                String parameterName = FormUtil.getElementParameterName(statusElement);
                LogUtil.info(getClassName(), "parameterName: [" + parameterName + "]");
                formData.addRequestParameterValues(parameterName, new String[]{targetStatus});
                
                // Cek ulang apakah value sudah masuk
                String[] checkValues = formData.getRequestParameterValues(parameterName);
                LogUtil.info(getClassName(), "value in formData after set: " + Arrays.toString(checkValues));
            }

            // ── 5. Complete assignment ─────────────────────────────
            FormData resultFormData = appService.completeAssignmentForm(form, assignment, formData, new HashMap<>());

            // ── 6. Cek validation error ─────────────────────────────
            Map<String, String> errors = resultFormData.getFormErrors();
            if (errors != null && !errors.isEmpty()) {
                result.put("validation_error", new JSONObject(errors));
                result.put("message", "Validation Error");
                response.setStatus(200);
                result.write(response.getWriter());
                return;
            }

            result.put("status", "success");
            response.setStatus(200);
            result.write(response.getWriter());

        } catch (Exception e) {
            LogUtil.error(getClassName(), e, "Error moving card");
            try { result.put("error", e.getMessage()); } catch (Exception ignored) {}
            response.setStatus(500);
        }
    }
}
