package se.ltu.workflow.manager;

import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import se.arkalix.ArSystem;
import se.arkalix.descriptor.EncodingDescriptor;
import se.arkalix.dto.DtoEncoding;
import se.arkalix.net.http.HttpMethod;
import se.arkalix.net.http.HttpStatus;
import se.arkalix.net.http.client.HttpClient;
import se.arkalix.net.http.client.HttpClientRequest;
import se.arkalix.net.http.service.HttpService;
import se.arkalix.security.access.AccessPolicy;
import se.arkalix.security.identity.OwnedIdentity;
import se.arkalix.security.identity.TrustStore;
import se.arkalix.util.concurrent.Future;
import se.arkalix.util.concurrent.Schedulers;
import se.ltu.workflow.manager.properties.TypeSafeProperties;

public class WManagerMain {
    
    private static final Logger logger = LoggerFactory.getLogger(WManagerMain.class);
    
    private static TypeSafeProperties props = TypeSafeProperties.getProp();

    public static void main( String[] args )
    {
        logger.info("Productive 4.0 Workflow Manager Demonstrator - Workflow Manager System");
        
        // Working directory should always contain a properties file!
        System.out.println("Working directory: " + System.getProperty("user.dir"));

        try {
            
            // Retrieve properties to set up keystore and truststore
            final char[] pKeyPassword = props.getProperty("server.ssl.key-password", "123456").toCharArray();
            final char[] kStorePassword = props.getProperty("server.ssl.key-store-password", "123456").toCharArray();
            final String kStorePath = props.getProperty("server.ssl.key-store", "certificates/workflow_manager.p12");
            final char[] tStorePassword = props.getProperty("server.ssl.trust-store-password", "123456").toCharArray();
            final String tStorePath = props.getProperty("server.ssl.trust-store", "certificates/truststore.p12");
            
            // Not working
//            Path kPath = Path.of(kStorePath);
//            System.out.println("Path: "+ kPath +" works? " + kPath.toFile().isFile());
            
//            Path kPath = Paths.get(WManagerMain.class.getResource(kStorePath).toURI());
//            System.out.println("Path: "+ kPath +" works? " + kPath.toFile().isFile());
            
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
            
            final int systemPort = props.getIntProperty("server.port", 8502);
            
            // Create Arrowhead system
            final var system = new ArSystem.Builder()
                    .identity(identity)
                    .trustStore(trustStore)
                    .localPort(systemPort)
                    .build();
            
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
            checkCoreSystems(client);

            //TODO: Check that there are no more Workflow Managers in the workstation
            
            // Add HTTP Service to Arrowhead system
            system.provide(new HttpService()

                    // Mandatory service configuration details.
                    .name("WManager-workflows")
                    .encodings(EncodingDescriptor.JSON)
                    .accessPolicy(AccessPolicy.cloud())
                    .basePath("/workflows")
                    
                    // HTTP GET endpoint that exposes the workflows available in this workstation
                    .get("", (request, response) -> {
                        
                        // Return the list of workflows available by looking at the Workflow Executors
                        system.consume()
                        .encodings(EncodingDescriptor.JSON);
                        
                        // Dummy response
                        response
                        .status(HttpStatus.OK)
                        .header("content-type", "text/html")
                        .header("cache-control", "no-store");
                        
                        return Future.done();
                    })
                    
                    .post("", (request, response) -> {
                        //TODO: Check that consumer is a Smart Product authorized in the Factory
                        request.consumer().identity().certificate();
                        
                        // Dummy response
                        return Future.done();
                    })
                    
                    // HTTP DELETE endpoint that causes the application to exit.
                    .delete("/runtime", (request, response) -> {
                        response.status(HttpStatus.NO_CONTENT);

                        // Exit in 0.5 seconds.
                        Schedulers.fixed()
                            .schedule(Duration.ofMillis(500), () -> System.exit(0))
                            .onFailure(Throwable::printStackTrace);

                        return Future.done();
                    }))
                    
                    .onFailure(Throwable::printStackTrace);
            
        } catch (Exception e) {
            e.printStackTrace();
        }
        
    }
    
    private static void checkCoreSystems(HttpClient  client) throws InterruptedException, TimeoutException {

        // Service Registry
        final String serviceRegistryAddres = props.getProperty("sr_address","127.0.0.1");
        final int serviceRegistryPort = props.getIntProperty("sr_port", 8443);
        final var serviceRegistrySocketAddress = new InetSocketAddress(serviceRegistryAddres, serviceRegistryPort);
        // Send GET request to echo service
        try {
            String result = client.send(serviceRegistrySocketAddress, new HttpClientRequest()
                    .method(HttpMethod.GET)
                    .uri("serviceregistry/echo"))
                    .flatMap(response -> response.bodyAsString())
                    .await(Duration.ofSeconds(5));
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
            logger.error("Service Registry mandatory core system is not reachable, Arrowhead local cloud incomplete!");
            throw e;
        }

        // Orchestrator
        final String OrchestratorAddres = props.getProperty("orch_address","127.0.0.1");
        final int OrchestratorPort = props.getIntProperty("orch_port", 8441);
        final var OrchestratorAddress = new InetSocketAddress(OrchestratorAddres, OrchestratorPort);
        // Send GET request to echo service
        try {
            String result = client.send(OrchestratorAddress, new HttpClientRequest()
                    .method(HttpMethod.GET)
                    .uri("orchestrator/echo"))
                    .flatMap(response -> response.bodyAsString())
                    .await(Duration.ofSeconds(5));
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
            logger.error("Orchestrator mandatory core system is not reachable, Arrowhead local cloud incomplete!");
            throw e;
        }
        
        if (props.getBooleanProperty("server.ssl.enabled", false)) {
            // Authorization - In this case we could obtain the address and port from Orchestrator also
            final String AuthorizationAddres = props.getProperty("auth_address","127.0.0.1");
            final int AuthorizationPort = props.getIntProperty("auth_port", 8445);
            final var AuthorizationSocketAddress = new InetSocketAddress(AuthorizationAddres, AuthorizationPort);
            // Send GET request to echo service
            try {
                String result = client.send(AuthorizationSocketAddress, new HttpClientRequest()
                        .method(HttpMethod.GET)
                        .uri("authorization/echo"))
                        .flatMap(response -> response.bodyAsString())
                        .await(Duration.ofSeconds(5));
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
                logger.error("Authorization mandatory core system is not reachable, Arrowhead local cloud incomplete!");
                throw e;
            }
        }

    }
}
