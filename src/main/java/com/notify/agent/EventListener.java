package com.notify.agent;

import com.notify.agent.annotations.Event;
import com.notify.agent.client.models.EventCapture;
import com.notify.agent.client.models.EventSchedule;
import com.notify.agent.client.models.dto.RuleResultDto;
import com.notify.agent.client.models.dto.SubjectResultDto;
import com.notify.agent.client.models.metadata.RuleMetadata;
import com.notify.agent.client.models.subject.Subject;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Intercepts @Event method execution, sends event details to the Buffer (for
 * acp-server via Dispatcher). Delegates to InvokeManager for before/after
 * callbacks; enriches and sends EventCaptureDto to the Dispatcher. Records
 * metrics.
 * For Kafka scheduled events, see NotifyKafkaListener.
 */
@Aspect
public class EventListener {

    private final Buffer buffer;
    private final InvokeManager invokeManager;
    private final MetricsManager metricsManager;
    private final ObjectMapper mapper;
    private final VocabularyManager vocabularyManager;

    public EventListener(Buffer buffer, InvokeManager invokeManager,
            MetricsManager metricsManager, VocabularyManager vocabularyManager) {
        this.buffer = buffer;
        this.invokeManager = invokeManager;
        this.metricsManager = metricsManager != null ? metricsManager : new MetricsManager();
        this.mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        this.vocabularyManager = vocabularyManager;
    }

    @Around("@annotation(ev)")
    public Object aroundEvent(ProceedingJoinPoint pjp, Event ev) throws Throwable {
        Event ann = ev;
        String eventKey = ann.key();
        long t0 = System.currentTimeMillis();

        Object result = null;
        Throwable caughtException = null;

        try {
            invokeManager.invokeCallbacksBefore(eventKey, pjp.getArgs());
        } catch (Exception e) {
            // log and continue
        }

        // Execute the actual event method
        try {
            result = pjp.proceed();
        } catch (Throwable e) {
            caughtException = e;
        }

        try {
            invokeManager.invokeCallbacksAfter(eventKey, pjp.getArgs());
        } catch (Exception e) {
            // log and continue
        }

        long duration = System.currentTimeMillis() - t0;

        EventCapture dto = new EventCapture();
        dto.getEvent().setName(eventKey);
        dto.getEvent().setEventType("USER");
        dto.getEvent().setDescription(ann.description());
        dto.setOccuredAt(Instant.now());
        dto.setPayload(vocabularyManager.toFlattenedMap(pjp.getArgs()[0]));
        dto.setDurationMillis(duration);

        // Limit call stack depth to 5
        StackTraceElement[] fullStack = Thread.currentThread().getStackTrace();
        StackTraceElement[] limitedStack = Arrays.copyOf(fullStack, Math.min(5, fullStack.length));

        // Convert StackTraceElement[] to CallStack
        com.notify.agent.client.models.CallStack callStack = new com.notify.agent.client.models.CallStack();
        List<com.notify.agent.client.models.StackFrame> frames = new ArrayList<>();
        for (StackTraceElement element : limitedStack) {
            com.notify.agent.client.models.StackFrame frame = new com.notify.agent.client.models.StackFrame();
            frame.setClassName(element.getClassName());
            frame.setMethodName(element.getMethodName());
            frame.setLineNumber(element.getLineNumber());
            frame.setFileName(element.getFileName());
            frames.add(frame);
        }
        callStack.setFrames(frames);
        dto.setCallStack(callStack);

        dto.setServiceName(pjp.getTarget().getClass().getSimpleName());

        try {
            // Execute subject supplier and capture result
            SubjectResultDto subjectResult = executeSubjectSupplier(eventKey, pjp.getArgs());
            dto.setSubjectResult(subjectResult);

            // Execute rule-annotated methods and capture results
            List<RuleResultDto> ruleResults = executeRules(eventKey, pjp.getArgs());
            dto.setRuleResults(ruleResults);
        } catch (Exception e) {
            // log and continue
            e.printStackTrace();
            return null;
        }

        // Set execution result
        if (caughtException != null) {
            // Set exception info
            com.notify.agent.client.models.ExceptionInfo exceptionInfo = new com.notify.agent.client.models.ExceptionInfo();
            exceptionInfo.setExceptionType(caughtException.getClass().getName());
            exceptionInfo.setMessage(caughtException.getMessage());

            StringWriter sw = new StringWriter();
            caughtException.printStackTrace(new PrintWriter(sw));
            exceptionInfo.setStackTrace(sw.toString());
            dto.setException(exceptionInfo);

            // Set result as failure
            com.notify.agent.client.models.ExecutionResult executionResult = new com.notify.agent.client.models.ExecutionResult();
            executionResult.setSuccess(false);
            executionResult.setReturnValue(null);
            dto.setResult(executionResult);
        } else {
            // Set result as success
            com.notify.agent.client.models.ExecutionResult executionResult = new com.notify.agent.client.models.ExecutionResult();
            executionResult.setSuccess(true);
            try {
                executionResult.setReturnValue(result != null ? mapper.writeValueAsString(result) : null);
            } catch (Exception e) {
                executionResult.setReturnValue(result != null ? result.toString() : null);
            }
            dto.setResult(executionResult);
            dto.setException(null);
        }

        buffer.addEventCapture(dto);
        if (metricsManager != null)
            metricsManager.recordEventCapture(eventKey, duration);

        // Re-throw the exception if one was caught
        if (caughtException != null) {
            throw caughtException;
        }

        return result;
    }

    /**
     * Execute subject supplier for the event and return the result DTO
     */
    @SuppressWarnings("unchecked")
    private SubjectResultDto executeSubjectSupplier(String eventKey, Object[] args) {
        SubjectResultDto resultDto = new SubjectResultDto();
        resultDto.setEventKey(eventKey);

        long t0 = System.currentTimeMillis();
        try {
            List<?> subjects = invokeManager.invokeSubjectSupplier(eventKey, args);
            long executionTime = System.currentTimeMillis() - t0;
            validateSubjects(eventKey, subjects);

            resultDto.setSubjects((List<Subject>) subjects);
            resultDto.setExecutionTimeMillis(executionTime);
            resultDto.setSuccess(true);
            resultDto.setErrorMessage(null);
        } catch (Exception e) {
            long executionTime = System.currentTimeMillis() - t0;
            resultDto.setSubjects(new ArrayList<>());
            resultDto.setExecutionTimeMillis(executionTime);
            resultDto.setSuccess(false);
            resultDto.setErrorMessage(e.getMessage());
        }

        return resultDto;
    }

    private void validateSubjects(String eventKey, List<?> subjects) {
        if (subjects == null) {
            throw new IllegalStateException("@SubjectSupplier for event '" + eventKey + "' returned null");
        }
        for (int i = 0; i < subjects.size(); i++) {
            Object candidate = subjects.get(i);
            if (!(candidate instanceof Subject subject)) {
                throw new IllegalStateException("@SubjectSupplier for event '" + eventKey
                        + "' returned non-Subject item at index " + i + ": "
                        + (candidate == null ? "null" : candidate.getClass().getName()));
            }
            String address = subject.getAddress();
            if (address == null || address.isBlank()) {
                throw new IllegalStateException("@SubjectSupplier for event '" + eventKey
                        + "' returned " + subject.getChannel() + " subject at index " + i
                        + " without a destination address");
            }
        }
    }

    /**
     * Execute all rule-annotated methods for the event and return the result DTOs
     */
    private List<RuleResultDto> executeRules(String eventKey, Object[] args) {
        List<RuleResultDto> results = new ArrayList<>();
        List<RuleMetadata> rules = invokeManager.getRulesForEvent(eventKey);

        for (RuleMetadata rule : rules) {
            RuleResultDto resultDto = new RuleResultDto();
            resultDto.setRuleName(rule.getName());
            resultDto.setEventKey(eventKey);

            long t0 = System.currentTimeMillis();
            try {
                Object ruleResult = invokeManager.invokeRule(rule, args);
                long executionTime = System.currentTimeMillis() - t0;

                resultDto.setResult(ruleResult);
                resultDto.setExecutionTimeMillis(executionTime);
                resultDto.setSuccess(true);
                resultDto.setErrorMessage(null);
            } catch (Exception e) {
                long executionTime = System.currentTimeMillis() - t0;
                resultDto.setResult(null);
                resultDto.setExecutionTimeMillis(executionTime);
                resultDto.setSuccess(false);
                resultDto.setErrorMessage(e.getMessage());
            }

            results.add(resultDto);
        }

        return results;
    }

    public void onScheduledEvent(EventSchedule schedule) {
        try {

            EventCapture dto = new EventCapture();
            dto.getEvent().setName(schedule.getEventName());

            if (dto.getOccuredAt() == null)
                dto.setOccuredAt(Instant.now());
            if (dto.getEvent().getEventType() == null || dto.getEvent().getEventType().isEmpty())
                dto.getEvent().setEventType("SCHEDULED");

            String eventKey = dto.getEvent().getName();
            if (eventKey != null && invokeManager != null) {
                try {
                    Object vocab = invokeManager.invokeVocabularySupplier(eventKey, dto.getPayload());
                    if (vocab != null) {
                        dto.setPayload(vocabularyManager.toFlattenedMap(vocab));
                    }
                } catch (Exception ignored) {
                }
            }

            buffer.addEventCapture(dto);
            if (metricsManager != null)
                metricsManager.recordEventCapture(dto.getEvent().getName(), 0);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
