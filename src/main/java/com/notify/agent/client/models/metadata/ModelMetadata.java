package com.notify.agent.client.models.metadata;

import java.util.List;

public class ModelMetadata {
    private final Class<?> modelClass;
    private final String description;
    private final List<VocabularyFieldMetadata> vocabularyFields;

    public ModelMetadata(Class<?> modelClass, String description, List<VocabularyFieldMetadata> vocabularyFields) {
        this.modelClass = modelClass;
        this.description = description;
        this.vocabularyFields = vocabularyFields;
    }

    public Class<?> getModelClass() { return modelClass; }
    public String getDescription() { return description; }
    public List<VocabularyFieldMetadata> getVocabularyFields() { return vocabularyFields; }
}
