package se.ltu.workflow.manager;

import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import se.arkalix.ArServiceCache;
import se.arkalix.ArSystem;
import se.arkalix.core.plugin.HttpJsonCloudPlugin;
import se.arkalix.core.plugin.or.OrchestrationStrategy;
import se.arkalix.description.ServiceDescription;
import se.arkalix.descriptor.EncodingDescriptor;
import se.arkalix.descriptor.TransportDescriptor;
import se.arkalix.net.http.HttpMethod;
import se.arkalix.net.http.HttpStatus;
import se.arkalix.net.http.client.HttpClient;
import se.arkalix.net.http.client.HttpClientRequest;
import se.arkalix.net.http.consumer.HttpConsumer;
import se.arkalix.net.http.consumer.HttpConsumerRequest;
import se.arkalix.net.http.service.HttpService;
import se.arkalix.query.ServiceNotFoundException;
import se.arkalix.security.access.AccessPolicy;
import se.arkalix.security.identity.OwnedIdentity;
import se.arkalix.security.identity.TrustStore;
import se.arkalix.util.concurrent.Future;
import se.arkalix.util.concurrent.Schedulers;
import se.ltu.workflow.manager.dto.Workflow;
import se.ltu.workflow.manager.dto.WorkflowBuilder;
import se.ltu.workflow.manager.dto.WorkflowDto;
import se.ltu.workflow.manager.properties.TypeSafeProperties;

public class WManagerMain {
    
    private static final Map<Workflow, ServiceDescription> WorkflowToExecutor = new ConcurrentHashMap<>();
    private static final Set<String> validProducts = new HashSet<>();
    
    private static final Logger logger = LoggerFactory.getLogger(WManagerMain.class);
    
    private static final TypeSafeProperties props = TypeSafeProperties.getProp();
    
    static {
        final var logLevel = Level.INFO;
        System.setProperty("java.util.logging.SimpleFormatter.format", "%1$tF %1$tT %4$s %5$s%6$s%n");
        final var root = java.util.logging.Logger.getLogger("");
        root.setLevel(logLevel);
        for (final var handler : root.getHandlers()) {
            handler.setLevel(logLevel);
        }
    }

    public static void main( String[] args )
    {
        logger.info("Productive 4.0 Workflow Manager Demonstrator - Workflow Manager System");
        
        // Working directory should always contain a properties file and certificates!
        System.out.println("Working directory: " + System.getProperty("user.dir"));

        try {
            // Retrieve properties to set up keystore and truststore
            // The paths must start at the working directory
            final char[] pKeyPassword = props.getProperty("server.ssl.key-password", "123456")
                    .toCharArray();
            final char[] kStorePassword = props.getProperty("server.ssl.key-store-password", "123456").
                    toCharArray();
            final String kStorePath = props.getProperty("server.ssl.key-store", "certificates/workflow_manager.p12");
            final char[] tStorePassword = props.getProperty("server.ssl.trust-store-password", "123456")
                    .toCharArray();
            final String tStorePath = props.getProperty("server.ssl.trust-store", "certificates/truststore.p12");
            
            // Load properties for system identity and truststore
            final var identity = new OwnedIdentity.Loader()
                    .keyStorePath(kStorePath)
                    .keyStorePassword(kStorePassword)
                    .keyPassword(pKeyPassword)
                    .load();
            final var trustStore = TrustStore.read(tStorePath, tStorePassword);
            
            /* Remove variables storing passwords, as they are final they can not be unreferenced and 
             * will not be garbage collected
             */
            Arrays.fill(pKeyPassword, 'x');
            Arrays.fill(kStorePassword, 'x');
            Arrays.fill(tStorePassword, 'x');
            
            /* Create client to send HTTPRequest, but only to non Arrowhead services! Is recommended to
             * use HttpConsumer when dealing with Arrowhead services
             */
            final var client = new HttpClient.Builder()
                    .identity(identity)
                    .trustStore(trustStore)
                    .build();
            
            /* Check that the core systems are available - This call is synchronous, as 
             * initialization should not continue if they are not succesfull
             */
            checkCoreSystems(client,2);

            //TODO: Check that there are no more Workflow Managers in the workstation
            
            // Obtain the product names from config
            final String productsConfig = props.getProperty("workflow_products", "product-1,product-2");
            Arrays.stream(productsConfig.split(",")).forEach(productName -> {
                var cleanProductName = productName.trim();
                validProducts.add(cleanProductName);
            });
            logger.info("The pre-loaded products ID (in random order) are: " + validProducts);
            
            // Retrieve Workflow Manager properties to create Arrowhead system
            final String systemAddress = props.getProperty("server.address", "127.0.0.1");
            final int systemPort = props.getIntProperty("server.port", 8502);
            final var systemSocketAddress = new InetSocketAddress(systemAddress, systemPort);
            
            // Retrieve Service Registry properties to register Arrowhead system
            final String serviceRegistryAddres = props.getProperty("sr_address","127.0.0.1");
            final int serviceRegistryPort = props.getIntProperty("sr_port", 8443);
            // TODO: In demo we can use "service-registry.uni" as hostname of Service Registry?
            final var srSocketAddress = new InetSocketAddress(serviceRegistryAddres, serviceRegistryPort);
            
            // Create Arrowhead system
            final var system = new ArSystem.Builder()
                    .identity(identity)
                    .trustStore(trustStore)
                    .localSocketAddress(systemSocketAddress)
                    .plugins(new HttpJsonCloudPlugin.Builder()
                            .serviceRegistrySocketAddress(srSocketAddress)
                            .orchestrationStrategy(OrchestrationStrategy.STORED_THEN_DYNAMIC)
                            .serviceRegistrationPredicate(service -> service.interfaces()
                                    .stream()
                                    .allMatch(i -> i.encoding().isDtoEncoding()))
                            .build())
                    .serviceCache(ArServiceCache.withEntryLifetimeLimit(Duration.ofHours(1)))
                    .build();
            
            // Add Echo HTTP Service to Arrowhead system
            system.provide(new HttpService()
                    // Mandatory service configuration details.
                    .name(WManagerConstants.WMANAGER_TOOLS_SERVICE_DEFINITION)
                    .encodings(EncodingDescriptor.getOrCreate("plain"))
                    // Could I have another AccessPolicy for any consumers? as this service will not be registered
                    .accessPolicy(AccessPolicy.cloud())
                    .basePath(WManagerConstants.WMANAGER_URI)
                    
                    // ECHO service
                    .get(WManagerConstants.ECHO_URI,(request, response) -> {
                        logger.info("Receiving echo request");
                        response
                            .status(HttpStatus.OK)
                            .header("content-type", "text/plain;charset=UTF-8")
                            .header("cache-control", "no-cache, no-store, max-age=0, must-revalidate")
                            .body("Got it!");
                        
                        return Future.done();
                    }).metadata(Map.ofEntries(Map.entry("http-method","GET")))
            
                     // HTTP DELETE endpoint that causes the application to exit.
                    .delete(WManagerConstants.SHUTDOWN_URI, (request, response) -> {
                        response.status(HttpStatus.NO_CONTENT);
                        
                        //Shutdown server and unregisters (dismiss) the services using the HttpJsonCloudPlugin
                        system.shutdown();
        
                        // Exit in 0.5 seconds.
                        Schedulers.fixed()
                            .schedule(Duration.ofMillis(500), () -> System.exit(0))
                            .onFailure(Throwable::printStackTrace);
        
                        return Future.done();
                    }))
                    .ifSuccess(handle -> logger.info("Workflow Manager "
                            + WManagerConstants.WMANAGER_TOOLS_SERVICE_DEFINITION + " service is now being served"))
                    .ifFailure(Throwable.class, Throwable::printStackTrace)
                    // Without await service is not sucessfully registered
                    .await();
            
            // Add Workflows HTTP Service to Workflow Manager system
            system.provide(new HttpService()
                    // Mandatory service configuration details.
                    .name(WManagerConstants.WORKSTATION_OPERATIONS_SERVICE_DEFINITION)
                    .encodings(EncodingDescriptor.JSON)
                    // Could I have another AccessPolicy for intercloud consumers?
                    .accessPolicy(AccessPolicy.cloud())
                    .basePath(WManagerConstants.WMANAGER_URI + WManagerConstants.WORKSTATION_OPERATIONS_URI)
                    
                    // HTTP GET endpoint that returns the workflows available in this workstation
                    .get("/", (request, response) -> {
                        
                        logger.info("Receiving GET "
                                + WManagerConstants.WMANAGER_URI
                                + WManagerConstants.WORKSTATION_OPERATIONS_URI + " request");
                        
                        List<ServiceDescription> Wexecutors = new ArrayList<>();
                        
                        // Retrieve the workflows from the Workflow Executors
                        system.consume()
                            .name(WManagerConstants.PROVIDE_AVAILABLE_WORKFLOW_SERVICE_DEFINITION)
                            .encodings(EncodingDescriptor.JSON)
                            .transports(TransportDescriptor.HTTP)
//                            .resolveAll() // How to find more than one Workflow Executor? Wait for library update
                            .using(HttpConsumer.factory())
                            .flatMap(consumer -> {
                                // If in the future there would be multiple responses we would need to change this
                                // Store the Wexecutor providing the service until we get the response
                                Wexecutors.add(consumer.service());
                                // Consume the service to obtain the workflows
                                return consumer.send(new HttpConsumerRequest()
                                    .method(HttpMethod.GET)
                                    .uri(WManagerConstants.WEXECUTOR_URI
                                            + WManagerConstants.PROVIDE_AVAILABLE_WORKFLOW_URI));
                                }
                            )
                            .flatMap(responseConsumer -> responseConsumer.bodyAsList(WorkflowDto.class))
                            .ifSuccess(workflows -> {
                                workflows.forEach(workflow -> {
                                    ServiceDescription Wexecutor = Wexecutors.get(0);
                                    WorkflowToExecutor.put(workflow, Wexecutor);
                                    logger.info("Storing internally Worklfow: " + workflow.workflowName()
                                        +" from " + Wexecutor.provider().name()
                                        + " at " + Wexecutor.provider().socketAddress());
                                });
                                response
                                .status(HttpStatus.OK)
                                // Why workflows gives compilation error? is not a List<DtoWritable>?
                                .body(List.copyOf(workflows));
                            })
                            .flatMapCatch(ServiceNotFoundException.class, exception -> {
                                logger.error("No workflow-executor system offering services found in this local cloud,"
                                        + " therefore this service fails to execute");
                                response.status(HttpStatus.SERVICE_UNAVAILABLE);
                                return Future.done();
                            })
                            .ifFailure(Throwable.class, throwable -> {
                                logger.error("GET to " 
                                        + WManagerConstants.WEXECUTOR_URI
                                        + WManagerConstants.PROVIDE_AVAILABLE_WORKFLOW_URI + " failed");
                                throwable.printStackTrace();
                                response.status(HttpStatus.INTERNAL_SERVER_ERROR);
                            }).await(); 
                        return Future.done();
                    }).metadata(Map.ofEntries(Map.entry("http-method","GET")))
                    
                    .post("/", (request, response) -> {
                        
                        logger.info("Receiving POST "
                                + WManagerConstants.WMANAGER_URI
                                + WManagerConstants.WORKSTATION_OPERATIONS_URI + " request");
                        
                        //Check that consumer is a Smart Product authorized in the Factory
                        String consumerCertName = request
                                .consumer()
                                .identity()
                                .certificate()
                                .getSubjectX500Principal()
                                .getName();
                        
                        // Future release will have a call to a factory system (MES) service in charge of planning
                        if (validProducts.contains(consumerCertName)){
                            // If productID is correct, proceed to check input
                            request
                                .bodyAs(WorkflowDto.class)
                                .flatMapCatch(ClassCastException.class, exception -> {
                                    response.status(HttpStatus.BAD_REQUEST);
                                    return Future.failure(exception);
                                })
                                .flatMap(workflow -> executeWorkflow(workflow, system))
                                .flatMapCatch(ServiceNotFoundException.class, exception -> {
                                    response.status(HttpStatus.SERVICE_UNAVAILABLE);
                                    return Future.done();
                                })
                                .ifSuccess(workflow -> {
                                    response
                                    .status(HttpStatus.OK);
                                })
                                
                                .ifFailure(Throwable.class, throwable -> 
                                    logger.info("Wrong request (input) to service, mut be a valid workflow"))
                                .await();
                            
                        }
                        else {
                            response
                            .status(HttpStatus.UNAUTHORIZED);
                        }

                        return Future.done();
                    }).metadata((Map.ofEntries(
                            Map.entry("http-method","POST"),
                            Map.entry("request-object-POST","workflow"))))
                )
                .ifSuccess(handle -> logger.info("Workflow Manager "
                        + WManagerConstants.WORKSTATION_OPERATIONS_SERVICE_DEFINITION
                        + " service is now being served"))
                .ifFailure(Throwable.class, Throwable::printStackTrace)
                .await();
            
        } catch (Exception e) {
            e.printStackTrace();
        }
        
    }
    
    private static void checkCoreSystems(HttpClient  client, int minutes)
            throws InterruptedException, TimeoutException {
        
        TimeCount timer = new TimeCount(Duration.ofMinutes(minutes));
        
        // Service Registry
        final String serviceRegistryAddres = props.getProperty("sr_address","127.0.0.1");
        final int serviceRegistryPort = props.getIntProperty("sr_port", 8443);
        final var serviceRegistrySocketAddress = new InetSocketAddress(serviceRegistryAddres, serviceRegistryPort);
        logger.info("Testing connection with Service Registry");
        
        // Send GET request to echo service until reply or timeout
        while(!timer.timeout()) {
            try {
                // Future throws exception (java.net.ConnectException) when server not available
                client.send(serviceRegistrySocketAddress, new HttpClientRequest()
                    .method(HttpMethod.GET)
                    .uri("serviceregistry/echo"))
                    .flatMap(response -> response.bodyAsString())
                    .ifSuccess(response -> {
                        // If exception is not thrown, request was successful so end loop
                        timer.endCount();
                        if (!response.isEmpty()) {
                            logger.info("Service Registry replied, core system is reachable");
                        }
                        else{
                            logger.warn("Service Registry core system was reached, but Echo message was empty!");
                        }
                    })
                    .flatMapCatch(ConnectException.class, exception -> {
                        int retryPeriodSeconds = 5;
                        if(timer.discountAndWait(Duration.ofSeconds(retryPeriodSeconds))) {
                            logger.info("Service Registry core system is not reachable, retry in " 
                                    + retryPeriodSeconds + " seconds");
                            return Future.done();
                        }
                        else {
                            logger.error("Service Registry mandatory core system is not reachable, "
                                    + "Arrowhead local cloud incomplete");
                            var e = new TimeoutException();
                            e.addSuppressed(exception);
                            throw e;
                        }
                    })
                    .await();
            }
            catch (InterruptedException e) {
                logger.error("Workflow Manager interrupted when waiting for Service Regsitry echo message");
                throw e;
            }
        }

        // Orchestrator
        final String OrchestratorAddres = props.getProperty("orch_address","127.0.0.1");
        final int OrchestratorPort = props.getIntProperty("orch_port", 8441);
        final var OrchestratorAddress = new InetSocketAddress(OrchestratorAddres, OrchestratorPort);
        logger.info("Testing connection with Orchestrator");
        
        timer.resetCount(Duration.ofMinutes(minutes));
        while(!timer.timeout()) {
            try {
                client.send(OrchestratorAddress, new HttpClientRequest()
                    .method(HttpMethod.GET)
                    .uri("orchestrator/echo"))
                    .flatMap(response -> response.bodyAsString())
                    .ifSuccess(response -> {
                        timer.endCount();
                        if (!response.isEmpty()) {
                            logger.info("Orchestrator replied, core system is reachable");
                        }
                        else{
                            logger.warn("Orchestrator core system was reached, but Echo message was empty!");
                        }
                    })
                    .flatMapCatch(ConnectException.class, exception -> {
                        int retryPeriodSeconds = 5;
                        if(timer.discountAndWait(Duration.ofSeconds(retryPeriodSeconds))) {
                            logger.info("Orchestrator core system is not reachable, retry in " 
                                    + retryPeriodSeconds + " seconds");
                            return Future.done();
                        }
                        else {
                            logger.error("Orchestrator mandatory core system is not reachable, "
                                    + "Arrowhead local cloud incomplete");
                            var e = new TimeoutException();
                            e.addSuppressed(exception);
                            throw e;
                        }
                    })
                    .await();
            }
            catch (InterruptedException e) {
                logger.error("Workflow Manager interrupted when waiting for Orchestrator echo message");
                throw e;
            }
        }
        
        // Authorization - In this case we could obtain the address and port from Orchestrator also
        if (props.getBooleanProperty("server.ssl.enabled", false)) {
            final String AuthorizationAddres = props.getProperty("auth_address","127.0.0.1");
            final int AuthorizationPort = props.getIntProperty("auth_port", 8445);
            final var AuthorizationSocketAddress = new InetSocketAddress(AuthorizationAddres, AuthorizationPort);
            logger.info("Testing connection with Authorization");
            
            timer.resetCount(Duration.ofMinutes(minutes));
            while(!timer.timeout()) {
                try {
                    client.send(AuthorizationSocketAddress, new HttpClientRequest()
                        .method(HttpMethod.GET)
                        .uri("authorization/echo"))
                        .flatMap(response -> response.bodyAsString())
                        .ifSuccess(response -> {
                            timer.endCount();
                            if (!response.isEmpty()) {
                                logger.info("Authorization replied, core system is reachable");
                            }
                            else{
                                logger.warn("Authorization core system was reached, but Echo message was empty!");
                            }
                        })
                        .flatMapCatch(ConnectException.class, exception -> {
                            int retryPeriodSeconds = 5;
                            if(timer.discountAndWait(Duration.ofSeconds(retryPeriodSeconds))) {
                                logger.info("Authorization core system is not reachable, retry in " 
                                        + retryPeriodSeconds + " seconds");
                                return Future.done();
                            }
                            else {
                                logger.error("Authorization mandatory core system is not reachable, "
                                        + "Arrowhead local cloud incomplete");
                                var e = new TimeoutException();
                                e.addSuppressed(exception);
                                throw e;
                            }
                        })
                        .await();
                }
                catch (InterruptedException e) {
                    logger.error("Workflow Manager interrupted when waiting for Authorization echo message");
                    throw e;
                }
            }
        }
    }
    
    private static Future<?> executeWorkflow(Workflow w, ArSystem system ) throws ServiceNotFoundException{
        // If workflow already in memory, proceed to call the Wexecutor with the input data
        if (WorkflowToExecutor.containsKey(w)) {
            final var query = system.consume()
                .name(WManagerConstants.EXECUTE_WORKFLOW_SERVICE_DEFINITION)
                .encodings(EncodingDescriptor.JSON)
                .transports(TransportDescriptor.HTTP);
            return query.resolveAll()
                .flatMap(services -> {
                    var service = services.stream()
                        // We already know which system provides the workflow service we want to consume
                        .filter(serviceFound -> serviceFound.provider().socketAddress()
                                .equals(WorkflowToExecutor.get(w).provider().socketAddress()))
                        .findFirst();
                    if(service.isPresent()){
                      return Future.success(HttpConsumer.factory()
                              .create(system, service.get(), List.of(EncodingDescriptor.JSON)));
                    }
                    else {
                          logger.error("Workflow Executor system providing workflow \"" + w.workflowName()
                                  + "\" is missing in local cloud");
                          // Remove mappings of WorkflowExecutor in memory (WorkflowToExecutor) if system is off
                          WorkflowToExecutor
                              .entrySet()
                              .removeIf(entry -> entry.getValue().equals(w));
                          return Future.failure(new ServiceNotFoundException(query));
                    }                
                })
                .ifSuccess(consumer -> consumer.send(new HttpConsumerRequest()
                        .method(HttpMethod.GET)
                        // The URI should come from the orchestrator
                        .uri(consumer.service().uri())
    //                            WManagerConstants.WEXECUTOR_URI
    //                            + WManagerConstants.EXECUTE_WORKFLOW_URI));
                        .body(new WorkflowBuilder()
                                .workflowName(w.workflowName())
                                .workflowConfig(w.workflowConfig())
                                .build()))
                );
        }
        // Workflow unknown, call the GET workflow service provided
        else {
            /* TODO: Trigger the get("/"... to search if there are new Workflow Executors that
             * offer the workflow
             */
            
            return Future.done();
        }
    }
    
    private static class TimeCount {
        private long timeLeftInCount;
        
        public TimeCount(Duration minutes) {
            if(minutes.getSeconds() < 60) {
                throw new IllegalArgumentException("The minimun amount of time accepted is 1 minute (60 seconds)");
            }
            timeLeftInCount = minutes.getSeconds();
        }
        
        public void resetCount(Duration minutes) {
            if(minutes.getSeconds() < 60) {
                throw new IllegalArgumentException("The minimun amount of time accepted is 1 minute (60 seconds)");
            }
            timeLeftInCount = minutes.getSeconds();
        }
        
        /**
         * Discount/subtract an amount of time (greater than a second) from this TimeCount, and sleep the thread
         * for that same amount 
         * 
         * @param seconds time to be subtracted from the internal time count and keep thread slept
         * @return true when there is still time in the count after the operation of subtraction
         * @throws InterruptedException if thread interrupted while
         */
        public Boolean discountAndWait(Duration seconds) throws InterruptedException {
            timeLeftInCount -= seconds.getSeconds();
            try {
                Thread.sleep(seconds.getSeconds() * 1000);
            } catch (InterruptedException e) {
                throw e;
            }
            return timeLeftInCount > 0;
        }
        
        /**
         * Checks if the initial time entered has been used
         * 
         * @return true when the time left is less or equal than zero
         */
        public Boolean timeout() {
            return timeLeftInCount <= 0;
        }
        
        public void endCount() {
            timeLeftInCount = 0;
        }
    }

}
