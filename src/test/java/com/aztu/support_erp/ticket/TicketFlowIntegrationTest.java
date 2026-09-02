package com.aztu.support_erp.ticket;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aztu.support_erp.security.ServiceTokenFilter;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * End-to-end cover for the two flows that span the whole service: opening a ticket with
 * screenshots, and a reporter being warned and then blocked for irrelevant ones.
 *
 * <p>Disabled by default because the context needs a live Postgres with the support schema
 * migrated; run it with {@code -Dtest=TicketFlowIntegrationTest} once {@code compose.yaml} is up.
 * Each test rolls back, so a run leaves the database as it found it.
 *
 * <p>Callers authenticate with the dev SSO token format the {@code SsoClient} decodes when no
 * introspection URL is configured — the same tokens the frontend's local sign-in panel mints.
 */
@SpringBootTest(properties = {
        "app.storage.base-path=./target/test-support-storage",
        "app.support.violation.warn-threshold=1",
        "app.support.violation.block-threshold=2",
        // No auth service in the test: block decisions are recorded and queued, not delivered.
        "app.auth.base-url=",
        "app.auth.service-token=test-service-token"
})
@AutoConfigureMockMvc
@Transactional
@Disabled("Requires a running PostgreSQL — start compose.yaml first")
class TicketFlowIntegrationTest {

    private static final byte[] PNG = pngBytes();

    @Autowired
    private MockMvc mvc;

    private final UUID reporterSso = UUID.randomUUID();
    private final UUID otherSso = UUID.randomUUID();
    private final UUID devSso = UUID.randomUUID();

    private String reporter() {
        return devToken(reporterSso, "student", "Aysel Mammadova", "aysel@aztu.edu.az");
    }

    private String other() {
        return devToken(otherSso, "student", "Rashad Quliyev", "rashad@aztu.edu.az");
    }

    private String dev() {
        return devToken(devSso, "dev", "Dev Nurlan", "nurlan@aztu.edu.az");
    }

    // ---- opening a ticket ----

    @Test
    @DisplayName("A ticket opens with its screenshots, a number and an opening history entry")
    void createsATicketWithAttachments() throws Exception {
        String body = mvc.perform(multipart("/api/support/tickets")
                        .file(new MockMultipartFile("screenshots", "one.png", "image/png", PNG))
                        .file(new MockMultipartFile("screenshots", "two.png", "image/png", PNG))
                        .param("module", "LMS")
                        .param("section", "davamiyyet")
                        .param("description", "Davamiyyet cedveli sehv gosterilir")
                        .param("pageUrl", "https://erp.aztu.edu.az/lms/attendance")
                        .param("userAgent", "Mozilla/5.0")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + reporter()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("OPEN"))
                .andExpect(jsonPath("$.data.boardColumn").value("TO_DO"))
                .andExpect(jsonPath("$.data.attachmentCount").value(2))
                .andExpect(jsonPath("$.data.attachments.length()").value(2))
                .andExpect(jsonPath("$.data.history.length()").value(1))
                .andExpect(jsonPath("$.data.history[0].fromStatus").doesNotExist())
                .andExpect(jsonPath("$.data.history[0].toStatus").value("OPEN"))
                .andReturn().getResponse().getContentAsString();

        assertTrue(JsonPath.<String>read(body, "$.data.reference").startsWith("SUP-"));
    }

    @Test
    @DisplayName("A description under ten characters is refused")
    void rejectsAThinDescription() throws Exception {
        mvc.perform(multipart("/api/support/tickets")
                        .param("module", "LMS")
                        .param("section", "davamiyyet")
                        .param("description", "sehv")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + reporter()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("A section that does not belong to the module is refused")
    void rejectsASectionFromAnotherModule() throws Exception {
        mvc.perform(multipart("/api/support/tickets")
                        .param("module", "LMS")
                        .param("section", "kitab_axtarisi")
                        .param("description", "Bu bolme bu modula aid deyil")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + reporter()))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("A fourth screenshot is refused")
    void rejectsMoreScreenshotsThanAllowed() throws Exception {
        mvc.perform(multipart("/api/support/tickets")
                        .file(new MockMultipartFile("screenshots", "1.png", "image/png", PNG))
                        .file(new MockMultipartFile("screenshots", "2.png", "image/png", PNG))
                        .file(new MockMultipartFile("screenshots", "3.png", "image/png", PNG))
                        .file(new MockMultipartFile("screenshots", "4.png", "image/png", PNG))
                        .param("module", "LMS")
                        .param("section", "davamiyyet")
                        .param("description", "Dord ekran goruntusu gonderirem")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + reporter()))
                .andExpect(status().isBadRequest());
    }

    // ---- who may see what ----

    @Test
    @DisplayName("One reporter cannot open another's ticket by its id")
    void keepsTicketsPrivateToTheirReporter() throws Exception {
        String id = openTicket(reporter(), "Kurs materiallari acilmir");

        mvc.perform(get("/api/support/tickets/my/" + id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + reporter()))
                .andExpect(status().isOk());

        mvc.perform(get("/api/support/tickets/my/" + id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + other()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("The queue and the board are the DEV's alone")
    void keepsTheQueueForDevs() throws Exception {
        mvc.perform(get("/api/support/tickets")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + reporter()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/support/tickets/board")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + reporter()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/support/tickets/board")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + dev()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.columns.length()").value(3))
                .andExpect(jsonPath("$.data.columns[0].code").value("TO_DO"))
                .andExpect(jsonPath("$.data.columns[2].defaultStatus").doesNotExist());
    }

    @Test
    @DisplayName("A screenshot is served to its reporter and to a DEV, and to nobody else")
    void servesScreenshotsToTheEntitled() throws Exception {
        String body = mvc.perform(multipart("/api/support/tickets")
                        .file(new MockMultipartFile("screenshots", "one.png", "image/png", PNG))
                        .param("module", "LMS")
                        .param("section", "davamiyyet")
                        .param("description", "Ekran goruntusu ile birlikde")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + reporter()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String url = JsonPath.read(body, "$.data.attachments[0].url");

        mvc.perform(get(url).header(HttpHeaders.AUTHORIZATION, "Bearer " + reporter()))
                .andExpect(status().isOk());
        mvc.perform(get(url).header(HttpHeaders.AUTHORIZATION, "Bearer " + dev()))
                .andExpect(status().isOk());
        mvc.perform(get(url).header(HttpHeaders.AUTHORIZATION, "Bearer " + other()))
                .andExpect(status().isForbidden());
    }

    // ---- the status machine over HTTP ----

    @Test
    @DisplayName("A DEV walks a ticket to RESOLVED, and cannot skip the middle")
    void movesATicketThroughItsLifecycle() throws Exception {
        String id = openTicket(reporter(), "Qiymetlendirme sehifesi acilmir");

        mvc.perform(patch("/api/support/tickets/" + id + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"RESOLVED\"}")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + dev()))
                .andExpect(status().isConflict());

        mvc.perform(patch("/api/support/tickets/" + id + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"IN_REVIEW\",\"comment\":\"Baxiram\"}")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + dev()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("IN_REVIEW"))
                .andExpect(jsonPath("$.data.boardColumn").value("IN_PROGRESS"));

        mvc.perform(patch("/api/support/tickets/" + id + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"RESOLVED\",\"comment\":\"Duzeldildi\"}")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + dev()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RESOLVED"))
                .andExpect(jsonPath("$.data.resolvedAt").exists())
                .andExpect(jsonPath("$.data.history.length()").value(3));
    }

    @Test
    @DisplayName("Cancelling without a reason is refused")
    void requiresAReasonToCancel() throws Exception {
        String id = openTicket(reporter(), "Sebebsiz legv edilmeyecek");

        mvc.perform(patch("/api/support/tickets/" + id + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CANCELED\"}")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + dev()))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("A reporter cannot move their own ticket")
    void keepsTheStatusOutOfTheReportersHands() throws Exception {
        String id = openTicket(reporter(), "Oz muracietimi bagliya bilmerem");

        mvc.perform(patch("/api/support/tickets/" + id + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"RESOLVED\"}")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + reporter()))
                .andExpect(status().isForbidden());
    }

    // ---- warning, blocking, unblocking ----

    @Test
    @DisplayName("The first irrelevant ticket warns; the second blocks; a DEV can lift it")
    void warnsThenBlocksThenUnblocks() throws Exception {
        String first = openTicket(reporter(), "Birinci esassiz muraciet");
        String second = openTicket(reporter(), "Ikinci esassiz muraciet");

        // Nothing on the record yet.
        mvc.perform(get("/api/support/me/violation-status")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + reporter()))
                .andExpect(jsonPath("$.data.irrelevantCount").value(0))
                .andExpect(jsonPath("$.data.warned").value(false))
                .andExpect(jsonPath("$.data.blocked").value(false));

        cancelAsIrrelevant(first, "Problem tapilmadi");

        mvc.perform(get("/api/support/me/violation-status")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + reporter()))
                .andExpect(jsonPath("$.data.irrelevantCount").value(1))
                .andExpect(jsonPath("$.data.warned").value(true))
                .andExpect(jsonPath("$.data.blocked").value(false))
                .andExpect(jsonPath("$.data.remainingBeforeBlock").value(1));

        cancelAsIrrelevant(second, "Yene problem tapilmadi");

        mvc.perform(get("/api/support/me/violation-status")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + reporter()))
                .andExpect(jsonPath("$.data.irrelevantCount").value(2))
                .andExpect(jsonPath("$.data.blocked").value(true))
                .andExpect(jsonPath("$.data.remainingBeforeBlock").value(0));

        // The auth service's own view of the same decision.
        mvc.perform(get("/api/support/internal/users/" + reporterSso + "/block-status")
                        .header(ServiceTokenFilter.HEADER, "test-service-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.blocked").value(true))
                .andExpect(jsonPath("$.data.reason").exists());

        String userId = JsonPath.read(mvc.perform(get("/api/support/violations")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + dev()))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.data.content[0].blocked").value(true))
                        .andReturn().getResponse().getContentAsString(),
                "$.data.content[0].user.id");

        mvc.perform(post("/api/support/violations/" + userId + "/unblock")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + dev()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.blocked").value(false))
                .andExpect(jsonPath("$.data.irrelevantCount").value(0))
                .andExpect(jsonPath("$.data.unblockedAt").exists());

        mvc.perform(get("/api/support/internal/users/" + reporterSso + "/block-status")
                        .header(ServiceTokenFilter.HEADER, "test-service-token"))
                .andExpect(jsonPath("$.data.blocked").value(false));
    }

    @Test
    @DisplayName("The internal block-status endpoint refuses a caller without the service token")
    void guardsTheInternalSurface() throws Exception {
        mvc.perform(get("/api/support/internal/users/" + reporterSso + "/block-status"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/support/internal/users/" + reporterSso + "/block-status")
                        .header(ServiceTokenFilter.HEADER, "wrong-token"))
                .andExpect(status().isUnauthorized());
        // Even a DEV's own token is not a service token.
        mvc.perform(get("/api/support/internal/users/" + reporterSso + "/block-status")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + dev()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Marking a ticket irrelevant without cancelling it is refused")
    void keepsIrrelevantTiedToCancellation() throws Exception {
        String id = openTicket(reporter(), "Bu esassiz sayila bilmez");

        mvc.perform(patch("/api/support/tickets/" + id + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"IN_REVIEW\",\"irrelevant\":true}")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + dev()))
                .andExpect(status().isBadRequest());
    }

    // ---- assignment ----

    @Test
    @DisplayName("A DEV takes a ticket and hands it back")
    void assignsAndUnassigns() throws Exception {
        String id = openTicket(reporter(), "Kim bunu goturecek");
        String devId = JsonPath.read(mvc.perform(get("/api/support/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + dev()))
                .andReturn().getResponse().getContentAsString(), "$.data.id");

        mvc.perform(patch("/api/support/tickets/" + id + "/assign")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"devId\":\"" + devId + "\"}")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + dev()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.assignedDev.id").value(devId));

        mvc.perform(patch("/api/support/tickets/" + id + "/assign")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"devId\":null}")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + dev()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.assignedDev").doesNotExist());
    }

    // ---- the catalogue ----

    @Test
    @DisplayName("The form's options come from the seeded catalogue")
    void servesTheModuleCatalogue() throws Exception {
        mvc.perform(get("/api/support/config/modules")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + reporter()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(6))
                .andExpect(jsonPath("$.data[0].code").value("LMS"))
                .andExpect(jsonPath("$.data[0].routePrefix").value("/lms"))
                .andExpect(jsonPath("$.data[0].sections[0].code").value("davamiyyet"));
    }

    @Test
    @DisplayName("An anonymous caller gets nowhere")
    void requiresAToken() throws Exception {
        mvc.perform(get("/api/support/config/modules")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/support/tickets/my")).andExpect(status().isUnauthorized());
    }

    // ---- helpers ----

    private String openTicket(String token, String description) throws Exception {
        String body = mvc.perform(multipart("/api/support/tickets")
                        .param("module", "LMS")
                        .param("section", "davamiyyet")
                        .param("description", description)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.data.id");
    }

    private void cancelAsIrrelevant(String ticketId, String reason) throws Exception {
        mvc.perform(patch("/api/support/tickets/" + ticketId + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CANCELED\",\"irrelevant\":true,\"cancelReason\":\""
                                + reason + "\"}")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + dev()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELED"))
                .andExpect(jsonPath("$.data.irrelevant").value(true));
    }

    /** The dev token format {@code SsoClient} decodes: base64url("uuid:roles:name:email"). */
    private static String devToken(UUID userId, String roles, String fullName, String email) {
        String raw = userId + ":" + roles + ":" + fullName + ":" + email;
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] pngBytes() {
        byte[] out = new byte[64];
        byte[] magic = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
        System.arraycopy(magic, 0, out, 0, magic.length);
        return out;
    }
}
