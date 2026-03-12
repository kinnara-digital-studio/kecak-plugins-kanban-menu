package com.kinnara.kecakplugins.jkanban.kanban;

import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.userview.model.UserviewMenu;
import org.joget.plugin.base.PluginManager;
import org.springframework.context.ApplicationContext;

import java.util.HashMap;
import java.util.Map;
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
}
