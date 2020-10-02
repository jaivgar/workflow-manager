package se.ltu.workflow.manager.dto;

/**
 * Status of activation of workflow.
 * <p>
 * Workflows can start in either of these two states:
 * <p><ul>
 * <li>{@code IDLE}: If the Workflow Executor is busy executing a similar or 
 * incompatible workflow, it states the workflow as IDLE.
 * <p>
 * <li>{@code SCHEDULE}: If the workflow is targeted to start after a specific
 * delay, the Workflow Executor will add the SCHEDULE state to that workflow. 
 * Logic for this status is not yet implemented.
 * <p></ul><p>
 * 
 * And during the workflow lifetime the state can change to:
 * 
 * <p><ul>
 * <li>{@code ACTIVE}: If the Workflow Executor is not busy, and can start
 * executing the workflow right away, it will state the workflow as ACTIVE
 * and proceed with its execution.
 * <p>
 * <li>{@code DONE}: If the Workflow Executor has finished execution of an
 * ACTIVE workflow, it will change its state to DONE, whether it finished .
 * </ul><p>
 * 
 * At any point in time there can be only one {@code ACTIVE} workflow, that will
 * be executed by the Workflow Executor. There can be any number of IDLE, SCHEDULE
 * and DONE workflows in storage.
 * <p>
 * After the ACTIVE state the workflow will continue to the DONE state, meaning it has 
 * been executed as is now ended.
 * <p>
 * Future releases may enable multiple ACTIVE workflows.
 *
 */
public enum WStatus {
    IDLE,
    SCHEDULE,
    ACTIVE,
    DONE;
}
