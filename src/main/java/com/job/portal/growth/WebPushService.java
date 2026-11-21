package com.job.portal.growth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.job.portal.monitoring.MonitorSettingsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.*;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Web Push sender implemented on the JDK alone (no extra dependencies).
 *
 *   • Payload encryption: RFC 8291 / RFC 8188, content coding "aes128gcm"
 *   • Server identification: RFC 8292 VAPID with an ES256-signed JWT
 *
 * The VAPID key pair is generated on first use and stored in monitor_settings,
 * so there is nothing to configure.
 */
@Slf4j
@Service
public class WebPushService {

    public record Result(int sent, int failed, int removed) {
    }

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder B64URL = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder B64URL_DEC = Base64.getUrlDecoder();

    private final MonitorSettingsService settings;
    private final PushSubscriptionRepository subscriptions;
    private final ObjectMapper mapper;
    private final String subject;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private volatile KeyPair vapid;

    public WebPushService(MonitorSettingsService settings, PushSubscriptionRepository subscriptions, ObjectMapper mapper,
                          @Value("${app.admin-email:support@chaitanyatechworld.com}") String adminEmail) {
        this.settings = settings;
        this.subscriptions = subscriptions;
        this.mapper = mapper;
        this.subject = "mailto:" + adminEmail;
    }

    /* ── Keys ──────────────────────────────────────────────────────────── */

    static ECParameterSpec p256() {
        try {
            AlgorithmParameters params = AlgorithmParameters.getInstance("EC");
            params.init(new ECGenParameterSpec("secp256r1"));
            return params.getParameterSpec(ECParameterSpec.class);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    static KeyPair generateKeyPair() {
        try {
            KeyPairGenerator g = KeyPairGenerator.getInstance("EC");
            g.initialize(new ECGenParameterSpec("secp256r1"), RANDOM);
            return g.generateKeyPair();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Uncompressed point (0x04 || X || Y), 65 bytes. */
    static byte[] encodePoint(ECPublicKey key) {
        byte[] out = new byte[65];
        out[0] = 0x04;
        copyUnsigned(key.getW().getAffineX(), out, 1);
        copyUnsigned(key.getW().getAffineY(), out, 33);
        return out;
    }

    private static void copyUnsigned(BigInteger v, byte[] out, int offset) {
        byte[] b = v.toByteArray();
        int start = b.length > 32 ? b.length - 32 : 0;
        int len = Math.min(32, b.length);
        System.arraycopy(b, start, out, offset + 32 - len, len);
    }

    static ECPublicKey decodePoint(byte[] point) throws GeneralSecurityException {
        if (point.length != 65 || point[0] != 0x04) throw new InvalidKeyException("Expected an uncompressed P-256 point");
        BigInteger x = new BigInteger(1, Arrays.copyOfRange(point, 1, 33));
        BigInteger y = new BigInteger(1, Arrays.copyOfRange(point, 33, 65));
        return (ECPublicKey) KeyFactory.getInstance("EC").generatePublic(new ECPublicKeySpec(new ECPoint(x, y), p256()));
    }

    private synchronized KeyPair vapid() {
        if (vapid != null) return vapid;
        try {
            String pub = settings.internal(MonitorSettingsService.PUSH_VAPID_PUBLIC);
            String priv = settings.internal(MonitorSettingsService.PUSH_VAPID_PRIVATE);
            if (pub != null && priv != null) {
                PublicKey publicKey = decodePoint(B64URL_DEC.decode(pub));
                PrivateKey privateKey = KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(priv)));
                vapid = new KeyPair(publicKey, privateKey);
                return vapid;
            }
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            log.warn("Stored VAPID keys unreadable, generating new ones: {}", e.getMessage());
        }
        KeyPair kp = generateKeyPair();
        settings.setInternal(MonitorSettingsService.PUSH_VAPID_PUBLIC, B64URL.encodeToString(encodePoint((ECPublicKey) kp.getPublic())));
        settings.setInternal(MonitorSettingsService.PUSH_VAPID_PRIVATE, Base64.getEncoder().encodeToString(kp.getPrivate().getEncoded()));
        vapid = kp;
        log.info("Generated a new VAPID key pair for Web Push");
        return vapid;
    }

    /** Application server key for PushManager.subscribe (base64url). */
    public String publicKey() {
        return B64URL.encodeToString(encodePoint((ECPublicKey) vapid().getPublic()));
    }

    /* ── Encryption (RFC 8291) ─────────────────────────────────────────── */

    static byte[] hmac(byte[] key, byte[]... parts) throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        for (byte[] p : parts) mac.update(p);
        return mac.doFinal();
    }

    static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] p : parts) out.writeBytes(p);
        return out.toByteArray();
    }

    /**
     * Encrypt a payload for one subscription. Returns the full aes128gcm body:
     * salt(16) | rs(4) | idlen(1) | keyid(65) | ciphertext.
     */
    static byte[] encrypt(byte[] payload, byte[] uaPublic, byte[] authSecret, KeyPair ephemeral, byte[] salt) throws GeneralSecurityException {
        KeyAgreement ka = KeyAgreement.getInstance("ECDH");
        ka.init(ephemeral.getPrivate());
        ka.doPhase(decodePoint(uaPublic), true);
        byte[] ecdhSecret = ka.generateSecret();
        byte[] asPublic = encodePoint((ECPublicKey) ephemeral.getPublic());

        byte[] prkKey = hmac(authSecret, ecdhSecret);
        byte[] keyInfo = concat("WebPush: info\0".getBytes(StandardCharsets.US_ASCII), uaPublic, asPublic);
        byte[] ikm = hmac(prkKey, keyInfo, new byte[]{1});

        byte[] prk = hmac(salt, ikm);
        byte[] cek = Arrays.copyOf(hmac(prk, "Content-Encoding: aes128gcm\0".getBytes(StandardCharsets.US_ASCII), new byte[]{1}), 16);
        byte[] nonce = Arrays.copyOf(hmac(prk, "Content-Encoding: nonce\0".getBytes(StandardCharsets.US_ASCII), new byte[]{1}), 12);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(cek, "AES"), new GCMParameterSpec(128, nonce));
        byte[] ciphertext = cipher.doFinal(concat(payload, new byte[]{2})); // 0x02 = last record

        ByteBuffer header = ByteBuffer.allocate(16 + 4 + 1 + asPublic.length);
        header.put(salt).putInt(4096).put((byte) asPublic.length).put(asPublic);
        return concat(header.array(), ciphertext);
    }

    /* ── VAPID (RFC 8292) ──────────────────────────────────────────────── */

    String vapidJwt(String audience) throws GeneralSecurityException {
        String header = B64URL.encodeToString("{\"typ\":\"JWT\",\"alg\":\"ES256\"}".getBytes(StandardCharsets.UTF_8));
        long exp = Instant.now().plus(Duration.ofHours(12)).getEpochSecond();
        String claims = B64URL.encodeToString(("{\"aud\":\"" + audience + "\",\"exp\":" + exp + ",\"sub\":\"" + subject + "\"}").getBytes(StandardCharsets.UTF_8));
        Signature sig = Signature.getInstance("SHA256withECDSA");
        sig.initSign((ECPrivateKey) vapid().getPrivate());
        sig.update((header + "." + claims).getBytes(StandardCharsets.US_ASCII));
        return header + "." + claims + "." + B64URL.encodeToString(derToRaw(sig.sign()));
    }

    /** JWS needs the raw R||S (64 bytes), the JDK produces DER. */
    static byte[] derToRaw(byte[] der) {
        int offset = 2;
        if ((der[1] & 0x80) != 0) offset += der[1] & 0x7f;
        int rLen = der[offset + 1];
        byte[] r = Arrays.copyOfRange(der, offset + 2, offset + 2 + rLen);
        offset = offset + 2 + rLen;
        int sLen = der[offset + 1];
        byte[] s = Arrays.copyOfRange(der, offset + 2, offset + 2 + sLen);
        byte[] out = new byte[64];
        copyUnsigned(new BigInteger(1, r), out, 0);
        copyUnsigned(new BigInteger(1, s), out, 32);
        return out;
    }

    /* ── Sending ───────────────────────────────────────────────────────── */

    /** Send one notification; returns the push service's HTTP status (0 on network error). */
    int send(PushSubscription sub, Map<String, String> message) {
        try {
            byte[] payload = mapper.writeValueAsBytes(message);
            byte[] salt = new byte[16];
            RANDOM.nextBytes(salt);
            byte[] body = encrypt(payload, B64URL_DEC.decode(sub.getP256dh()), B64URL_DEC.decode(sub.getAuth()), generateKeyPair(), salt);
            URI endpoint = URI.create(sub.getEndpoint());
            String audience = endpoint.getScheme() + "://" + endpoint.getHost() + (endpoint.getPort() > 0 ? ":" + endpoint.getPort() : "");
            HttpRequest request = HttpRequest.newBuilder(endpoint)
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Encoding", "aes128gcm")
                    .header("Content-Type", "application/octet-stream")
                    .header("TTL", "86400")
                    .header("Urgency", "normal")
                    .header("Authorization", "vapid t=" + vapidJwt(audience) + ", k=" + publicKey())
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .build();
            return http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
        } catch (Exception e) {
            log.debug("Push to {} failed: {}", sub.getEndpoint(), e.getMessage());
            return 0;
        }
    }

    /** Send to every subscription; expired ones (404/410) are removed. */
    public Result broadcast(String title, String body, String url) {
        Map<String, String> message = Map.of("title", title, "body", body, "url", url, "tag", "ctw-" + Instant.now().getEpochSecond());
        int sent = 0, failed = 0, removed = 0;
        for (PushSubscription sub : subscriptions.findAll()) {
            int status = send(sub, message);
            if (status >= 200 && status < 300) {
                sent++;
                sub.setLastSuccessAt(Instant.now());
                sub.setFailures(0);
                subscriptions.save(sub);
            } else if (status == 404 || status == 410) {
                subscriptions.delete(sub);
                removed++;
            } else {
                failed++;
                int failures = (sub.getFailures() == null ? 0 : sub.getFailures()) + 1;
                if (failures >= 5) {
                    subscriptions.delete(sub);
                    removed++;
                } else {
                    sub.setFailures(failures);
                    subscriptions.save(sub);
                }
            }
        }
        return new Result(sent, failed, removed);
    }
}
