package com.aztu.support_erp.infrastructure.storage;

/** Result of a successful upload: where it landed plus the metadata worth persisting. */
public record StoredFile(String storagePath, String originalName, String mimeType, long sizeBytes) {}
