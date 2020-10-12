package se.ltu.workflow.manager;

/**
 * Stores the constants need across the whole Workflow Executor system
 *
 */
public class WManagerConstants {

    //=================================================================================================
    // members
    
    public static final String BASE_PACKAGE = "se.ltu.workflow.manager";
    
    public static final String INTERFACE_SECURE = "HTTPS-SECURE-JSON";
    public static final String INTERFACE_INSECURE = "HTTP-INSECURE-JSON";
    public static final String HTTP_METHOD = "http-method";
    
    public static final String WMANAGER_URI = "/workflow_manager";
    
    public static final String WMANAGER_TOOLS_SERVICE_DEFINITION = "wmanager-runtime";
    public static final String ECHO_URI = "/echo";
    public static final String SHUTDOWN_URI = "/shutdown";
    
    public static final String WORKSTATION_OPERATIONS_SERVICE_DEFINITION = "wmanager-workstation-operations";
    public static final String WORKSTATION_OPERATIONS_URI = "/operations";
    
    // Workflow Executor constants
    public static final String WEXECUTOR_URI = "/workflow-executor";
    
    public static final String PROVIDE_AVAILABLE_WORKFLOW_SERVICE_DEFINITION = "provide-workflows-type";
    public static final String PROVIDE_AVAILABLE_WORKFLOW_URI = "/workflows";
    
    public static final String PROVIDE_IN_EXECUTION_WORKFLOW_SERVICE_DEFINITION = "provide-workflows-in-execution";
    public static final String PROVIDE_IN_EXECUTION_WORKFLOW_URI = "/workflows/execution";
    
    public static final String EXECUTE_WORKFLOW_SERVICE_DEFINITION = "execute-workflow";
    public static final String EXECUTE_WORKFLOW_URI = "/execute";
    public static final String REQUEST_OBJECT_KEY_WORKFLOW = "request-object";
    public static final String REQUEST_OBJECT_WORKFLOW = "workflow";
    
    

    
    
    /**
     * Do not create an instance of a class used to hold constants
     */
    private WManagerConstants() {
        throw new UnsupportedOperationException();
    }

}
