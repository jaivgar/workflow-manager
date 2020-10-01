package se.ltu.workflow.manager;

import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.Arrays;

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
import se.ltu.workflow.manager.dto.Echo;
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
            final String kStorePath = props.getProperty("server.ssl.key-store", "keystore.p12");
            final char[] tStorePassword = props.getProperty("server.ssl.trust-store-password", "123456").toCharArray();
            final String tStorePath = props.getProperty("server.ssl.trust-store", "truststore.p12");
            
            // Load properties for system identity and truststore
            final var identity = new OwnedIdentity.Loader()
                    .keyPassword(pKeyPassword)
                    .keyStorePath(Path.of(kStorePath))
                    .keyStorePassword(kStorePassword)
                    .load();
            final var trustStore = TrustStore.read(Path.of(tStorePath), tStorePassword);
            
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
            
            // Create client to send HTTPRequest
            final var client = new HttpClient.Builder()
                    .identity(identity)
                    .trustStore(trustStore)
                    .build();
            
            /* Check that the core systems are available - We want this call synchronous, as 
             * initialization should not continue if they are not succesfull
             */
            // Service Registry
            final String serviceRegistryAddres = props.getProperty("sr_address","127.0.0.1");
            final int serviceRegistryPort = props.getIntProperty("sr_port", 8443);
            final var serviceRegistrySocketAddress = new InetSocketAddress(serviceRegistryAddres, serviceRegistryPort);
            // Send GET request to echo
            // TODO: Error due to not using DTO class in bodyAsClassIfSuccess() method, 
            client.send(serviceRegistrySocketAddress, new HttpClientRequest()
                    .method(HttpMethod.GET)
                    .uri("/echo"))
            .flatMap(response -> response.bodyAsClassIfSuccess(DtoEncoding.JSON, EchoDto.class))
            .ifSuccess(body -> {
                logger.info("Service Registry core system is reachable.");
            })
            .onFailure(throwable -> {
                logger.error("Service Registry mandatory core system is not reachable, Arrowhead local cloud incomplete!");
                throwable.printStackTrace();
            });

            
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
            // TODO: handle exception
        }
        
    }
}
