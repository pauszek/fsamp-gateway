package io.github.pauszek.fsampgateway.infrastructure.security;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Central SHA-256 factory so the configured provider order is used consistently. */
public final class Sha256Digest {

    private final MessageDigest delegate;

    private Sha256Digest(MessageDigest delegate) {
        this.delegate = delegate;
    }

    public static Sha256Digest create() {
        return new Sha256Digest(createDelegate());
    }

    private static MessageDigest createDelegate() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by the Java runtime", e);
        }
    }

    public static byte[] digest(byte[] value) {
        return createDelegate().digest(value);
    }

    public void update(byte value) {
        delegate.update(value);
    }

    public void update(byte[] value) {
        delegate.update(value);
    }

    public void update(byte[] value, int offset, int length) {
        delegate.update(value, offset, length);
    }

    public byte[] finish() {
        return delegate.digest();
    }
}
