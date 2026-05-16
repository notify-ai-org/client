package com.notify.agent.client.enums;

/**
 * Types of events emitted by the AgentOrchestrator.
 */
public enum AgentOrchestratorEventType {
    AGENT_REGISTERED,
    AGENT_STAGE_CHANGED,
    TASK_ENQUEUED,
    TASK_STARTED,
    TASK_COMPLETED,
    TASK_FAILED,
    POOL_FULL,
    POOL_EMPTY,
    AGENT_EVICTED
}
