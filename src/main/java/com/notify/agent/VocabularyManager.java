package com.notify.agent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.notify.agent.client.models.Vocabulary;

import com.notify.agent.client.models.AttributeModel;
import com.notify.agent.client.models.ClassModel;
import com.notify.agent.client.models.metadata.ModelMetadata;
import com.notify.agent.client.models.metadata.VocabularyFieldMetadata;

/**
 * Extracts vocabulary using the AnnotationProcessor's @Model and @Vocabulary
 * metadata and forms ClassModelDto objects for acp-server vocabulary ingestion.
 */
public class VocabularyManager {

    private final AnnotationProcessor annotationProcessor;

    public VocabularyManager(AnnotationProcessor annotationProcessor) {
        this.annotationProcessor = annotationProcessor;
    }

    /**
     * Build a root Vocabulary instance representing the instance graph.
     * Each Vocabulary object's currentValue is set to the instance field's value.
     */
    public Vocabulary toInstanceVocabularyGraph(Object instance) {
        if (instance == null)
            return null;

        Class<?> clazz = instance.getClass();
        ModelMetadata metadata = annotationProcessor.getModels().stream()
                .filter(m -> m.getModelClass().equals(clazz))
                .findFirst()
                .orElse(null);

        Vocabulary root = new Vocabulary();
        root.setTerm(clazz.getSimpleName());
        root.setType("CLASS");
        root.setCurrentValue(instance);
        if (metadata != null) {
            root.setDescription(metadata.getDescription());
        }

        Set<Object> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        visited.add(instance);
        buildInstanceGraphInternal(instance, root, visited);
        return root;
    }

    private void buildInstanceGraphInternal(Object instance, Vocabulary parent,
            Set<Object> visited) {
        if (instance == null)
            return;

        Class<?> clazz = instance.getClass();
        ModelMetadata metadata = annotationProcessor.getModels().stream()
                .filter(m -> m.getModelClass().equals(clazz))
                .findFirst()
                .orElse(null);

        if (metadata == null)
            return;

        for (VocabularyFieldMetadata vf : metadata.getVocabularyFields()) {
            Vocabulary v = new Vocabulary();
            v.setTerm(vf.getName());
            v.setDescription(vf.getDescription());
            v.setType(vf.getField().getType().getSimpleName());
            v.setParent(parent);
            parent.getChildren().add(v);

            try {
                vf.getField().setAccessible(true);
                Object val = vf.getField().get(instance);
                v.setCurrentValue(val);

                // If the value is another @Model, recurse
                if (val != null && visited.add(val)) {
                    buildInstanceGraphInternal(val, v, visited);
                }
            } catch (IllegalAccessException e) {
                // Silently skip if we can't access
            }
        }
    }

    public java.util.Map<String, Object> toFlattenedMap(Object instance) {
        Vocabulary root = toInstanceVocabularyGraph(instance);
        java.util.Map<String, Object> result = new java.util.HashMap<>();
        if (root != null) {
            for (Vocabulary child : root.getChildren()) {
                flattenVocabulary(child.getTerm(), child, result);
            }
        }
        return result;
    }

    private void flattenVocabulary(String prefix, Vocabulary current, java.util.Map<String, Object> result) {
        if (current.getChildren().isEmpty()) {
            result.put(prefix, current.getCurrentValue());
        } else {
            for (Vocabulary child : current.getChildren()) {
                String newPrefix = prefix.isEmpty() ? child.getTerm() : prefix + "." + child.getTerm();
                flattenVocabulary(newPrefix, child, result);
            }
        }
    }

    /**
     * Build ClassModelDto list from @Model classes and their @Vocabulary fields.
     */
    public List<ClassModel> toClassModelDtoList() {
        List<ClassModel> result = new ArrayList<>();
        for (ModelMetadata model : annotationProcessor.getModels()) {
            ClassModel dto = new ClassModel();
            Class<?> c = model.getModelClass();
            dto.setPackageName(c.getPackage() != null ? c.getPackage().getName() : "");
            dto.setClassName(c.getSimpleName());
            dto.setClassDescription(model.getDescription());
            // dto.setClassType("CLASS");
            dto.setSuperClass(c.getSuperclass() != null && c.getSuperclass() != Object.class
                    ? c.getSuperclass().getName()
                    : null);
            dto.setInterfaces(
                    java.util.Arrays.stream(c.getInterfaces()).map(Class::getName).collect(Collectors.toList()));

            List<AttributeModel> attrs = new ArrayList<>();
            for (VocabularyFieldMetadata vf : model.getVocabularyFields()) {
                AttributeModel a = new AttributeModel();
                a.setName(vf.getName());
                a.setType(vf.getField().getType().getSimpleName());
                a.setDescription(vf.getDescription());
                attrs.add(a);
            }
            dto.setAttributes(attrs);
            dto.setMethods(new ArrayList<>()); // optional; vocabulary consumer may not require
            result.add(dto);
        }
        return result;
    }
}
