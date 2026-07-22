package com.kinnara.kecakplugins.jkanban.userview;

import com.kinnara.kecakplugins.jkanban.datalist.KanbanWorkflowDataListBinder;
import com.kinnara.kecakplugins.jkanban.model.KanbanBoard;
import com.kinnara.kecakplugins.jkanban.model.KanbanCard;
import com.kinnara.kecakplugins.jkanban.kanban.graph.*;
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
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.context.ApplicationContext;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.sql.DataSource;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;
import java.util.stream.Collectors;

public class KanbanInboxMenu extends UserviewMenu {
    private static final String LABEL = "Kanban Inbox Menu";
    private static final Map<String, ProcessGraph> GRAPH_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

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

        String targetProcessId = getProcessDefId();
        ProcessGraph graph;
        if (processDefId != null) {
            graph = GRAPH_CACHE.computeIfAbsent(processDefId, id -> {
                String xpdlXmlContent = fetchXpdlContent(id);
                return parseXpdlToGraph(xpdlXmlContent, targetProcessId);
            });
        } else {
            graph = new ProcessGraph();
        }
        List<ColumnResult> columns = graph.computeColumnsBfs();

        List<KanbanBoard> boards = new ArrayList<>();
        Map<String, KanbanBoard> boardLookup = new LinkedHashMap<>();
        for (ColumnResult col : columns) {
            KanbanBoard board = new KanbanBoard(col.getActivityId(), col.getActivityName(), "#F707F7");
            boards.add(board);
            boardLookup.put(col.getActivityId(), board);
        }

        Map<String, User> userCache = new HashMap<>();
        for (Map<String, Object> row : validRows) {
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
            String activityDefId = "";
            boolean canDrag = false;

            if (processDefId != null) {
                WorkflowAssignment assignment = workflowManager.getAssignmentByRecordId(recordId, processDefId, null, null);

                if (assignment != null) {
                    activityId = assignment.getActivityId();
                    activityName = assignment.getActivityName();
                    activityDefId = assignment.getActivityDefId();
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
                } else {
                    String statusValue = row.get(getStatusField()) != null ? row.get(getStatusField()).toString() : null;
                    String matchedBoardId = graph.findEndNodeBoardByStatus(statusValue);
                    if (matchedBoardId != null) {
                        activityDefId = matchedBoardId;
                        ProcessNode matchedNode = graph.getNodes().get(matchedBoardId);
                        activityName = matchedNode != null ? matchedNode.getName() : matchedBoardId;
                    }
                }
            }

            boolean canEdit = Objects.equals(currentAssigneeUserName, currentUser.getUsername());

            KanbanCard card = new KanbanCard(
                    recordId, title, activityDefId, displayRequesterName,
                    displayAssigneeName, activityId, activityName, canDrag, canEdit
            );

            KanbanBoard targetBoard = boardLookup.get(activityDefId);
            if (targetBoard != null) {
                targetBoard.addCard(card);
            }
        }

        String graphDebug = graph.debugPrint();
        LogUtil.info(getClassName(), "graph: " + graphDebug);
        JSONObject dragTargetsJson = graph.getAllDragTargetsJson();
        String dragTargetsDebug = null;
        try {
            dragTargetsDebug = dragTargetsJson.toString(2);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        LogUtil.info(getClassName(), "dragTargets: " + dragTargetsDebug);


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
        dataModel.put("xpdlDebug", graphDebug + "\n\n=== DRAG TARGETS ===\n" + dragTargetsDebug);
        dataModel.put("dragTargets", dragTargetsJson.toString());

        return pluginManager.getPluginFreeMarkerTemplate(dataModel, getClassName(), "/templates/KanbanInboxMenu.ftl", null);
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
        return AppUtil.readPluginResource(getClassName(), "/properties/userview/KanbanInboxMenu.json", args, true, "");
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

    private String fetchXpdlContent(String processDefId) {
        String packageId = processDefId;
        String packageVersion = "1";
        if (processDefId != null && processDefId.contains("#")) {
            String[] parts = processDefId.split("#");
            packageId = parts[0];
            packageVersion = parts.length > 1 ? parts[1] : "1";
        }

        String sql = "SELECT d.XPDLContent "
                + "FROM SHKProcessDefinitions pd "
                + "JOIN SHKXPDLS x ON x.XPDLId = pd.PackageId AND x.XPDLVersion = pd.ProcessDefinitionVersion "
                + "JOIN SHKXPDLData d ON d.XPDL = x.oid "
                + "WHERE pd.PackageId = ? AND pd.ProcessDefinitionVersion = ?";

        DataSource ds = (DataSource) AppUtil.getApplicationContext().getBean("setupDataSource");

        try (Connection con = ds.getConnection();
             PreparedStatement stmt = con.prepareStatement(sql)) {

            stmt.setString(1, packageId);
            stmt.setString(2, packageVersion);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    byte[] content = rs.getBytes("XPDLContent");
                    return content != null ? new String(content, java.nio.charset.StandardCharsets.UTF_8) : null;
                }
            }
        } catch (Exception e) {
            LogUtil.error(getClassName(), e, "fetchXpdlContent failed");
        }
        return null;
    }

    private ProcessGraph parseXpdlToGraph(String xpdlXml, String targetProcessId) {
        ProcessGraph graph = new ProcessGraph();
        try {
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            dbFactory.setNamespaceAware(true);
            dbFactory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            DocumentBuilder builder = dbFactory.newDocumentBuilder();
            Document doc = builder.parse(new InputSource(new StringReader(xpdlXml)));

            NodeList processNodes = doc.getElementsByTagNameNS("*", "WorkflowProcess");
            org.w3c.dom.Element targetProcessEl = null;
            for (int i = 0; i < processNodes.getLength(); i++) {
                org.w3c.dom.Element el = (org.w3c.dom.Element) processNodes.item(i);
                if (targetProcessId.equals(el.getAttribute("Id"))) {
                    targetProcessEl = el;
                    break;
                }
            }

            if (targetProcessEl == null) {
                LogUtil.warn(getClassName(), "WorkflowProcess with Id=" + targetProcessId + " not found in XPDL");
                return graph;
            }

            // Parse activities
            NodeList activityNodes = targetProcessEl.getElementsByTagNameNS("*", "Activity");
            for (int i = 0; i < activityNodes.getLength(); i++) {
                org.w3c.dom.Element actEl = (org.w3c.dom.Element) activityNodes.item(i);
                graph.addNode(actEl.getAttribute("Id"), actEl.getAttribute("Name"), detectActivityType(actEl));
            }

            // Parse transitions + condition
            NodeList transitionNodes = targetProcessEl.getElementsByTagNameNS("*", "Transition");
            for (int i = 0; i < transitionNodes.getLength(); i++) {
                org.w3c.dom.Element transEl = (org.w3c.dom.Element) transitionNodes.item(i);
                String transId = transEl.getAttribute("Id");
                String from = transEl.getAttribute("From");
                String to = transEl.getAttribute("To");

                ProcessTransition transition = new ProcessTransition(transId, from, to);

                NodeList conditionNodes = transEl.getElementsByTagNameNS(XPDL_NS, "Condition");
                if (conditionNodes.getLength() > 0) {
                    org.w3c.dom.Element condEl = (org.w3c.dom.Element) conditionNodes.item(0);
                    transition.setConditionType(condEl.getAttribute("Type"));
                }

                NodeList extAttrNodes = transEl.getElementsByTagNameNS(XPDL_NS, "ExtendedAttribute");
                for (int j = 0; j < extAttrNodes.getLength(); j++) {
                    org.w3c.dom.Element extAttrEl = (org.w3c.dom.Element) extAttrNodes.item(j);
                    if ("PBUILDER_TRANSITION_CONDITIONS".equals(extAttrEl.getAttribute("Name"))) {
                        parseConditionsJson(extAttrEl.getAttribute("Value"), transition);
                    }
                }
                graph.addTransitionDetail(transition);
            }

        } catch (Exception e) {
            LogUtil.error(getClassName(), e, "Failed to parse XPDL to graph");
        }
        return graph;
    }

    private static final String XPDL_NS = "http://www.wfmc.org/2002/XPDL1.0";

    private ActivityType detectActivityType(org.w3c.dom.Element activityEl) {
        NodeList routeNodes = activityEl.getElementsByTagNameNS(XPDL_NS, "Route");
        if (routeNodes.getLength() > 0) {
            return ActivityType.ROUTE;
        }

        NodeList implNodes = activityEl.getElementsByTagNameNS(XPDL_NS, "Implementation");
        if (implNodes.getLength() > 0) {
            org.w3c.dom.Element implEl = (org.w3c.dom.Element) implNodes.item(0);

            NodeList toolNodes = implEl.getElementsByTagNameNS(XPDL_NS, "Tool");
            if (toolNodes.getLength() > 0) {
                return ActivityType.TOOL;
            }

            NodeList noNodes = implEl.getElementsByTagNameNS(XPDL_NS, "No");
            if (noNodes.getLength() > 0) {
                return ActivityType.WORK_ACTIVITY;
            }
        }
        return ActivityType.ROUTE; // default aman: anggap bukan work activity/tool
    }

    private void parseConditionsJson(String rawJson, ProcessTransition transition) {
        if (rawJson == null || rawJson.isEmpty()) {
            return;
        }
        try {
            JSONArray arr = new JSONArray(rawJson);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                String variable = obj.optString("variable", null);
                String operator = obj.optString("operator", null);
                String value = obj.optString("value", null);
                String join = obj.optString("join", null);

                ProcessTransition.TransitionCondition condition =
                        new ProcessTransition.TransitionCondition(variable, operator, value, join);
                transition.getConditions().add(condition);
            }
        } catch (Exception e) {
            LogUtil.error(getClassName(), e, "Failed to parse PBUILDER_TRANSITION_CONDITIONS: " + rawJson);
        }
    }
}
