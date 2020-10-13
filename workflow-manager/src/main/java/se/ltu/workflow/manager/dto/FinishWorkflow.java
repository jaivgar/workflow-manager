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
 * DTO object received from a Workflow Executor system
 *
 */
public interface FinishWorkflow {
    
    int id();
    
    String workflowName();
    
    Boolean success();

    Optional<String> errorMessage();
    
    WStatus workflowStatus();
    
    ZonedDateTime queueTime();
    
    ZonedDateTime startTime();
    
    ZonedDateTime endTime();

}
