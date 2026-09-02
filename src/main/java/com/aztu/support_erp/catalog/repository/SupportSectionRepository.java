package com.aztu.support_erp.catalog.repository;

import com.aztu.support_erp.catalog.domain.SupportSection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SupportSectionRepository extends JpaRepository<SupportSection, Integer> {

    List<SupportSection> findByActiveTrueOrderByModuleCodeAscSortOrderAscCodeAsc();

    Optional<SupportSection> findByModuleCodeAndCode(String moduleCode, String code);
}
