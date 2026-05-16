package com.notify.agent.client.models.metadata;

import java.lang.reflect.Field;

public class VocabularyFieldMetadata {
    private final String name;
    private final String description;
    private final Field field;
    private final Class<?> modelClass;

    public VocabularyFieldMetadata(String name, String description, Field field, Class<?> modelClass) {
        this.name = name;
        this.description = description;
        this.field = field;
        this.modelClass = modelClass;
    }

    public String getName() { return name; }
    public String getDescription() { return description; }
    public Field getField() { return field; }
    public Class<?> getModelClass() { return modelClass; }
}
