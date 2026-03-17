package com.kinnara.kecakplugins.jkanban.userview;

import com.kinnarastudio.commons.Try;
import org.apache.commons.lang3.StringEscapeUtils;
import org.joget.apps.app.dao.DatalistDefinitionDao;
import org.joget.apps.app.dao.FormDefinitionDao;
import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.model.DatalistDefinition;
import org.joget.apps.app.model.FormDefinition;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.datalist.model.DataList;
import org.joget.apps.datalist.model.DataListAction;
import org.joget.apps.datalist.service.DataListService;
import org.joget.apps.form.service.FormService;
import org.joget.apps.form.service.FormUtil;
import org.joget.apps.userview.model.UserviewMenu;
import org.joget.apps.userview.model.UserviewPermission;
import org.joget.commons.util.LogUtil;
import org.joget.commons.util.SecurityUtil;
import org.joget.directory.model.User;
import org.joget.plugin.base.PluginManager;
import org.joget.workflow.model.service.WorkflowUserManager;
import org.json.JSONObject;
import org.springframework.context.ApplicationContext;

import java.util.*;

public class KanbanUserViewMenu extends UserviewMenu {

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
        PluginManager pluginManager = (PluginManager) applicationContext.getBean("pluginManager");

        Map<String, Object> dataModel = new HashMap<>();
        dataModel.put("className", getClassName());

        String label = getPropertyString("labelField");
        String status = getPropertyString("statusField");
        Map<String, String>[] boards = getPropertyGrid("options");
        dataModel.put("label", label);
        dataModel.put("status", status);
        dataModel.put("boards", boards);

        final String dataListId = getPropertyString("dataListId");
        final String formId = getPropertyString("formId");
        final AppDefinition appDefinition = AppUtil.getCurrentAppDefinition();

        dataModel.put("dataListId", dataListId);
        dataModel.put("formId", formId);
        dataModel.put("appId", appDefinition.getAppId());
        dataModel.put("appVersion", appDefinition.getVersion());

        String elementUniqueKey = FormUtil.getUniqueKey();
        dataModel.put("elementUniqueKey", elementUniqueKey);

        List<Map<String, String>> rowActionsInfos = new ArrayList<>();
        final DataList dataList = getDataList(dataListId);
        final DataListAction[] rowActions = dataList.getRowActions();
        for (int i = 0; i < rowActions.length; i++) {
            LogUtil.info(getClassName(), rowActions[i].getPropertyString("hrefParams"));
        }
        if (rowActions != null) {
            for (org.joget.apps.datalist.model.DataListAction action : rowActions) {
                Map<String, String> actionInfo = new HashMap<>();
                actionInfo.put("id", action.getPropertyString("id"));
                actionInfo.put("label", action.getPropertyString("label"));
                actionInfo.put("href", action.getHref());
                actionInfo.put("hrefParams", action.getPropertyString("hrefParams"));
                rowActionsInfos.add(actionInfo);
            }
        }
        dataModel.put("rowActions", rowActionsInfos);


        WorkflowUserManager workflowUserManager = (WorkflowUserManager) applicationContext.getBean("workflowUserManager");
        final User currentUser = workflowUserManager.getCurrentUser();

        final boolean hasPermissionToEdit = optPermission().map(permission -> {
            permission.setCurrentUser(currentUser);
            return permission.isAuthorize();
        }).orElse(false);

        if (formId.isEmpty()){
            dataModel.put("editable", false);
        }else {
            final JSONObject jsonForm = getJsonForm(formId, !hasPermissionToEdit);
            dataModel.put("jsonForm", StringEscapeUtils.escapeHtml4(jsonForm.toString()));

            final String nonce = generateNonce(appDefinition, jsonForm.toString());
            dataModel.put("nonce", nonce);

            dataModel.put("editable", hasPermissionToEdit);
        }

        return pluginManager.getPluginFreeMarkerTemplate(dataModel, getClassName(), "/templates/KanbanUserViewMenu.ftl",
                null);
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
        return "JKanban";
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
        return "JKanban";
    }

    @Override
    public String getClassName() {
        return getClass().getName();
    }

    @Override
    public String getPropertyOptions() {
        return AppUtil.readPluginResource(getClassName(), "/properties/userview/KanbanUserViewMenu.json");
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
                .map(Try.toPeek(e -> FormUtil.setReadOnlyProperty(e, readonly, readonly)))
                .map(formService::generateElementJson)
                .map(Try.onFunction(JSONObject::new))
                .orElseGet(JSONObject::new);
    }

    protected String generateNonce(AppDefinition appDefinition, String jsonForm) {
        return SecurityUtil.generateNonce(
                new String[] { "EmbedForm", appDefinition.getAppId(), appDefinition.getVersion().toString(), jsonForm },
                1);
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

    protected Optional<UserviewPermission> optPermission() {
        final ApplicationContext applicationContext = AppUtil.getApplicationContext();
        final PluginManager pluginManager = (PluginManager) applicationContext.getBean("pluginManager");
        return Optional.of("permission")
                .map(this::getProperty)
                .map(o -> (Map<String, Object>) o)
                .map(pluginManager::getPlugin);
    }
}
