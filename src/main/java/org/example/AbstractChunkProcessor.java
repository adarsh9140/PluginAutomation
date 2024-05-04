package org.example;

import org.example.ChunkProcess;

public abstract class AbstractChunkProcessor implements ChunkProcess {

    private static final long serialVersionUID = -1115979651189462302L;
    private Long total;
    private Integer slice = 100;
    private SimplePageRequest pager;

    public AbstractChunkProcessor(Long total) {
        this.total = total;
    }

    public AbstractChunkProcessor(Long total, Integer slice) {
        this.total = total;
        this.slice = slice;
    }

    @Override
    public void setTotal(Long total) {
        this.total = total;
    }

    @Override
    public void setSlice(Integer slice) {
        this.slice = slice;
    }

    @Override
    public Long getTotal() {
        return total;
    }

    @Override
    public Integer getSlice() {
        return slice;
    }

    @Override
    public void setPager(SimplePageRequest pager) {
        this.pager = pager;
    }

    @Override
    public SimplePageRequest getPager() {
        return pager;
    }
}
