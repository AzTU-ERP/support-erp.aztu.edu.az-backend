package com.aztu.support_erp.ticket.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

/**
 * Multipart ticket form bound via {@code @ModelAttribute} (module/section/description/screenshots).
 *
 * <p>{@code pageUrl} and {@code userAgent} are filled in by the browser rather than typed, and are
 * accepted as plain fields so a ticket opened from a script is no different from one opened from
 * the button. Screenshots are optional; the count limit is enforced in the service, where the
 * configured maximum lives.
 */
public class CreateTicketForm {

    @NotBlank
    private String module;

    @NotBlank
    private String section;

    @NotBlank
    @Size(min = 10, max = 2000, message = "must be between 10 and 2000 characters")
    private String description;

    @Size(max = 2000)
    private String pageUrl;

    @Size(max = 500)
    private String userAgent;

    private List<MultipartFile> screenshots;

    public String getModule() { return module; }
    public void setModule(String module) { this.module = module; }
    public String getSection() { return section; }
    public void setSection(String section) { this.section = section; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getPageUrl() { return pageUrl; }
    public void setPageUrl(String pageUrl) { this.pageUrl = pageUrl; }
    public String getUserAgent() { return userAgent; }
    public void setUserAgent(String userAgent) { this.userAgent = userAgent; }
    public List<MultipartFile> getScreenshots() { return screenshots; }
    public void setScreenshots(List<MultipartFile> screenshots) { this.screenshots = screenshots; }
}
