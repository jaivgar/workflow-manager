package se.ltu.workflow.manager;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import se.arkalix.ArServiceCache;
import se.arkalix.ArSystem;
import se.arkalix.core.plugin.HttpJsonCloudPlugin;
import se.arkalix.descriptor.EncodingDescriptor;
import se.arkalix.dto.DtoEncoding;
import se.arkalix.dto.DtoWritable;
import se.arkalix.net.http.HttpMethod;
import se.arkalix.net.http.HttpStatus;
import se.arkalix.net.http.client.HttpClient;
import se.arkalix.net.http.client.HttpClientRequest;
import se.arkalix.net.http.consumer.HttpConsumer;
import se.arkalix.net.http.consumer.HttpConsumerRequest;
import se.arkalix.net.http.service.HttpService;
import se.arkalix.security.access.AccessPolicy;
import se.arkalix.security.identity.OwnedIdentity;
import se.arkalix.security.identity.TrustStore;
import se.arkalix.util.concurrent.Future;
import se.arkalix.util.concurrent.Schedulers;
import se.ltu.workflow.manager.dto.WorkflowDto;
import se.ltu.workflow.manager.properties.TypeSafeProperties;

public class WManagerMain {
    
    private static final Logger logger = LoggerFactory.getLogger(WManagerMain.class);
    
    private static final TypeSafeProperties props = TypeSafeProperties.getProp();
    
    static {
        final var logLevel = Level.INFO;
        //System.setProperty("java.util.logging.SimpleFormatter.format", "%1$tF %1$tT %4$s %5$s%6$s%n");
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
            final char[] pKeyPassword = props.getProperty("server.ssl.key-password", "123456").toCharArray();
            final char[] kStorePassword = props.getProperty("server.ssl.key-store-password", "123456").toCharArray();
            final String kStorePath = props.getProperty("server.ssl.key-store", "certificates/workflow_manager.p12");
            final char[] tStorePassword = props.getProperty("server.ssl.trust-store-password", "123456").toCharArray();
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
            
            /* Check that the core systems are available - We want this call synchronous, as 
             * initialization should not continue if they are not succesfull
             */
            checkCoreSystems(client,2);

            //TODO: Check that there are no more Workflow Managers in the workstation
            final String systemAddress = props.getProperty("server.address", "127.0.0.1");
            final int systemPort = props.getIntProperty("server.port", 8502);
            final var systemSocketAddress = new InetSocketAddress(systemAddress, systemPort);
            
            final String serviceRegistryAddres = props.getProperty("sr_address","127.0.0.1");
            final int serviceRegistryPort = props.getIntProperty("sr_port", 8443);
            // In demo we can use "service-registry.uni" as hostname of Service Registry?
            final var srSocketAddress = new InetSocketAddress(serviceRegistryAddres, serviceRegistryPort);
            
            // Create Arrowhead system
            final var system = new ArSystem.Builder()
                    .identity(identity)
                    .trustStore(trustStore)
                    .localSocketAddress(systemSocketAddress)
                    .plugins(HttpJsonCloudPlugin.joinViaServiceRegistryAt(srSocketAddress))
                    .serviceCache(ArServiceCache.withEntryLifetimeLimit(Duration.ofHours(1)))
                    .build();
            
            // Add Echo HTTP Service to Arrowhead system
            system.provide(new HttpService()
                    // Mandatory service configuration details.
                    .name(WManagerConstants.WMANAGER_ECHO_SERVICE_DEFINITION)
                    .encodings(EncodingDescriptor.JSON)
                    // Could I have another AccessPolicy for any consumers?
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
                    .delete("/runtime", (request, response) -> {
                        response.status(HttpStatus.NO_CONTENT);
                        
                        //Shutdown server and unregisters (dismiss) the services using the HttpJsonCloudPlugin
                        system.shutdown();
        
                        // Exit in 0.5 seconds.
                        Schedulers.fixed()
                            .schedule(Duration.ofMillis(500), () -> System.exit(0))
                            .onFailure(Throwable::printStackTrace);
        
                        return Future.done();
                    }))
            
                    .ifSuccess(handle -> logger.info("Workflow Manager " + WManagerConstants.WMANAGER_ECHO_SERVICE_DEFINITION 
                            + " service is now being served"))
                    .ifFailure(Throwable.class, Throwable::printStackTrace)
                    // Without await service is not sucessfully registered
                    .await();
            
            // Add Workflows HTTP Service to Workflow Manager system
            system.provide(new HttpService()
                    // Mandatory service configuration details.
                    .name(WManagerConstants.WORKSTATION_WORKFLOW_SERVICE_DEFINITION)
                    .encodings(EncodingDescriptor.JSON)
                    // Could I have another AccessPolicy for intercloud consumers?
                    .accessPolicy(AccessPolicy.cloud())
                    .basePath(WManagerConstants.WMANAGER_URI + WManagerConstants.WORKSTATION_WORKFLOW_URI)
                    
                    // HTTP GET endpoint that returns the workflows available in this workstation
                    .get("/", (request, response) -> {
                        
                        logger.info("Receiving GET " + WManagerConstants.WMANAGER_URI + WManagerConstants.WORKSTATION_WORKFLOW_URI 
                                + " request");
                        
                        List<WorkflowDto> workflows = new ArrayList<>();
                        
                        // Retrieve the workflows from the Workflow Executors
                        // How to find more than one Workflow Executor?
                        system.consume()
                            .name(WManagerConstants.PROVIDE_AVAILABLE_WORKFLOW_SERVICE_DEFINITION)
                            .encodings(EncodingDescriptor.JSON)
                            .using(HttpConsumer.factory())
                            .flatMap(consumer -> consumer.send(new HttpConsumerRequest()
                                .method(HttpMethod.GET)
                                .uri(WManagerConstants.WEXECUTOR_URI + WManagerConstants.PROVIDE_AVAILABLE_WORKFLOW_URI)))
                            .flatMap(responseConsume -> responseConsume.bodyAsList(WorkflowDto.class))
                            .ifSuccess(workflow -> {
                                workflows.addAll(workflow);
                            })
                            .ifFailure(Throwable.class, throwable -> {
                                // Exception as ServiceNotFound are handled here, giving the log errors and moving forward
                                logger.error("GET to " 
                                        + WManagerConstants.WEXECUTOR_URI + WManagerConstants.PROVIDE_AVAILABLE_WORKFLOW_URI
                                        + " failed");
                                throwable.printStackTrace();
                            }).await(); 
                            
                        // Add all the workflows retrieved to the response
                        if(!workflows.isEmpty()) {
                            response
                                .status(HttpStatus.OK)
                                .body((DtoWritable) workflows);
                        }
                        else {
                            response
                                .status(HttpStatus.NO_CONTENT);
                        }

                        return Future.done();
                    }).metadata(Map.ofEntries(Map.entry("http-method","GET-POST"),Map.entry("request-object-POST","workflow")))
                    
                    .post("/", (request, response) -> {
                        
                        logger.info("Receiving POST " + WManagerConstants.WMANAGER_URI + WManagerConstants.WORKSTATION_WORKFLOW_URI 
                                + " request");
                        
                        //TODO: Check that consumer is a Smart Product authorized in the Factory
                        request.consumer().identity().certificate();
                        
                        // Dummy response
                        return Future.done();
                    }))
                    .ifSuccess(handle -> logger.info("Workflow Manager " + WManagerConstants.WORKSTATION_WORKFLOW_SERVICE_DEFINITION 
                            + " service is now being served"))
                    .ifFailure(Throwable.class, Throwable::printStackTrace)
                    .await();
            
        } catch (Exception e) {
            e.printStackTrace();
        }
        
    }
    
    private static void checkCoreSystems(HttpClient  client, int minutes) throws InterruptedException, TimeoutException {

        // Service Registry
        final String serviceRegistryAddres = props.getProperty("sr_address","127.0.0.1");
        final int serviceRegistryPort = props.getIntProperty("sr_port", 8443);
        final var serviceRegistrySocketAddress = new InetSocketAddress(serviceRegistryAddres, serviceRegistryPort);
        int nCalls = 0;
        // 5 seconds * 12 = 1 minute
        while(nCalls/12 < minutes) {
            // Send GET request to echo service
            try {
                /* TODO: Throws Exception in spawn thread when request does not find target, exiting application (How can I catch it?)
                 * io.netty.channel.AbstractChannel$AnnotatedConnectException: finishConnect(..) failed: Connection refused: /127.0.0.1:8443
                 */
                String result = client.send(serviceRegistrySocketAddress, new HttpClientRequest()
                        .method(HttpMethod.GET)
                        .uri("serviceregistry/echo"))
                        .flatMap(response -> response.bodyAsString())
                        .await(Duration.ofSeconds(5));
                // If exception is not thrown, request was successful so end loop
                nCalls = Integer.MAX_VALUE;
                if (!result.isEmpty()) {
                    logger.info("Service Registry core system is reachable.");
                }
                else{
                    logger.warn("Service Registry core system was reached, but Echo message was empty!");
                }
            }
            catch (InterruptedException e) {
                logger.error("Workflow Manager interrupted when waiting for Service Regsitry echo message");
                throw e;
            }
            catch (TimeoutException e) {
                nCalls ++;
                if(nCalls/12 >= minutes) {
                    logger.error("Service Registry mandatory core system is not reachable, Arrowhead local cloud incomplete!");
                    throw e;
                }
                logger.info("Waiting for ServiceRegistry to be available ...");
            }
        }

        // Orchestrator
        final String OrchestratorAddres = props.getProperty("orch_address","127.0.0.1");
        final int OrchestratorPort = props.getIntProperty("orch_port", 8441);
        final var OrchestratorAddress = new InetSocketAddress(OrchestratorAddres, OrchestratorPort);
        nCalls = 0;
        
        while(nCalls/12 < minutes) {
         // Send GET request to echo service
            try {
                String result = client.send(OrchestratorAddress, new HttpClientRequest()
                        .method(HttpMethod.GET)
                        .uri("orchestrator/echo"))
                        .flatMap(response -> response.bodyAsString())
                        .await(Duration.ofSeconds(5));
                nCalls = Integer.MAX_VALUE;
                if (!result.isEmpty()) {
                    logger.info("Orchestrator core system is reachable.");
                }
                else{
                    logger.warn("Orchestrator core system was reached, but Echo message was empty!");
                }
            }
            catch (InterruptedException e) {
                logger.error("Workflow Manager interrupted when waiting for Orchestrator echo message");
                throw e;
            }
            catch (TimeoutException e) {
                nCalls ++;
                if(nCalls/12 >= minutes) {
                    logger.error("Orchestrator mandatory core system is not reachable, Arrowhead local cloud incomplete!");
                    throw e;
                }
                logger.info("Waiting for Orchestrator to be available ...");
            }
        }
        
        if (props.getBooleanProperty("server.ssl.enabled", false)) {
            // Authorization - In this case we could obtain the address and port from Orchestrator also
            final String AuthorizationAddres = props.getProperty("auth_address","127.0.0.1");
            final int AuthorizationPort = props.getIntProperty("auth_port", 8445);
            final var AuthorizationSocketAddress = new InetSocketAddress(AuthorizationAddres, AuthorizationPort);
            nCalls = 0;
            
            while(nCalls/12 < minutes) {
             // Send GET request to echo service
                try {
                    String result = client.send(AuthorizationSocketAddress, new HttpClientRequest()
                            .method(HttpMethod.GET)
                            .uri("authorization/echo"))
                            .flatMap(response -> response.bodyAsString())
                            .await(Duration.ofSeconds(5));
                    nCalls = Integer.MAX_VALUE;
                    if (!result.isEmpty()) {
                        logger.info("Authorization core system is reachable.");
                    }
                    else{
                        logger.warn("Authorization core system was reached, but Echo message was empty!");
                    }
                }
                catch (InterruptedException e) {
                    logger.error("Workflow Manager interrupted when waiting for Authorization echo message");
                    throw e;
                }
                catch (TimeoutException e) {
                    nCalls ++;
                    if(nCalls/12 >= minutes) {
                        logger.error("Authorization mandatory core system is not reachable, Arrowhead local cloud incomplete!");
                        throw e;
                    }
                    logger.info("Waiting for Authorization to be available ...");
                    
                    throw e;
                }
            }
            
        }

    }
}
