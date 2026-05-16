package com.notify.agent;

import com.notify.agent.annotations.*;
import com.notify.agent.client.models.metadata.*;

import org.reflections.Reflections;
import org.reflections.scanners.Scanners;
import org.reflections.util.ConfigurationBuilder;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * Scans the client codebase for @EnableNotify and the configured base package,
 * then records @Event, @Rule, @Callback, @Vocabulary, @Model, @VocabularySupplier,
 * and @SubjectSupplier with their mappings to fields, methods, or classes.
 *
 * <p>Validation rules enforced during {@link #process()}:
 * <ul>
 *   <li>{@code @Event.key()} must not be null or blank.</li>
 *   <li>{@code @Event.priority()} must be in the range [1, 5].</li>
 *   <li>{@code @Rule.name()} must not be null or blank.</li>
 *   <li>{@code @Callback.event()} must not be null or blank.</li>
 *   <li>{@code @VocabularySupplier.event()} must not be null or blank.</li>
 *   <li>{@code @SubjectSupplier.event()} must not be null or blank.</li>
 * </ul>
 *
 * <p>When the scan finds zero annotated elements of any type, a WARNING is logged.
 *
 * <p>The {@code includeInheritedFields} flag (default {@code false}) controls
 * whether {@link #processModel} also scans fields declared in superclasses.
 */
public class AnnotationProcessor {

    private static final Logger LOG = Logger.getLogger(AnnotationProcessor.class.getName());

    private final String basePackage;
    private final Supplier<Reflections> reflectionsSupplier;

    private final List<EventMetadata>             events              = new ArrayList<>();
    private final List<RuleMetadata>              rules               = new ArrayList<>();
    private final List<CallbackMetadata>          callbacks           = new ArrayList<>();
    private final List<VocabularySupplierMetadata> vocabularySuppliers = new ArrayList<>();
    private final List<SubjectSupplierMetadata>   subjectSuppliers    = new ArrayList<>();
    private final List<ModelMetadata>             models              = new ArrayList<>();

    /**
     * When {@code true}, {@link #processModel} walks the full class hierarchy.
     * Default is {@code false} (only {@link Class#getDeclaredFields()}).
     */
    private boolean includeInheritedFields = false;

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    public AnnotationProcessor(String basePackage) {
        this(basePackage, null);
    }

    /**
     * Package-private constructor for testing: allows injecting a custom
     * {@link Reflections} supplier so unit tests can provide mocks without
     * a real classpath scan.
     */
    AnnotationProcessor(String basePackage, Supplier<Reflections> reflectionsSupplier) {
        this.basePackage = (basePackage == null || basePackage.isBlank())
                ? "com.notify"
                : basePackage;
        this.reflectionsSupplier = reflectionsSupplier != null
                ? reflectionsSupplier
                : () -> new Reflections(
                        new ConfigurationBuilder()
                                .forPackages(this.basePackage)
                                .setScanners(Scanners.TypesAnnotated, Scanners.MethodsAnnotated,
                                             Scanners.FieldsAnnotated));
    }

    // -------------------------------------------------------------------------
    // Configuration
    // -------------------------------------------------------------------------

    public boolean isIncludeInheritedFields() {
        return includeInheritedFields;
    }

    public void setIncludeInheritedFields(boolean includeInheritedFields) {
        this.includeInheritedFields = includeInheritedFields;
    }

    // -------------------------------------------------------------------------
    // Scan
    // -------------------------------------------------------------------------

    /**
     * Scan the base package and all sub-packages for annotations.
     *
     * @throws IllegalArgumentException if a discovered annotation carries invalid field values.
     */
    public void process() {
        Reflections reflections = reflectionsSupplier.get();

        // --- @Model classes ---
        Set<Class<?>> modelClasses = reflections.getTypesAnnotatedWith(Model.class);
        if (modelClasses.isEmpty()) {
            LOG.warning("No @Model classes found in package: " + basePackage);
        }
        for (Class<?> modelClass : modelClasses) {
            processModel(modelClass);
        }

        // --- @Event methods ---
        Set<Method> methods = reflections.getMethodsAnnotatedWith(Event.class);
        if (methods.isEmpty()) {
            LOG.warning("No @Event methods found in package: " + basePackage);
        }
        for (Method m : methods) {
            com.notify.agent.annotations.Event a = m.getAnnotation(com.notify.agent.annotations.Event.class);

            if (a.key() == null || a.key().isBlank()) {
                throw new IllegalArgumentException(
                        "@Event on method '" + m.getName() + "' in " + m.getDeclaringClass().getName()
                                + " must have a non-blank key().");
            }
            if (a.priority() < 1 || a.priority() > 5) {
                throw new IllegalArgumentException(
                        "@Event '" + a.key() + "' on method '" + m.getName()
                                + "' has priority=" + a.priority() + ". Must be between 1 and 5.");
            }

            com.notify.agent.client.models.Event event = new com.notify.agent.client.models.Event();
            event.setName(a.key());
            event.setDescription(a.description());
            event.setEventType(a.eventType());
            event.setPreferredTimeWindow(a.preferredTimeWindow());
            event.setScheduleIntent(a.scheduleIntent());
            event.setPriority(a.priority());
            events.add(new EventMetadata(event, a.version(), m, m.getDeclaringClass()));
        }

        // --- @Rule methods ---
        methods = reflections.getMethodsAnnotatedWith(Rule.class);
        if (methods.isEmpty()) {
            LOG.warning("No @Rule methods found in package: " + basePackage);
        }
        for (Method m : methods) {
            Rule a = m.getAnnotation(Rule.class);

            if (a.name() == null || a.name().isBlank()) {
                throw new IllegalArgumentException(
                        "@Rule on method '" + m.getName() + "' in " + m.getDeclaringClass().getName()
                                + " must have a non-blank name().");
            }

            rules.add(new RuleMetadata(a.name(), a.description(), a.event(), m, m.getDeclaringClass()));
        }

        // --- @Callback methods ---
        methods = reflections.getMethodsAnnotatedWith(Callback.class);
        if (methods.isEmpty()) {
            LOG.warning("No @Callback methods found in package: " + basePackage);
        }
        for (Method m : methods) {
            Callback a = m.getAnnotation(Callback.class);

            if (a.event() == null || a.event().isBlank()) {
                throw new IllegalArgumentException(
                        "@Callback on method '" + m.getName() + "' in " + m.getDeclaringClass().getName()
                                + " must have a non-blank event().");
            }

            callbacks.add(new CallbackMetadata(a.event(), a.when(), m, m.getDeclaringClass()));
        }

        // --- @VocabularySupplier methods ---
        methods = reflections.getMethodsAnnotatedWith(VocabularySupplier.class);
        if (methods.isEmpty()) {
            LOG.warning("No @VocabularySupplier methods found in package: " + basePackage);
        }
        for (Method m : methods) {
            VocabularySupplier a = m.getAnnotation(VocabularySupplier.class);

            if (a.event() == null || a.event().isBlank()) {
                throw new IllegalArgumentException(
                        "@VocabularySupplier on method '" + m.getName() + "' must have a non-blank event().");
            }

            vocabularySuppliers.add(
                    new VocabularySupplierMetadata(a.event(), a.description(), m, m.getDeclaringClass()));
        }

        // --- @SubjectSupplier methods ---
        methods = reflections.getMethodsAnnotatedWith(SubjectSupplier.class);
        if (methods.isEmpty()) {
            LOG.warning("No @SubjectSupplier methods found in package: " + basePackage);
        }
        for (Method m : methods) {
            SubjectSupplier a = m.getAnnotation(SubjectSupplier.class);

            if (a.event() == null || a.event().isBlank()) {
                throw new IllegalArgumentException(
                        "@SubjectSupplier on method '" + m.getName() + "' must have a non-blank event().");
            }

            subjectSuppliers.add(
                    new SubjectSupplierMetadata(a.event(), a.description(), m, m.getDeclaringClass()));
        }
    }

    // -------------------------------------------------------------------------
    // Model processing
    // -------------------------------------------------------------------------

    private void processModel(Class<?> modelClass) {
        Model ann = modelClass.getAnnotation(Model.class);
        String description = ann != null ? ann.description() : "";

        Field[] declaredFields = includeInheritedFields
                ? getAllFields(modelClass)
                : modelClass.getDeclaredFields();

        boolean hasVocabularyAnnotations = false;
        for (Field f : declaredFields) {
            if (f.isAnnotationPresent(Vocabulary.class)) {
                hasVocabularyAnnotations = true;
                break;
            }
        }

        List<VocabularyFieldMetadata> fields = new ArrayList<>();
        for (Field f : declaredFields) {
            if (hasVocabularyAnnotations) {
                if (!f.isAnnotationPresent(Vocabulary.class)) continue;
                Vocabulary v = f.getAnnotation(Vocabulary.class);
                String name = (v.name() == null || v.name().isEmpty()) ? f.getName() : v.name();
                fields.add(new VocabularyFieldMetadata(name, v.description(), f, modelClass));
            } else {
                fields.add(new VocabularyFieldMetadata(f.getName(), "", f, modelClass));
            }
        }
        models.add(new ModelMetadata(modelClass, description, fields));
    }

    private static Field[] getAllFields(Class<?> clazz) {
        List<Field> all = new ArrayList<>();
        for (Class<?> c = clazz; c != null && c != Object.class; c = c.getSuperclass()) {
            all.addAll(Arrays.asList(c.getDeclaredFields()));
        }
        return all.toArray(new Field[0]);
    }

    // -------------------------------------------------------------------------
    // Getters — all return unmodifiable views
    // -------------------------------------------------------------------------

    public List<EventMetadata>              getEvents()              { return Collections.unmodifiableList(events); }
    public List<RuleMetadata>               getRules()               { return Collections.unmodifiableList(rules); }
    public List<CallbackMetadata>           getCallbacks()           { return Collections.unmodifiableList(callbacks); }
    public List<VocabularySupplierMetadata> getVocabularySuppliers() { return Collections.unmodifiableList(vocabularySuppliers); }
    public List<SubjectSupplierMetadata>    getSubjectSuppliers()    { return Collections.unmodifiableList(subjectSuppliers); }
    public List<ModelMetadata>              getModels()              { return Collections.unmodifiableList(models); }
}
