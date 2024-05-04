package org.example;

import java.io.Serializable;

public interface ChunkProcess extends Serializable {

    void setTotal(Long total);

    Long getTotal();

    void setSlice(Integer slice);

    Integer getSlice();

    default void code() {
        throw new UnsupportedOperationException("No Code to execute");
    }

    default void result() {

    }

    default void beforeAllCode() {

    }

    default void afterAllCode() {

    }

    default void beforeCode() {

    }

    default void afterCode() {

    }

    void setPager(SimplePageRequest pager);

    SimplePageRequest getPager();
}
