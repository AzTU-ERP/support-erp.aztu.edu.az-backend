package com.aztu.support_erp.infrastructure.storage;

import com.aztu.support_erp.common.exception.BadRequestException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/** Saves screenshots to the VPS filesystem and returns the stored path only (never base64/S3). */
@Service
public class FileStorageService {

    /** Screenshots only. A ticket has no use for anything a browser cannot render inline. */
    public static final Set<String> SCREENSHOT_MIME_TYPES = Set.of(
            "image/png",
            "image/jpeg",
            "image/webp");

    private final Path basePath;
    private final long maxSizeBytes;

    public FileStorageService(@Value("${app.storage.base-path:./support-storage}") String basePath,
                              @Value("${app.storage.max-size-bytes:5242880}") long maxSizeBytes) {
        this.basePath = Paths.get(basePath).toAbsolutePath().normalize();
        this.maxSizeBytes = maxSizeBytes;
    }

    /** Store one screenshot, restricting it to png/jpeg/webp by both header and content. */
    public StoredFile storeScreenshot(MultipartFile file, String subDir) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("File is required");
        }
        if (file.getSize() > maxSizeBytes) {
            throw new BadRequestException("File exceeds the maximum allowed size");
        }
        String mime = file.getContentType();
        if (mime == null || !SCREENSHOT_MIME_TYPES.contains(mime)) {
            throw new BadRequestException("Unsupported file type. Allowed: " + SCREENSHOT_MIME_TYPES);
        }
        byte[] head = readHead(file);
        if (!ImageSignature.matches(mime, head)) {
            throw new BadRequestException("The file content does not match its declared type");
        }
        try {
            Path dir = basePath.resolve(subDir).normalize();
            if (!dir.startsWith(basePath)) {
                throw new BadRequestException("Invalid storage path");
            }
            Files.createDirectories(dir);
            // The stored name is ours alone — the uploader's is kept only as a display label, so
            // nothing a caller typed can steer the path.
            String stored = UUID.randomUUID() + ImageSignature.extensionFor(mime);
            Path target = dir.resolve(stored);
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
            return new StoredFile(target.toString(), sanitizeName(file.getOriginalFilename()), mime, file.getSize());
        } catch (IOException e) {
            throw new BadRequestException("Failed to store file: " + e.getMessage());
        }
    }

    public Path resolve(String storagePath) {
        return Paths.get(storagePath);
    }

    /** Best-effort cleanup when a ticket's upload fails partway through. */
    public void deleteQuietly(String storagePath) {
        if (storagePath == null || storagePath.isBlank()) return;
        try {
            Files.deleteIfExists(resolve(storagePath));
        } catch (IOException ignored) {
            // The row is already gone; a stray file on disk is not worth failing the request over.
        }
    }

    private byte[] readHead(MultipartFile file) {
        try (InputStream in = file.getInputStream()) {
            return in.readNBytes(ImageSignature.HEAD_BYTES);
        } catch (IOException e) {
            throw new BadRequestException("Failed to read file: " + e.getMessage());
        }
    }

    /**
     * Keeps the display label harmless: no path separators, no control characters, bounded
     * length. It is only ever shown, never used to build a path.
     */
    private static String sanitizeName(String original) {
        if (original == null || original.isBlank()) return "screenshot";
        String base = original.replace('\\', '/');
        base = base.substring(base.lastIndexOf('/') + 1);
        base = base.replaceAll("[\\p{Cntrl}]", "").replaceAll("[^A-Za-z0-9._ -]", "_").trim();
        if (base.isBlank() || base.equals(".") || base.equals("..")) return "screenshot";
        return base.length() > 120 ? base.substring(0, 120) : base;
    }

    /**
     * Magic-byte checks. A declared MIME type is a claim by the uploader; these few bytes are
     * what the file actually is.
     */
    static final class ImageSignature {
        /** Enough for the longest signature we check (WebP's RIFF....WEBP). */
        static final int HEAD_BYTES = 12;

        private static final Map<String, String> EXTENSIONS = Map.of(
                "image/png", ".png",
                "image/jpeg", ".jpg",
                "image/webp", ".webp");

        private ImageSignature() {}

        static boolean matches(String mime, byte[] head) {
            if (head == null) return false;
            return switch (mime) {
                case "image/png" -> startsWith(head, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A);
                case "image/jpeg" -> startsWith(head, 0xFF, 0xD8, 0xFF);
                // "RIFF" .... "WEBP" — the four size bytes in between are not fixed.
                case "image/webp" -> head.length >= 12
                        && startsWith(head, 0x52, 0x49, 0x46, 0x46)
                        && head[8] == 'W' && head[9] == 'E' && head[10] == 'B' && head[11] == 'P';
                default -> false;
            };
        }

        static String extensionFor(String mime) {
            return EXTENSIONS.getOrDefault(mime, "");
        }

        private static boolean startsWith(byte[] head, int... signature) {
            if (head.length < signature.length) return false;
            for (int i = 0; i < signature.length; i++) {
                if ((head[i] & 0xFF) != signature[i]) return false;
            }
            return true;
        }
    }
}
