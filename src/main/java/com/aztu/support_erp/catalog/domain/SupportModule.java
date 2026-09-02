package com.aztu.support_erp.catalog.domain;

import com.aztu.support_erp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * support_modules — one row per module a ticket can be filed against.
 *
 * <p>The code is the natural key ("LMS") and the same string the ticket stores. Seeded by
 * migration V2 and editable afterwards: renaming a module, reordering the list or hiding one
 * from the form takes an UPDATE, not a release.
 */
@Entity
@Table(name = "support_modules")
public class SupportModule extends BaseEntity {

    @Id
    @Column(name = "code", updatable = false, nullable = false)
    private String code;

    @Column(name = "name", nullable = false)
    private String name;

    /**
     * The shell route this module lives under, so the ticket form can preselect the module the
     * reporter is actually looking at. Null means "no route of its own".
     */
    @Column(name = "route_prefix")
    private String routePrefix;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getRoutePrefix() { return routePrefix; }
    public void setRoutePrefix(String routePrefix) { this.routePrefix = routePrefix; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
