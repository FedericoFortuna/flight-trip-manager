package com.flighttripmanager.flightsearch.api.mapper;
import java.io.IOException;
import java.util.List;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.cfg.*;
import com.fasterxml.jackson.databind.type.LogicalType;
import org.springframework.stereotype.Component;
import com.flighttripmanager.flightsearch.api.dto.CriteriaDto;
import com.flighttripmanager.flightsearch.application.contract.SearchException;
@Component
public class SearchJsonReader {
    private final ObjectMapper json;
    public SearchJsonReader(ObjectMapper mapper){
        json=mapper.copy().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT).disable(MapperFeature.ALLOW_COERCION_OF_SCALARS);
        for(var shape:List.of(CoercionInputShape.Integer,CoercionInputShape.Float,CoercionInputShape.Boolean))
            json.coercionConfigFor(LogicalType.Textual).setCoercion(shape,CoercionAction.Fail);
    }
    public CriteriaDto read(String body){
        if(body.length()>16384)throw invalid();
        try{
            JsonNode node=json.readTree(body);if(node==null||!node.isObject())throw invalid();
            for(String field:List.of("departureDate","returnDate"))text(node.path(field));
            for(String field:List.of("departureFrom","departureTo"))text(node.path("filters").path(field));
            if(!node.path("adults").isIntegralNumber())throw invalid();
            return json.treeToValue(node,CriteriaDto.class);
        }catch(IOException e){throw invalid();}
    }
    private void text(JsonNode node){if(!node.isMissingNode()&&!node.isNull()&&!node.isTextual())throw invalid();}
    private SearchException invalid(){return new SearchException(SearchException.Reason.INVALID_REQUEST,"request");}
}
