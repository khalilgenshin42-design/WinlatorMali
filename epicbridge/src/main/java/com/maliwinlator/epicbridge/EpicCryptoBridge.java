package com.maliwinlator.epicbridge;

import java.security.GeneralSecurityException;
import java.util.Arrays;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/** Android JCA AES-GCM primitive used by Legendary's Android-only crypto shim. */
public final class EpicCryptoBridge {
    private static final int GCM_TAG_BITS = 128;

    private EpicCryptoBridge() {}

    public static byte[] decryptAesGcm(byte[] key, byte[] nonce, byte[] ciphertext, byte[] tag)
            throws GeneralSecurityException {
        if (key == null || (key.length != 16 && key.length != 24 && key.length != 32)) {
            throw new GeneralSecurityException("AES key length must be 16, 24, or 32 bytes");
        }
        if (nonce == null || nonce.length != 12) {
            throw new GeneralSecurityException("AES-GCM nonce must be 12 bytes");
        }
        if (tag == null || tag.length != 16) {
            throw new GeneralSecurityException("AES-GCM tag must be 16 bytes");
        }

        byte[] authenticatedCiphertext = new byte[ciphertext.length + tag.length];
        System.arraycopy(ciphertext, 0, authenticatedCiphertext, 0, ciphertext.length);
        System.arraycopy(tag, 0, authenticatedCiphertext, ciphertext.length, tag.length);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            SecretKeySpec secretKey = new SecretKeySpec(key, "AES");
            GCMParameterSpec parameters = new GCMParameterSpec(GCM_TAG_BITS, nonce);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, parameters);
            return cipher.doFinal(authenticatedCiphertext);
        } finally {
            Arrays.fill(authenticatedCiphertext, (byte) 0);
        }
    }

    /** NIST AES-128-GCM known-answer test plus negative tag-authentication test. */
    public static boolean verifyKnownAnswerTest() {
        byte[] key = new byte[16];
        byte[] nonce = new byte[12];
        byte[] ciphertext = hex("0388dace60b6a392f328c2b971b2fe78");
        byte[] tag = hex("ab6e47d42cec13bdf53a67b21257bddf");
        try {
            byte[] plaintext = decryptAesGcm(key, nonce, ciphertext, tag);
            if (plaintext.length != 16) return false;
            for (byte value : plaintext) if (value != 0) return false;

            tag[0] ^= 1;
            try {
                decryptAesGcm(key, nonce, ciphertext, tag);
                return false;
            } catch (AEADBadTagException expected) {
                return true;
            }
        } catch (GeneralSecurityException e) {
            return false;
        } finally {
            Arrays.fill(key, (byte) 0);
            Arrays.fill(nonce, (byte) 0);
            Arrays.fill(ciphertext, (byte) 0);
            Arrays.fill(tag, (byte) 0);
        }
    }

    private static byte[] hex(String value) {
        byte[] result = new byte[value.length() / 2];
        for (int i = 0; i < result.length; i++) {
            result[i] = (byte) Integer.parseInt(value.substring(i * 2, i * 2 + 2), 16);
        }
        return result;
    }
}
