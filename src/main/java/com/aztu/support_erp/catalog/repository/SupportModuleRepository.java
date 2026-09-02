package com.aztu.support_erp.catalog.repository;

import com.aztu.support_erp.catalog.domain.SupportModule;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SupportModuleRepository extends JpaRepository<SupportModule, String> {

    List<SupportModule> findByActiveTrueOrderBySortOrderAscCodeAsc();
}
