package com.flighttripmanager.flightsearch.infrastructure.persistence.adapter;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import com.flighttripmanager.flightsearch.application.contract.*;
import com.flighttripmanager.flightsearch.application.port.out.SearchHistoryStore;
import com.flighttripmanager.flightsearch.infrastructure.persistence.entity.SearchHistoryJpaEntity;
import com.flighttripmanager.flightsearch.infrastructure.persistence.repository.SearchHistoryJpaRepository;
@Component
public class JpaSearchHistory implements SearchHistoryStore {
    private final SearchHistoryJpaRepository repository;private final ObjectMapper json;
    public JpaSearchHistory(SearchHistoryJpaRepository repository,ObjectMapper json){this.repository=repository;this.json=json;}
    @Override @Transactional
    public void save(CriteriaHistory entry){
        try{
            var entity=new SearchHistoryJpaEntity();entity.setId(entry.id());entity.setCreatedAt(entry.createdAt());
            entity.setCriteria(json.writeValueAsString(entry.criteria()));repository.saveAndFlush(entity);
        }catch(JsonProcessingException e){throw new IllegalStateException("Search criteria serialization failed");}
    }
    @Override @Transactional(readOnly=true)
    public SearchPage<CriteriaHistory> list(SearchQuery query){
        var page=repository.findAll(PageRequest.of(query.page(),query.size(),Sort.by(Sort.Order.desc("createdAt"),Sort.Order.asc("id"))));
        return new SearchPage<>(page.getContent().stream().map(this::read).toList(),page.getNumber(),page.getSize(),page.getTotalElements(),page.getTotalPages());
    }
    private CriteriaHistory read(SearchHistoryJpaEntity entity){
        try{return new CriteriaHistory(entity.getId(),entity.getCreatedAt(),json.readValue(entity.getCriteria(),CriteriaData.class));}
        catch(JsonProcessingException e){throw new IllegalStateException("Stored search criteria could not be read");}
    }
}
