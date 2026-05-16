package com.notify.agent;

import com.notify.agent.annotations.Callback.When;
import com.notify.agent.client.models.metadata.*;

import org.springframework.context.ApplicationContext;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;

/**
 * Holds the mapping from event keys to rule executors, vocabulary suppliers,
 * subject suppliers, and before/after callbacks. Invokes the respective methods
 * and returns validated results. Records metrics via MetricsManager.
 */
public class InvokeManager {

    private final ApplicationContext applicationContext;
    private final MetricsManager metricsManager;

    private final Map<String, List<RuleMetadata>> rulesByEvent = new HashMap<>();
    private final Map<String, VocabularySupplierMetadata> vocabularySupplierByEvent = new HashMap<>();
    private final Map<String, SubjectSupplierMetadata> subjectSupplierByEvent = new HashMap<>();
    private final Map<String, List<CallbackMetadata>> callbacksBeforeByEvent = new HashMap<>();
    private final Map<String, List<CallbackMetadata>> callbacksAfterByEvent = new HashMap<>();

    public InvokeManager(ApplicationContext applicationContext, MetricsManager metricsManager) {
        this.applicationContext = applicationContext;
        this.metricsManager = metricsManager != null ? metricsManager : new MetricsManager();
    }

    public void buildFrom(AnnotationProcessor processor) {
        for (RuleMetadata r : processor.getRules()) {
            String ev = (r.getEvent() == null || r.getEvent().isEmpty()) ? "*" : r.getEvent();
            rulesByEvent.computeIfAbsent(ev, k -> new ArrayList<>()).add(r);
        }
        for (VocabularySupplierMetadata v : processor.getVocabularySuppliers()) {
            vocabularySupplierByEvent.put(v.getEvent(), v);
        }
        for (SubjectSupplierMetadata s : processor.getSubjectSuppliers()) {
            subjectSupplierByEvent.put(s.getEvent(), s);
        }
        for (CallbackMetadata c : processor.getCallbacks()) {
            if (c.getWhen() == When.BEFORE) {
                callbacksBeforeByEvent.computeIfAbsent(c.getEvent(), k -> new ArrayList<>()).add(c);
            } else {
                callbacksAfterByEvent.computeIfAbsent(c.getEvent(), k -> new ArrayList<>()).add(c);
            }
        }
    }

    public List<RuleMetadata> getRulesForEvent(String eventKey) {
        List<RuleMetadata> direct = rulesByEvent.getOrDefault(eventKey, Collections.emptyList());
        List<RuleMetadata> wild = rulesByEvent.getOrDefault("*", Collections.emptyList());
        List<RuleMetadata> out = new ArrayList<>(direct);
        out.addAll(wild);
        return out;
    }

    public VocabularySupplierMetadata getVocabularySupplierForEvent(String eventKey) {
        return vocabularySupplierByEvent.get(eventKey);
    }

    public SubjectSupplierMetadata getSubjectSupplierForEvent(String eventKey) {
        return subjectSupplierByEvent.get(eventKey);
    }

    public List<CallbackMetadata> getCallbacksBefore(String eventKey) {
        return callbacksBeforeByEvent.getOrDefault(eventKey, Collections.emptyList());
    }

    public List<CallbackMetadata> getCallbacksAfter(String eventKey) {
        return callbacksAfterByEvent.getOrDefault(eventKey, Collections.emptyList());
    }

    /**
     * Invoke a rule method. args are typically (eventPayload or context map).
     */
    public Object invokeRule(RuleMetadata rule, Object... args) throws Exception {
        Object bean = applicationContext.getBean(rule.getDeclaringClass());
        Method m = rule.getMethod();
        m.setAccessible(true);
        long t0 = System.currentTimeMillis();
        try {
            Object result = m.invoke(bean, args);
            if (metricsManager != null) metricsManager.recordRuleInvoke(rule.getName(), System.currentTimeMillis() - t0);
            return result;
        } catch (InvocationTargetException e) {
            throw (e.getCause() instanceof Exception) ? (Exception) e.getCause() : e;
        }
    }

    /**
     * Invoke vocabulary supplier for the event. Returns the payload object.
     */
    public Object invokeVocabularySupplier(String eventKey, Object... args) throws Exception {
        VocabularySupplierMetadata v = vocabularySupplierByEvent.get(eventKey);
        if (v == null) return null;
        Object bean = applicationContext.getBean(v.getDeclaringClass());
        Method m = v.getMethod();
        m.setAccessible(true);
        long t0 = System.currentTimeMillis();
        try {
            Object result = m.invoke(bean, args);
            if (metricsManager != null) metricsManager.recordVocabularySupplierInvoke(eventKey, System.currentTimeMillis() - t0);
            return result;
        } catch (InvocationTargetException e) {
            throw (e.getCause() instanceof Exception) ? (Exception) e.getCause() : e;
        }
    }

    /**
     * Invoke subject supplier. Returns a List (subjects).
     */
    @SuppressWarnings("unchecked")
    public List<?> invokeSubjectSupplier(String eventKey, Object... args) throws Exception {
        SubjectSupplierMetadata s = subjectSupplierByEvent.get(eventKey);
        if (s == null) return Collections.emptyList();
        Object bean = applicationContext.getBean(s.getDeclaringClass());
        Method m = s.getMethod();
        m.setAccessible(true);
        long t0 = System.currentTimeMillis();
        try {
            Object result = m.invoke(bean, args);
            if (metricsManager != null) metricsManager.recordSubjectSupplierInvoke(eventKey, System.currentTimeMillis() - t0);
            if (result instanceof List) return (List<?>) result;
            return result != null ? Collections.singletonList(result) : Collections.emptyList();
        } catch (InvocationTargetException e) {
            throw (e.getCause() instanceof Exception) ? (Exception) e.getCause() : e;
        }
    }

    public void invokeCallbacksBefore(String eventKey, Object... args) throws Exception {
        for (CallbackMetadata c : getCallbacksBefore(eventKey)) {
            Object bean = applicationContext.getBean(c.getDeclaringClass());
            Method m = c.getMethod();
            m.setAccessible(true);
            m.invoke(bean, args);
        }
    }

    public void invokeCallbacksAfter(String eventKey, Object... args) throws Exception {
        for (CallbackMetadata c : getCallbacksAfter(eventKey)) {
            Object bean = applicationContext.getBean(c.getDeclaringClass());
            Method m = c.getMethod();
            m.setAccessible(true);
            m.invoke(bean, args);
        }
    }
}
