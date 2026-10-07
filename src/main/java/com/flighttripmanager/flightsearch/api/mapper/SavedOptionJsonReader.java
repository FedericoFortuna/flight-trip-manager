package com.flighttripmanager.flightsearch.api.mapper;
import java.io.IOException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import org.springframework.stereotype.Component;
import com.flighttripmanager.flightsearch.api.dto.SaveOptionRequest;
import com.flighttripmanager.flightsearch.application.contract.SavedOptionException;
@Component
public class SavedOptionJsonReader {
    private final ObjectMapper json;
    public SavedOptionJsonReader(ObjectMapper json){
        this.json=json.copy().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION).disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
            .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS);
    }
    public SaveOptionRequest read(String body){
        if(body.length()>4096)throw invalid();
        try{
            var node=json.readTree(body);
            if(node==null||!node.isObject()||!node.path("version").isIntegralNumber()||!node.path("version").canConvertToLong()
                ||!node.path("searchId").isTextual()||!node.path("provider").isTextual()||!node.path("providerOfferId").isTextual())throw invalid();
            return json.treeToValue(node,SaveOptionRequest.class);
        }catch(IOException e){throw invalid();}
    }
    private SavedOptionException invalid(){return new SavedOptionException(SavedOptionException.Reason.INVALID_REQUEST);}
}
