package se.ltu.workflow.manager.dto;

import static se.arkalix.dto.DtoEncoding.JSON;

import java.time.ZonedDateTime;
import java.util.Optional;

import se.arkalix.dto.DtoReadableAs;
import se.arkalix.dto.DtoToString;
import se.arkalix.dto.DtoWritableAs;

@DtoReadableAs(JSON)
@DtoWritableAs(JSON)
@DtoToString
/**
 * Data from the FinishWorkflow DTO useful to be sent to the product inside an OperationResult DTO
 *
 */
public interface WorkflowResult {
    
    Boolean success();

    Optional<String> errorMessage();
    
    ZonedDateTime queueTime();
    
    ZonedDateTime startTime();
    
    ZonedDateTime endTime();
}
