package se.ltu.workflow.manager.dto;

import static se.arkalix.dto.DtoEncoding.JSON;

import java.util.Optional;

import se.arkalix.dto.DtoReadableAs;
import se.arkalix.dto.DtoToString;
import se.arkalix.dto.DtoWritableAs;

@DtoReadableAs(JSON)
@DtoWritableAs(JSON)
@DtoToString
/**
 * Data sent to the product when operation finishes
 *
 */
public interface OperationResult {
    
    int operationId();
    
    Optional<String> productId();
    
    String operationName();
    
    WorkflowResult result();
}
