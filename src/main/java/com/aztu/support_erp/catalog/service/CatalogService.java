package com.aztu.support_erp.catalog.service;

import com.aztu.support_erp.catalog.domain.SupportModule;
import com.aztu.support_erp.catalog.domain.SupportSection;
import com.aztu.support_erp.catalog.dto.ModuleConfigResponse;
import com.aztu.support_erp.catalog.dto.SectionResponse;
import com.aztu.support_erp.catalog.repository.SupportModuleRepository;
import com.aztu.support_erp.catalog.repository.SupportSectionRepository;
import com.aztu.support_erp.common.Codes;
import com.aztu.support_erp.common.enums.TicketModule;
import com.aztu.support_erp.common.exception.BadRequestException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The reportable surface: which modules exist and how each is divided.
 *
 * <p>This is the one authority on what {@code module}/{@code section} may hold. The frontend
 * builds its two selects from {@link #listModules()} and the ticket service validates against
 * {@link #requireOfferedSection}, so the two can never drift.
 */
@Service
public class CatalogService {

    private final SupportModuleRepository moduleRepository;
    private final SupportSectionRepository sectionRepository;

    public CatalogService(SupportModuleRepository moduleRepository,
                          SupportSectionRepository sectionRepository) {
        this.moduleRepository = moduleRepository;
        this.sectionRepository = sectionRepository;
    }

    /** Active modules with their active sections, in the order an admin arranged them. */
    @Transactional(readOnly = true)
    public List<ModuleConfigResponse> listModules() {
        Map<String, List<SectionResponse>> byModule = new LinkedHashMap<>();
        for (SupportSection section : sectionRepository.findByActiveTrueOrderByModuleCodeAscSortOrderAscCodeAsc()) {
            byModule.computeIfAbsent(section.getModuleCode(), k -> new java.util.ArrayList<>())
                    .add(new SectionResponse(section.getCode(), section.getName()));
        }
        return moduleRepository.findByActiveTrueOrderBySortOrderAscCodeAsc().stream()
                .map(module -> new ModuleConfigResponse(
                        module.getCode(),
                        module.getName(),
                        module.getRoutePrefix(),
                        byModule.getOrDefault(module.getCode(), List.of())))
                .toList();
    }

    /**
     * Validates a submitted pair, returning the module code. A deactivated section is refused
     * like an unknown one — it is no longer on offer, whatever old tickets still point at it.
     */
    @Transactional(readOnly = true)
    public String requireOfferedSection(String module, String section) {
        // Reject a typo before it reaches the catalogue, and say which codes exist.
        Codes.require(TicketModule.class, module);
        SupportSection found = sectionRepository.findByModuleCodeAndCode(module, section)
                .orElseThrow(() -> new BadRequestException(
                        "This section does not belong to the selected module. Allowed: " + sectionCodes(module)));
        if (!found.isActive()) {
            throw new BadRequestException("This section is no longer accepting reports");
        }
        SupportModule owner = moduleRepository.findById(module)
                .orElseThrow(() -> new BadRequestException("Unknown module '" + module + "'"));
        if (!owner.isActive()) {
            throw new BadRequestException("This module is no longer accepting reports");
        }
        return module;
    }

    private List<String> sectionCodes(String module) {
        return sectionRepository.findByActiveTrueOrderByModuleCodeAscSortOrderAscCodeAsc().stream()
                .filter(s -> s.getModuleCode().equals(module))
                .map(SupportSection::getCode)
                .collect(Collectors.toList());
    }
}
