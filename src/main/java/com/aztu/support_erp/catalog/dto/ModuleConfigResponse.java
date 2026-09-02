package com.aztu.support_erp.catalog.dto;

import java.util.List;

/**
 * A module and everything that can be reported against it. {@code routePrefix} lets the ticket
 * form preselect the module from the page the reporter is on, without the frontend keeping its
 * own copy of the mapping.
 */
public record ModuleConfigResponse(
        String code,
        String name,
        String routePrefix,
        List<SectionResponse> sections) {}
