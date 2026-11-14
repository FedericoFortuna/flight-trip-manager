package com.flighttripmanager.budgets.api.mapper;
import java.io.IOException;
import java.util.List;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.cfg.*;
import com.fasterxml.jackson.databind.type.LogicalType;
import org.springframework.stereotype.Component;
import com.flighttripmanager.budgets.api.dto.*;
import com.flighttripmanager.budgets.application.contract.BudgetException;
@Component
public class BudgetJsonReader {
    private final ObjectMapper json;
    public BudgetJsonReader(ObjectMapper json){
        this.json=json.copy().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,DeserializationFeature.FAIL_ON_TRAILING_TOKENS,DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION).disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
            .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS);
        for(var shape:List.of(CoercionInputShape.Integer,CoercionInputShape.Float,CoercionInputShape.Boolean))
            this.json.coercionConfigFor(LogicalType.Textual).setCoercion(shape,CoercionAction.Fail);
    }
    public <T>T read(String body,Class<T> type){
        if(body.length()>8192)throw invalid();
        try{
            var node=json.readTree(body);
            if(node==null||!node.isObject()||!node.path("version").isIntegralNumber()||!node.path("version").canConvertToLong())throw invalid();
            if(type==PutBudgetRequest.class){
                if(!node.has("totalBudgetUsd")||!node.path("categories").isObject())throw invalid();
                number(node.path("totalBudgetUsd"));node.path("categories").elements().forEachRemaining(this::number);
            }else if(type==PutLegBudgetRequest.class){
                if(!node.has("amountUsd"))throw invalid();number(node.path("amountUsd"));
            }else{
                if(!node.path("category").isTextual()||!node.path("description").isTextual())throw invalid();
                for(String field:List.of("estimated","actual")){
                    var money=node.path(field);if(money.isMissingNode()||money.isNull())continue;
                    if(!money.isObject()||!money.path("amount").isNumber()||!money.path("currency").isTextual())throw invalid();
                    number(money.path("amount"));number(money.path("amountUsd"));
                }
            }
            return json.treeToValue(node,type);
        }catch(IOException|ArithmeticException e){throw invalid();}
    }
    private void number(JsonNode node){
        if(!node.isNull()&&!node.isMissingNode()){
            if(!node.isNumber())throw invalid();
            var amount=node.decimalValue();
            if(amount.scale()>4||(long)amount.precision()-amount.scale()>19)throw invalid();
        }
    }
    private BudgetException invalid(){return new BudgetException(BudgetException.Reason.INVALID_REQUEST);}
}
