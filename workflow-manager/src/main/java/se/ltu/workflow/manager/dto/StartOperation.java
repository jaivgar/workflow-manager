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
public interface StartOperation {
    
    int operationId();
    
    Optional<String> productId();
    
    String operationName();
    
    ZonedDateTime queueTime();

}
