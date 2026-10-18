package com.arshek.paymentgateway.web;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/** Clamps client-supplied paging and fixes the sort so callers cannot sort on arbitrary columns. */
final class PageRequests {

    static final int MAX_PAGE_SIZE = 100;

    private PageRequests() {
    }

    static Pageable newestFirst(int page, int size) {
        return PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "createdAt", "id"));
    }
}
