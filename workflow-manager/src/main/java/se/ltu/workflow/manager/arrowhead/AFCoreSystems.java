package se.ltu.workflow.manager.arrowhead;

import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import se.arkalix.net.http.HttpMethod;
import se.arkalix.net.http.client.HttpClient;
import se.arkalix.net.http.client.HttpClientRequest;
import se.arkalix.util.concurrent.Future;

import se.ltu.workflow.manager.properties.TypeSafeProperties;

public class AFCoreSystems {
    
    private static final Logger logger = LoggerFactory.getLogger(AFCoreSystems.class);

    private static final TypeSafeProperties props = TypeSafeProperties.getProp();
    
    public static void checkCoreSystems(HttpClient  client, int minutes)
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
                            logger.warn("Service Registry core system was reached,"
                                    + " but Echo message was empty!");
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
                                logger.warn("Authorization core system was reached,"
                                        + " but Echo message was empty!");
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
    
    private static class TimeCount {
        private long timeLeftInCount;
        
        public TimeCount(Duration minutes) {
            if(minutes.getSeconds() < 60) {
                throw new IllegalArgumentException(
                        "The minimun amount of time accepted is 1 minute (60 seconds)");
            }
            timeLeftInCount = minutes.getSeconds();
        }
        
        public void resetCount(Duration minutes) {
            if(minutes.getSeconds() < 60) {
                throw new IllegalArgumentException(
                        "The minimun amount of time accepted is 1 minute (60 seconds)");
            }
            timeLeftInCount = minutes.getSeconds();
        }
        
        /**
         * Discount/subtract an amount of time (greater than a second) from this TimeCount, and
         * sleep the thread for that same amount
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
