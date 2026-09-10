package com.citynoise.evidence.common;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * SHA-256 工具，用于原始数据哈希与审计/证据哈希链。
 */
public final class HashUtils {

    public static final String GENESIS = "0".repeat(64);

    private HashUtils() {
    }

    public static String sha256(String input) {
        if (input == null) {
            input = "";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    /**
     * 链式哈希：entryHash = sha256(prevHash | canonicalPayload)。
     */
    public static String chainHash(String prevHash, String canonicalPayload) {
        return sha256((prevHash == null ? GENESIS : prevHash) + "|" + canonicalPayload);
    }
}
