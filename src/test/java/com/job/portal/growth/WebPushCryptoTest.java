package com.job.portal.growth;

import org.junit.jupiter.api.Test;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.interfaces.ECPublicKey;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/** Web Push payload encryption must decrypt exactly the way a browser does (RFC 8291 / RFC 8188). */
class WebPushCryptoTest {

    /** Browser side of RFC 8291: decrypt an aes128gcm body with the subscription's private key. */
    static byte[] decrypt(byte[] body, KeyPair ua, byte[] authSecret) throws Exception {
        ByteBuffer buf = ByteBuffer.wrap(body);
        byte[] salt = new byte[16];
        buf.get(salt);
        int rs = buf.getInt();
        assertEquals(4096, rs);
        byte[] asPublic = new byte[buf.get()];
        buf.get(asPublic);
        byte[] ciphertext = new byte[buf.remaining()];
        buf.get(ciphertext);

        KeyAgreement ka = KeyAgreement.getInstance("ECDH");
        ka.init(ua.getPrivate());
        ka.doPhase(WebPushService.decodePoint(asPublic), true);
        byte[] ecdh = ka.generateSecret();
        byte[] uaPublic = WebPushService.encodePoint((ECPublicKey) ua.getPublic());

        byte[] prkKey = WebPushService.hmac(authSecret, ecdh);
        byte[] ikm = WebPushService.hmac(prkKey, WebPushService.concat("WebPush: info\0".getBytes(StandardCharsets.US_ASCII), uaPublic, asPublic), new byte[]{1});
        byte[] prk = WebPushService.hmac(salt, ikm);
        byte[] cek = Arrays.copyOf(WebPushService.hmac(prk, "Content-Encoding: aes128gcm\0".getBytes(StandardCharsets.US_ASCII), new byte[]{1}), 16);
        byte[] nonce = Arrays.copyOf(WebPushService.hmac(prk, "Content-Encoding: nonce\0".getBytes(StandardCharsets.US_ASCII), new byte[]{1}), 12);

        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(cek, "AES"), new GCMParameterSpec(128, nonce));
        byte[] plain = c.doFinal(ciphertext);
        int end = plain.length - 1;
        while (end >= 0 && plain[end] == 0) end--;
        assertEquals(2, plain[end], "last-record delimiter");
        return Arrays.copyOf(plain, end);
    }

    @Test
    void payloadRoundTripsLikeABrowser() throws Exception {
        KeyPair browser = WebPushService.generateKeyPair();
        byte[] auth = new byte[16];
        new SecureRandom().nextBytes(auth);
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        byte[] payload = "{\"title\":\"Traceflow Daily #7 is live\",\"url\":\"/daily\"}".getBytes(StandardCharsets.UTF_8);

        byte[] body = WebPushService.encrypt(payload, WebPushService.encodePoint((ECPublicKey) browser.getPublic()), auth,
                WebPushService.generateKeyPair(), salt);

        assertArrayEquals(payload, decrypt(body, browser, auth));
    }

    @Test
    void derSignatureConvertsToRawJwsFormat() throws Exception {
        KeyPair kp = WebPushService.generateKeyPair();
        for (int i = 0; i < 20; i++) {
            byte[] data = ("vapid-" + i).getBytes(StandardCharsets.UTF_8);
            Signature s = Signature.getInstance("SHA256withECDSA");
            s.initSign(kp.getPrivate());
            s.update(data);
            byte[] raw = WebPushService.derToRaw(s.sign());
            assertEquals(64, raw.length);
            // Verify the raw signature by converting back with the JDK's P1363 format.
            Signature v = Signature.getInstance("SHA256withECDSAinP1363Format");
            v.initVerify(kp.getPublic());
            v.update(data);
            assertTrue(v.verify(raw), "raw R||S signature verifies");
        }
    }

    @Test
    void pushEndpointsAreRestrictedToRealPushServices() {
        assertTrue(GrowthController.allowedEndpoint("https://fcm.googleapis.com/fcm/send/abc"));
        assertTrue(GrowthController.allowedEndpoint("https://updates.push.services.mozilla.com/wpush/v2/abc"));
        assertTrue(GrowthController.allowedEndpoint("https://wns2-par02p.notify.windows.com/w/?token=x"));
        assertTrue(GrowthController.allowedEndpoint("https://web.push.apple.com/abc"));
        assertFalse(GrowthController.allowedEndpoint("http://fcm.googleapis.com/fcm/send/abc"));
        assertFalse(GrowthController.allowedEndpoint("https://evil.example.com/fcm.googleapis.com"));
        assertFalse(GrowthController.allowedEndpoint("https://169.254.169.254/latest/meta-data"));
    }
}
