package com.qcadoo.mes.core.data.definition.view;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.qcadoo.mes.core.data.beans.Entity;
import com.qcadoo.mes.core.data.definition.DataDefinition;
import com.qcadoo.mes.core.data.definition.DataFieldDefinition;
import com.qcadoo.mes.core.data.internal.types.BelongsToType;
import com.qcadoo.mes.core.data.internal.types.HasManyType;

public abstract class ComponentDefinition<T> {

    private final String name;

    private final String path;

    private final String fieldPath;

    private String sourceFieldPath;

    private ComponentDefinition<?> sourceComponent;

    private boolean initialized;

    private final Map<String, String> options = new HashMap<String, String>();

    private final ContainerDefinition<?> parentContainer;

    private final Set<String> listeners = new HashSet<String>();

    private DataDefinition dataDefinition;

    public abstract String getType();

    public abstract ViewEntity<T> castComponentValue(Entity entity, Map<String, List<Entity>> selectedEntities,
            JSONObject viewObject) throws JSONException;

    public final ViewEntity<T> castValue(final Entity entity, final Map<String, List<Entity>> selectedEntities,
            final JSONObject viewObject) throws JSONException {
        ViewEntity<T> value = castComponentValue(entity, selectedEntities, viewObject);
        if (viewObject != null) {
            value.setEnabled(viewObject.getBoolean("enabled"));
            value.setVisible(viewObject.getBoolean("visible"));
        }
        return value;
    }

    public abstract ViewEntity<T> getComponentValue(Entity entity, Map<String, List<Entity>> selectedEntities,
            ViewEntity<Object> globalViewEntity, ViewEntity<T> viewEntity);

    @SuppressWarnings("unchecked")
    public ViewEntity<T> getValue(final Entity entity, final Map<String, List<Entity>> selectedEntities,
            final ViewEntity<Object> globalViewEntity, final ViewEntity<?> viewEntity) {
        if (sourceComponent != null) {
            Entity selectedEntity = selectedEntities.get(sourceComponent.getPath()) != null
                    && selectedEntities.get(sourceComponent.getPath()).size() == 1 ? selectedEntities.get(
                    sourceComponent.getPath()).get(0) : null;
            // TODO multiselection?
            return getComponentValue(selectedEntity, selectedEntities, globalViewEntity, (ViewEntity<T>) viewEntity);
        } else {
            return getComponentValue(entity, selectedEntities, globalViewEntity, (ViewEntity<T>) viewEntity);
        }
    }

    protected String getFieldValue(final Entity entity, final String path) {
        if (entity == null) {
            return "";
        }

        if (path != null) {
            String[] fields = path.split("\\.");
            Object value = entity;

            for (String field : fields) {
                if (value instanceof Entity) {
                    value = ((Entity) value).getField(field);
                } else {
                    return "";
                }
            }

            return String.valueOf(value);
        }

        return "";
    }

    public ComponentDefinition(final String name, final ContainerDefinition<?> parentContainer, final String fieldPath,
            final String sourceFieldPath) {
        this.name = name;
        this.parentContainer = parentContainer;
        this.fieldPath = fieldPath;

        if (parentContainer != null) {
            this.path = parentContainer.getPath() + "." + name;
        } else {
            this.path = name;
        }

        this.sourceFieldPath = sourceFieldPath;
    }

    public boolean initializeComponent(final Map<String, ComponentDefinition<?>> componentRegistry) {
        if (sourceFieldPath != null && sourceFieldPath.startsWith("#")) {
            String[] source = parseSourceFieldPath(sourceFieldPath);
            sourceComponent = componentRegistry.get(source[0]);

            if (sourceComponent == null || !sourceComponent.isInitialized()) {
                return false;
            }

            sourceFieldPath = source[1];
            dataDefinition = sourceComponent.getDataDefinition();
            sourceComponent.registerListener(path);
        } else if (parentContainer != null) {
            if (!parentContainer.isInitialized()) {
                return false;
            }
            sourceComponent = null;
            dataDefinition = parentContainer.getDataDefinition();
        } else {
            sourceComponent = null;
            dataDefinition = null;
        }

        if (sourceFieldPath != null) {
            dataDefinition = getDataDefinitionBasedOnFieldPath(dataDefinition, sourceFieldPath);
        } else if (fieldPath != null) {
            dataDefinition = getDataDefinitionBasedOnFieldPath(dataDefinition, fieldPath);
        }

        this.initialized = true;

        return true;
    }

    private DataDefinition getDataDefinitionBasedOnFieldPath(final DataDefinition dataDefinition, final String fieldPath) {
        String[] fields = fieldPath.split("\\.");

        DataDefinition newDataDefinition = dataDefinition;

        for (String field : fields) {
            DataFieldDefinition fieldDefinition = newDataDefinition.getField(field);
            if (fieldDefinition == null) {
                break;
            }
            if (fieldDefinition.getType() instanceof BelongsToType) {
                newDataDefinition = ((BelongsToType) fieldDefinition.getType()).getDataDefinition();
            } else if (fieldDefinition.getType() instanceof HasManyType) {
                newDataDefinition = ((HasManyType) fieldDefinition.getType()).getDataDefinition();
            }
        }

        return newDataDefinition;
    }

    private String[] parseSourceFieldPath(final String sourceFieldPath) {
        if (sourceFieldPath.endsWith("}")) {
            return new String[] { sourceFieldPath.substring(2, sourceFieldPath.length() - 1), null };
        } else {
            String[] splittedSourceFieldPath = sourceFieldPath.split("}.");
            return new String[] { splittedSourceFieldPath[0].substring(2), splittedSourceFieldPath[1] };
        }
    }

    public boolean isContainer() {
        return false;
    }

    public String getName() {
        return name;
    }

    public String getSourceFieldPath() {
        return sourceFieldPath;
    }

    public String getPath() {
        return path;
    }

    public String getFieldPath() {
        return fieldPath;
    }

    public Map<String, Object> getOptions() {
        Map<String, Object> viewOptions = new HashMap<String, Object>(options);
        viewOptions.put("name", name);
        viewOptions.put("listeners", listeners);
        return viewOptions;
    }

    public String getOptionsAsJson() {
        JSONObject jsonOptions = new JSONObject();
        try {
            for (Entry<String, Object> option : getOptions().entrySet()) {
                Object value = null;
                if (option.getValue() instanceof Collection) {
                    Collection<?> list = (Collection<?>) option.getValue();
                    JSONArray arr = new JSONArray();
                    for (Object o : list) {
                        arr.put(o);
                    }
                    value = arr;
                } else {
                    jsonOptions.put(option.getKey(), option.getValue());
                    value = option.getValue();
                }
                if (value != null) {
                    jsonOptions.put(option.getKey(), option.getValue());
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return jsonOptions.toString();
    }

    protected ComponentDefinition<?> getSourceComponent() {
        return sourceComponent;
    }

    protected ContainerDefinition<?> getParentContainer() {
        return parentContainer;
    }

    public DataDefinition getDataDefinition() {
        return dataDefinition;
    }

    public void setDataDefinition(final DataDefinition dataDefinition) {
        this.dataDefinition = dataDefinition;
    }

    public void addOptions(final String name, final String value) {
        this.options.put(name, value);
    }

    protected void registerListener(final String path) {
        listeners.add(path);
    }

    public boolean isInitialized() {
        return initialized;
    }

    @Override
    public String toString() {
        String dd = dataDefinition != null ? dataDefinition.getName() : "null";
        String sc = sourceComponent != null ? sourceComponent.getPath() : "null";
        return path + ", [" + fieldPath + ", " + sourceFieldPath + ", " + sc + "], [" + listeners + "], " + dd;
    }

}
