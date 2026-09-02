package com.aztu.support_erp.infrastructure.storage;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aztu.support_erp.common.exception.BadRequestException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

/**
 * Screenshot validation and storage. The point of these is that a declared MIME type is a claim
 * by the uploader — what matters is the first few bytes and where the file ends up.
 */
class FileStorageServiceTest {

    private static final long MAX_SIZE = 5L * 1024 * 1024;

    private static final byte[] PNG_MAGIC = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
    private static final byte[] JPEG_MAGIC = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0};
    // "RIFF", then the four size bytes (any value), then "WEBP".
    private static final byte[] WEBP_MAGIC = {'R', 'I', 'F', 'F', 1, 2, 3, 4, 'W', 'E', 'B', 'P'};

    private static byte[] png() {
        return withTail(PNG_MAGIC, 64);
    }

    private static byte[] jpeg() {
        return withTail(JPEG_MAGIC, 64);
    }

    private static byte[] webp() {
        return withTail(WEBP_MAGIC, 64);
    }

    private static byte[] withTail(byte[] head, int totalLength) {
        byte[] out = new byte[Math.max(totalLength, head.length)];
        System.arraycopy(head, 0, out, 0, head.length);
        return out;
    }

    private FileStorageService serviceIn(Path base) {
        return new FileStorageService(base.toString(), MAX_SIZE);
    }

    @Nested
    @DisplayName("A screenshot the browser really produced")
    class Accepted {

        @Test
        void storesAPngUnderTheTicketsOwnDirectory(@TempDir Path base) throws IOException {
            StoredFile stored = serviceIn(base).storeScreenshot(
                    new MockMultipartFile("screenshots", "shot.png", "image/png", png()), "ticket-1");

            Path written = Path.of(stored.storagePath());
            assertTrue(Files.exists(written));
            assertEquals(base.resolve("ticket-1").toAbsolutePath().normalize(), written.getParent());
            assertEquals("image/png", stored.mimeType());
            assertEquals("shot.png", stored.originalName());
            assertTrue(written.getFileName().toString().endsWith(".png"));
        }

        @Test
        void acceptsJpegAndWebpToo(@TempDir Path base) {
            FileStorageService service = serviceIn(base);
            assertDoesNotThrow(() -> service.storeScreenshot(
                    new MockMultipartFile("screenshots", "a.jpg", "image/jpeg", jpeg()), "t"));
            assertDoesNotThrow(() -> service.storeScreenshot(
                    new MockMultipartFile("screenshots", "b.webp", "image/webp", webp()), "t"));
        }

        @Test
        void givesEveryFileItsOwnNameEvenWhenTheUploadedOnesCollide(@TempDir Path base) {
            FileStorageService service = serviceIn(base);
            StoredFile first = service.storeScreenshot(
                    new MockMultipartFile("screenshots", "shot.png", "image/png", png()), "t");
            StoredFile second = service.storeScreenshot(
                    new MockMultipartFile("screenshots", "shot.png", "image/png", png()), "t");
            assertFalse(first.storagePath().equals(second.storagePath()));
        }
    }

    @Nested
    @DisplayName("Anything else")
    class Rejected {

        @Test
        void rejectsATypeThatIsNotAnImageWeRender(@TempDir Path base) {
            assertThrows(BadRequestException.class, () -> serviceIn(base).storeScreenshot(
                    new MockMultipartFile("screenshots", "notes.pdf", "application/pdf",
                            "%PDF-1.7".getBytes(StandardCharsets.UTF_8)), "t"));
        }

        @Test
        void rejectsAFileWhoseContentDoesNotMatchItsClaim(@TempDir Path base) {
            // A Windows executable renamed to .png and announced as image/png.
            byte[] notAnImage = withTail(new byte[] {'M', 'Z', (byte) 0x90}, 64);
            BadRequestException ex = assertThrows(BadRequestException.class,
                    () -> serviceIn(base).storeScreenshot(
                            new MockMultipartFile("screenshots", "shot.png", "image/png", notAnImage), "t"));
            assertTrue(ex.getMessage().contains("does not match"), ex.getMessage());
        }

        @Test
        void rejectsAJpegClaimingToBeAPng(@TempDir Path base) {
            assertThrows(BadRequestException.class, () -> serviceIn(base).storeScreenshot(
                    new MockMultipartFile("screenshots", "shot.png", "image/png", jpeg()), "t"));
        }

        @Test
        void rejectsAFileOverTheLimit(@TempDir Path base) {
            byte[] tooBig = withTail(PNG_MAGIC, (int) MAX_SIZE + 1);
            assertThrows(BadRequestException.class, () -> serviceIn(base).storeScreenshot(
                    new MockMultipartFile("screenshots", "huge.png", "image/png", tooBig), "t"));
        }

        @Test
        void rejectsAnEmptyPart(@TempDir Path base) {
            assertThrows(BadRequestException.class, () -> serviceIn(base).storeScreenshot(
                    new MockMultipartFile("screenshots", "empty.png", "image/png", new byte[0]), "t"));
        }

        @Test
        void rejectsAMissingContentType(@TempDir Path base) {
            assertThrows(BadRequestException.class, () -> serviceIn(base).storeScreenshot(
                    new MockMultipartFile("screenshots", "shot.png", null, png()), "t"));
        }
    }

    @Nested
    @DisplayName("The uploaded filename")
    class Naming {

        @Test
        void cannotClimbOutOfTheStorageDirectory(@TempDir Path base) {
            StoredFile stored = serviceIn(base).storeScreenshot(
                    new MockMultipartFile("screenshots", "../../../etc/passwd.png", "image/png", png()), "t");

            Path written = Path.of(stored.storagePath());
            assertTrue(written.startsWith(base.toAbsolutePath().normalize()));
            // The label is kept for display, with the path stripped out of it.
            assertEquals("passwd.png", stored.originalName());
        }

        @Test
        void survivesAMissingOne(@TempDir Path base) {
            StoredFile stored = serviceIn(base).storeScreenshot(
                    new MockMultipartFile("screenshots", null, "image/png", png()), "t");
            assertEquals("screenshot", stored.originalName());
        }

        @Test
        void carriesNoQuotesThatCouldEscapeAContentDispositionHeader(@TempDir Path base) {
            StoredFile stored = serviceIn(base).storeScreenshot(
                    new MockMultipartFile("screenshots", "a\"; x=\"b.png", "image/png", png()), "t");
            assertFalse(stored.originalName().contains("\""));
        }
    }

    @Nested
    @DisplayName("Cleanup")
    class Cleanup {

        @Test
        void removesAStoredFile(@TempDir Path base) {
            FileStorageService service = serviceIn(base);
            StoredFile stored = service.storeScreenshot(
                    new MockMultipartFile("screenshots", "shot.png", "image/png", png()), "t");
            service.deleteQuietly(stored.storagePath());
            assertFalse(Files.exists(Path.of(stored.storagePath())));
        }

        @Test
        void saysNothingAboutAFileThatIsAlreadyGone(@TempDir Path base) {
            FileStorageService service = serviceIn(base);
            assertDoesNotThrow(() -> service.deleteQuietly(base.resolve("nothing-here.png").toString()));
            assertDoesNotThrow(() -> service.deleteQuietly(null));
        }
    }
}
