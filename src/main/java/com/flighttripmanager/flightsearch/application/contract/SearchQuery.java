package com.flighttripmanager.flightsearch.application.contract;
public record SearchQuery(int page,int size){
    public SearchQuery {if(page<0||page>1000000||size<1||size>100)throw new SearchException(SearchException.Reason.INVALID_REQUEST,"pagination");}
}
