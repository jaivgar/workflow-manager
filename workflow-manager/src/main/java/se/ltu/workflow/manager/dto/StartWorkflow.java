package se.ltu.workflow.manager.dto;

import se.arkalix.dto.DtoReadableAs;
import se.arkalix.dto.DtoToString;
import se.arkalix.dto.DtoWritableAs;

import static se.arkalix.dto.DtoEncoding.JSON;

import java.time.ZonedDateTime;

@DtoReadableAs(JSON)
@DtoWritableAs(JSON)
@DtoToString
public interface StartWorkflow {
    
    int id();
    
    String workflowName();
    
    WStatus workflowStatus();
    
    ZonedDateTime queueTime();

}
