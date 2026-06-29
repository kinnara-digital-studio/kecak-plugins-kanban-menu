package com.kinnara.kecakplugins.jkanban.datalist;

import com.kinnarastudio.commons.jsonstream.JSONCollectors;
import com.kinnarastudio.commons.jsonstream.JSONStream;
import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.datalist.lib.FormRowDataListBinder;
import org.joget.apps.datalist.model.*;
import org.joget.apps.userview.lib.InboxMenu;
import org.joget.commons.util.LogUtil;
import org.joget.plugin.base.PluginManager;
import org.joget.workflow.model.WorkflowProcess;
import org.joget.workflow.model.service.WorkflowManager;
import org.json.JSONArray;
import org.json.JSONException;
import org.springframework.context.ApplicationContext;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class KanbanWorkflowDataListBinder extends FormRowDataListBinder {
    public final static String LABEL = "Kanban Workflow DataList Binder";

    private Collection<WorkflowProcess> runningProcessList = null;

    @Override
    public DataListColumn[] getColumns() {
        return super.getColumns();
    }

    @Override
    public DataListCollection getData(DataList dataList, Map properties, DataListFilterQueryObject[] filterQueryObjects, String sort, Boolean desc, Integer start, Integer rows) {
        AppDefinition appDefinition = AppUtil.getCurrentAppDefinition();
        String appId = appDefinition.getPackageDefinition().getAppId();
        String processId = getProcessDefId();
        Collection<WorkflowProcess> runningProcessList = getRunningProcessList(appId, processId);
        Collection<String> recordIds = Optional.ofNullable(runningProcessList)
                .orElseGet(Collections::emptyList)
                .stream()
                .map(WorkflowProcess::getRecordId)
                .collect(Collectors.toList());
        filterQueryObjects = addFilterByRecordId(filterQueryObjects, recordIds);
        return super.getData(dataList, properties, filterQueryObjects, sort, desc, start, rows);
    }

    @Override
    public int getDataTotalRowCount(DataList dataList, Map properties, DataListFilterQueryObject[] filterQueryObjects) {
        AppDefinition appDefinition = AppUtil.getCurrentAppDefinition();
        String appId = appDefinition.getPackageDefinition().getAppId();
        String processId = getProcessDefId();
        Collection<WorkflowProcess> runningProcessList = getRunningProcessList(appId, processId);
        Collection<String> recordIds = Optional.ofNullable(runningProcessList)
                .orElseGet(Collections::emptyList)
                .stream()
                .map(WorkflowProcess::getRecordId)
                .collect(Collectors.toList());
        filterQueryObjects = addFilterByRecordId(filterQueryObjects, recordIds);
        return super.getDataTotalRowCount(dataList, properties, filterQueryObjects);
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
        try {
            String parentPropertyOptions = super.getPropertyOptions();
            JSONArray jsonParentPropertyOptions = new JSONArray(parentPropertyOptions);

            Object[] args = new Object[] {InboxMenu.class.getName()};
            String childPropertyOptions = AppUtil.readPluginResource(getClassName(), "/properties/datalist/KanbanWorkflowDataListBinder.json", args, true, "/messages/jkanban");
            JSONArray jsonChildPropertyOptions = new JSONArray(childPropertyOptions);

            JSONArray jsonMergedPropertyOptions = Stream.concat(JSONStream.of(jsonParentPropertyOptions, JSONArray::optJSONObject), JSONStream.of(jsonChildPropertyOptions, JSONArray::optJSONObject))
                    .collect(JSONCollectors.toJSONArray());

            return jsonMergedPropertyOptions.toString();
        } catch (JSONException e) {
            LogUtil.error(getClassName(), e, e.getMessage());
            return null;
        }
    }

    public void setProcessDefId(String processDefId) {
        setProperty("processDefId", processDefId);
    }

    public String getProcessDefId() {
        return getPropertyString("processDefId");
    }

    protected DataListFilterQueryObject[] addFilterByRecordId(DataListFilterQueryObject[] filterQueryObjects, Collection<String> recordIds) {
        List<DataListFilterQueryObject> filterQueryObjectsList = Optional.ofNullable(filterQueryObjects)
                .stream()
                .flatMap(Arrays::stream)
                .collect(Collectors.toList());

        DataListFilterQueryObject recordIdQueryObject = new DataListFilterQueryObject() {{
            if(recordIds == null || recordIds.isEmpty()) {
                setQuery("1 <> 1");
            } else {
                String query = recordIds.stream().map(s -> "?").collect(Collectors.joining(", ", "id IN (", ")"));
                setQuery(query);
                setValues(recordIds.toArray(new String[0]));
            }
        }};

        filterQueryObjectsList.add(recordIdQueryObject);

        return filterQueryObjectsList.toArray(new DataListFilterQueryObject[0]);
    }

    protected Collection<WorkflowProcess> getRunningProcessList(String packageId, String processDefId) {
        ApplicationContext applicationContext = AppUtil.getApplicationContext();
        WorkflowManager workflowManager = (WorkflowManager) applicationContext.getBean("workflowManager");

        if(runningProcessList == null) {
            runningProcessList = workflowManager.getRunningProcessList(packageId, processDefId, null, null, null, null, null, null);
        }

        return runningProcessList;
    }
}
