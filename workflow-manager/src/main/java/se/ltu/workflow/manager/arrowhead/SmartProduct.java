package se.ltu.workflow.manager.arrowhead;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import se.arkalix.net.http.service.HttpServiceRequest;
import se.arkalix.security.identity.SystemIdentity;

public class SmartProduct extends AFSystem{
    
    private final Optional<String> productID;
    private final List<Integer> operations = new ArrayList<>();
    
    public SmartProduct(String name, Optional<String> productID, SystemIdentity identity, 
            InetSocketAddress productAddress, Optional<Integer> operationId){
        
        super(name,identity, productAddress);
        this.productID = productID;
        operationId.ifPresent(id -> this.operations.add(id));
    }

    /**
     * Creates a Smart product extracting data from the service request
     * 
     * @param request
     * @param productID
     * @param operationId
     * @return
     */
    public static SmartProduct fromRequest(HttpServiceRequest request, Optional<String> productID, Optional<Integer> operationId){
        return new SmartProduct(request.consumer().name(), 
                                productID, 
                                request.consumer().identity(), 
                                request.consumer().socketAddress(), 
                                operationId);
    }
    
    public Optional<String> getProductID() {
        return productID;
    }

    public List<Integer> getOperations() {
        return operations;
    }
    
    
}
