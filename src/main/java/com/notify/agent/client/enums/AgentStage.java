package com.notify.agent.client.enums;

/**
 * Represents the different stages an agent can be in during its lifecycle
 */
public enum AgentStage {
    CREATED,        // Agent created but not started
    INITIALIZING,   // Agent is being initialized
    READY,          // Agent is ready to accept tasks
    RUNNING,        // Agent is currently executing a task
    PAUSED,         // Agent is paused (can be resumed)
    COMPLETED,      // Agent has completed its task successfully
    FAILED,         // Agent encountered an error
    TERMINATED,     // Agent has been terminated
    IDLE            // Agent is idle and waiting for tasks
}
