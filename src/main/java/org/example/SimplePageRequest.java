package org.example;


public class SimplePageRequest {

    private final int pageNumber;
    private final int pageSize;

    public SimplePageRequest(int pageNumber, int pageSize) {
        this.pageNumber = pageNumber;
        this.pageSize = pageSize;
    }

    public int getPageNumber() {
        return pageNumber;
    }

    public int getPageSize() {
        return pageSize;
    }
}
