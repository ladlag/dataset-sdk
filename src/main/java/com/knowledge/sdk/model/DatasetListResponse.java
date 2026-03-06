package com.knowledge.sdk.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class DatasetListResponse {

    @JsonProperty("data")
    private List<DatasetResponse> data;

    @JsonProperty("has_more")
    private Boolean hasMore;

    @JsonProperty("total")
    private Integer total;

    @JsonProperty("page")
    private Integer page;

    @JsonProperty("limit")
    private Integer limit;

    public List<DatasetResponse> getData() {
        return data;
    }

    public void setData(List<DatasetResponse> data) {
        this.data = data;
    }

    public Boolean getHasMore() {
        return hasMore;
    }

    public void setHasMore(Boolean hasMore) {
        this.hasMore = hasMore;
    }

    public Integer getTotal() {
        return total;
    }

    public void setTotal(Integer total) {
        this.total = total;
    }

    public Integer getPage() {
        return page;
    }

    public void setPage(Integer page) {
        this.page = page;
    }

    public Integer getLimit() {
        return limit;
    }

    public void setLimit(Integer limit) {
        this.limit = limit;
    }
}
