package ao.co.isptec.aplm.sca.security;

import android.util.Base64;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Simple AES-GCM encryption helper for app-layer payload protection during P2P sharing.
 * Format: Base64( [16-byte salt][12-byte iv][ciphertext...] ) with PBKDF2-HMAC-SHA256 key derivation.
 */
public final class CryptoUtils {
    private static final String KDF_ALG = "PBKDF2WithHmacSHA256";
    private static final String CIPHER_ALG = "AES/GCM/NoPadding";
    private static final int KEY_BITS = 256;
    private static final int PBKDF2_ITERS = 120_000;
    private static final int SALT_LEN = 16;
    private static final int IV_LEN = 12; // recommended for GCM
    private static final int GCM_TAG_BITS = 128;

    private CryptoUtils() {}

    public static String encryptToBase64(String plaintext, String passphrase) throws GeneralSecurityException {
        if (passphrase == null || passphrase.isEmpty()) {
            throw new GeneralSecurityException("Passphrase vazia");
        }
        byte[] salt = new byte[SALT_LEN];
        byte[] iv = new byte[IV_LEN];
        SecureRandom sr = new SecureRandom();
        sr.nextBytes(salt);
        sr.nextBytes(iv);

        SecretKeySpec key = deriveKey(passphrase, salt);
        Cipher cipher = Cipher.getInstance(CIPHER_ALG);
        GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_BITS, iv);
        cipher.init(Cipher.ENCRYPT_MODE, key, spec);
        byte[] ct = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

        ByteBuffer bb = ByteBuffer.allocate(SALT_LEN + IV_LEN + ct.length);
        bb.put(salt);
        bb.put(iv);
        bb.put(ct);
        return Base64.encodeToString(bb.array(), Base64.NO_WRAP);
    }

    public static String decryptFromBase64(String blobBase64, String passphrase) throws GeneralSecurityException {
        if (passphrase == null || passphrase.isEmpty()) {
            throw new GeneralSecurityException("Passphrase vazia");
        }
        byte[] blob = Base64.decode(blobBase64, Base64.NO_WRAP);
        if (blob.length < SALT_LEN + IV_LEN + 1) {
            throw new GeneralSecurityException("Blob inválido");
        }
        ByteBuffer bb = ByteBuffer.wrap(blob);
        byte[] salt = new byte[SALT_LEN];
        byte[] iv = new byte[IV_LEN];
        bb.get(salt);
        bb.get(iv);
        byte[] ct = new byte[bb.remaining()];
        bb.get(ct);

        SecretKeySpec key = deriveKey(passphrase, salt);
        Cipher cipher = Cipher.getInstance(CIPHER_ALG);
        GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_BITS, iv);
        cipher.init(Cipher.DECRYPT_MODE, key, spec);
        byte[] pt = cipher.doFinal(ct);
        return new String(pt, StandardCharsets.UTF_8);
    }

    private static SecretKeySpec deriveKey(String passphrase, byte[] salt) throws GeneralSecurityException {
        PBEKeySpec spec = new PBEKeySpec(passphrase.toCharArray(), salt, PBKDF2_ITERS, KEY_BITS);
        SecretKeyFactory skf = SecretKeyFactory.getInstance(KDF_ALG);
        byte[] keyBytes = skf.generateSecret(spec).getEncoded();
        return new SecretKeySpec(keyBytes, "AES");
    }
}
