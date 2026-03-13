package com.kinnara.kecakplugins.jkanban.kanban;

import com.kinnarastudio.commons.Try;
import org.apache.commons.lang3.StringEscapeUtils;
import org.joget.apps.app.dao.FormDefinitionDao;
import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.model.FormDefinition;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.service.FormService;
import org.joget.apps.form.service.FormUtil;
import org.joget.apps.userview.model.UserviewMenu;
import org.joget.commons.util.SecurityUtil;
import org.joget.plugin.base.PluginManager;
import org.json.JSONObject;
import org.springframework.context.ApplicationContext;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.ResourceBundle;

public class KanbanPlugin extends UserviewMenu {

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

        final JSONObject jsonForm = getJsonForm(formId, false);
        dataModel.put("jsonForm", StringEscapeUtils.escapeHtml4(jsonForm.toString()));

        final String nonce = generateNonce(appDefinition, jsonForm.toString());
        dataModel.put("nonce", nonce);

        String elementUniqueKey = FormUtil.getUniqueKey();
        dataModel.put("elementUniqueKey", elementUniqueKey);

        return pluginManager.getPluginFreeMarkerTemplate(dataModel, getClassName(), "/templates/KanbanUserView.ftl",
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
        return AppUtil.readPluginResource(getClassName(), "/properties/userview/JKanbanMenu.json");
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
                new String[]{"EmbedForm", appDefinition.getAppId(), appDefinition.getVersion().toString(), jsonForm},
                1);
    }
}
