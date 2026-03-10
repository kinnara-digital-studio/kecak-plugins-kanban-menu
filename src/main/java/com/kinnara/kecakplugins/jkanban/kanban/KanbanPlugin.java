package com.kinnara.kecakplugins.jkanban.kanban;

import org.joget.apps.app.service.AppUtil;
import org.joget.apps.userview.model.UserviewMenu;
import org.joget.plugin.base.PluginManager;
import org.springframework.context.ApplicationContext;

import java.awt.*;
import java.util.HashMap;
import java.util.Map;

public class KanbanPlugin extends UserviewMenu {

    @Override
    public String getCategory() {
        return "Kecak";
    }

    @Override
    public String getIcon() {
        return null;
    }

    @Override
    public String getRenderPage() {
        ApplicationContext applicationContext = AppUtil.getApplicationContext();
        PluginManager pluginManager = (PluginManager) applicationContext.getBean("pluginManager");

        Map<String, Object> dataModel = new HashMap<>();

        return pluginManager.getPluginFreeMarkerTemplate(dataModel, getClassName(), "/templates/KanbanUserView.ftl", null);
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
        return "J Kanban";
    }

    @Override
    public String getVersion() {
        return getClass().getPackage().getImplementationVersion();
    }

    @Override
    public String getDescription() {
        return getClass().getPackage().getImplementationTitle();
    }

    @Override
    public String getLabel() {
        return "J Kanban";
    }

    @Override
    public String getClassName() {
        return getClass().getName();
    }

    @Override
    public String getPropertyOptions() {
        return "";
    }
}
