package se.ltu.workflow.manager.dto;

import se.arkalix.dto.DtoReadableAs;
import se.arkalix.dto.DtoToString;
import se.arkalix.dto.DtoWritableAs;

import static se.arkalix.dto.DtoEncoding.JSON;

import java.util.List;
import java.util.Map;

@DtoReadableAs(JSON)
@DtoWritableAs(JSON)
@DtoToString
public interface Workflow {
    
    String workflowName();
    
    Map<String,List<String>> workflowConfig();

}
