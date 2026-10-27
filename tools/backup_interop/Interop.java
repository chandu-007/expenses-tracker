import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * The reference side of the backup-format check: the exact steps of androidApp/.../BackupCrypto.kt (same javax.crypto calls, same layout), as a
 * stand-alone program so the iPhone code can be checked against it without a phone.
 *   java tools/backup_interop/Interop.java make  <passphrase> <plain-file> <out.expbak>   fixed-salt/nonce sample, the way Android writes it
 *   java tools/backup_interop/Interop.java check <passphrase> <in.expbak> <expected-plain-file>   decrypts a file made elsewhere; exit 1 on mismatch
 *
 * Backups with photos and files (docs/BACKUP_FORMAT.md, version 2, and the EXPENC2 chunked layout), written and read here from the document alone, with no code shared with the app:
 *   java tools/backup_interop/Interop.java make2   <passphrase or -> <out.expbak>   a version 2 file with four sample files (the pattern below), sealed as EXPENC2 with a fixed salt unless "-"
 *   java tools/backup_interop/Interop.java verify2 <passphrase or -> <in.expbak> [pattern]   opens a version 1 or 2 file (plain, EXPENC1 or EXPENC2): payload length and sha256, every file's sha256, no bytes left over;
 *                                                                                           with "pattern" also that file k (1, 2, ...) holds byte i = (i*31 + k*7) mod 256, which is what the apps' sample writer produces; exit 1 on any mismatch
 */
public class Interop {
    static final byte[] MAGIC = "EXPENC1\n".getBytes(StandardCharsets.UTF_8);
    static final int ITERATIONS = 600_000;

    static SecretKeySpec key(String pass, byte[] salt, int iterations) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(pass.toCharArray(), salt, iterations, 256);
        return new SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded(), "AES");
    }

    // ---- version 2 files ----

    static final byte[] MAGIC2 = "EXPENC2\n".getBytes(StandardCharsets.UTF_8);
    static final int[] SAMPLE_SIZES = {70_000, 65_536, 1, 3_000};

    static byte[] pattern(int k, int size) {
        byte[] b = new byte[size];
        for (int i = 0; i < size; i++) b[i] = (byte) (i * 31 + k * 7);
        return b;
    }

    static String sha256(byte[] data) throws Exception {
        StringBuilder sb = new StringBuilder();
        for (byte x : MessageDigest.getInstance("SHA-256").digest(data)) sb.append(String.format("%02x", x));
        return sb.toString();
    }

    static byte[] nonce(long index) {
        return ByteBuffer.allocate(12).putInt(0).putLong(index).array();
    }

    static byte[] aad(byte[] header, boolean last) {
        return ByteBuffer.allocate(header.length + 1).put(header).put((byte) (last ? 1 : 0)).array();
    }

    /** The sample plain file: the first line, the payload (the attachments list, in the way the document says), then the files' bytes. */
    static byte[] sample2() throws Exception {
        StringBuilder list = new StringBuilder();
        ByteArrayOutputStream files = new ByteArrayOutputStream();
        for (int k = 1; k <= SAMPLE_SIZES.length; k++) {
            byte[] f = pattern(k, SAMPLE_SIZES[k - 1]);
            files.write(f);
            if (k > 1) list.append(',');
            list.append("{\"id\":\"att-").append(k).append("\",\"entryId\":\"e").append(k <= 2 ? 1 : 2).append("\",\"name\":\"file").append(k).append(".bin\",\"mime\":\"application/octet-stream\",\"sizeBytes\":")
                .append(f.length).append(",\"sha256\":\"").append(sha256(f)).append("\",\"createdAt\":").append(1000 + k).append('}');
        }
        byte[] payload = ("{\"currencies\":[{\"code\":\"INR\",\"digits\":2}],\"accounts\":[{\"id\":\"cash\",\"name\":\"Cash\",\"type\":\"CASH\",\"currency\":\"INR\"}],"
            + "\"entries\":[{\"id\":\"e1\",\"kind\":\"EXPENSE\",\"occurredAt\":1,\"accountId\":\"cash\",\"amountMinor\":500,\"currency\":\"INR\",\"splits\":[{\"categoryId\":\"Food\",\"amountMinor\":500}]},"
            + "{\"id\":\"e2\",\"kind\":\"EXPENSE\",\"occurredAt\":2,\"accountId\":\"cash\",\"amountMinor\":700,\"currency\":\"INR\",\"splits\":[{\"categoryId\":\"Food\",\"amountMinor\":700}]}],"
            + "\"attachments\":[" + list + "],\"settings\":{}}").getBytes(StandardCharsets.UTF_8);
        String line = "{\"format\":\"expenses-backup\",\"version\":2,\"createdAt\":1760000000000,\"payloadBytes\":" + payload.length + ",\"sha256\":\"" + sha256(payload) + "\"}\n";
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(line.getBytes(StandardCharsets.UTF_8)); out.write(payload); out.write(files.toByteArray());
        return out.toByteArray();
    }

    /** EXPENC2: the layout of the document, written from it. Fixed salt, so the sample is the same every time. */
    static byte[] seal2(byte[] plain, String pass, int chunk) throws Exception {
        byte[] salt = new byte[16];
        for (int i = 0; i < 16; i++) salt[i] = (byte) (i * 5 + 2);
        byte[] header = ByteBuffer.allocate(32).put(MAGIC2).putInt(ITERATIONS).put(salt).putInt(chunk).array();
        SecretKeySpec key = key(pass, salt, ITERATIONS);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(header);
        int chunks = Math.max(1, (plain.length + chunk - 1) / chunk);
        for (int i = 0; i < chunks; i++) {
            int from = i * chunk, to = Math.min(plain.length, from + chunk);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, nonce(i)));
            c.updateAAD(aad(header, i == chunks - 1));
            out.write(c.doFinal(plain, from, to - from));
        }
        return out.toByteArray();
    }

    static byte[] open2(byte[] bytes, String pass) throws Exception {
        byte[] header = Arrays.copyOfRange(bytes, 0, 32);
        int iterations = ByteBuffer.wrap(header, 8, 4).getInt();
        int chunk = ByteBuffer.wrap(header, 28, 4).getInt();
        if (iterations < 100_000 || iterations > 5_000_000 || chunk < 4096 || chunk > 1_048_576) throw new IllegalStateException("header out of range");
        SecretKeySpec key = key(pass, Arrays.copyOfRange(header, 12, 28), iterations);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int sealed = chunk + 16, pos = 32;
        for (long i = 0; pos < bytes.length; i++) {
            int len = Math.min(sealed, bytes.length - pos);
            boolean last = pos + len >= bytes.length;
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, nonce(i)));
            c.updateAAD(aad(header, last));
            out.write(c.doFinal(bytes, pos, len));
            pos += len;
        }
        return out.toByteArray();
    }

    /** A just-enough JSON reader: objects, arrays, strings, integers, booleans, null. */
    static final class Json {
        final String s; int i = 0;
        Json(String s) { this.s = s; }
        Object value() {
            ws();
            char c = s.charAt(i);
            if (c == '{') { i++; Map<String, Object> m = new LinkedHashMap<>(); ws(); if (s.charAt(i) == '}') { i++; return m; }
                while (true) { ws(); String k = string(); ws(); expect(':'); m.put(k, value()); ws(); if (s.charAt(i) == ',') { i++; continue; } expect('}'); return m; } }
            if (c == '[') { i++; List<Object> l = new ArrayList<>(); ws(); if (s.charAt(i) == ']') { i++; return l; }
                while (true) { l.add(value()); ws(); if (s.charAt(i) == ',') { i++; continue; } expect(']'); return l; } }
            if (c == '"') return string();
            if (s.startsWith("true", i)) { i += 4; return Boolean.TRUE; }
            if (s.startsWith("false", i)) { i += 5; return Boolean.FALSE; }
            if (s.startsWith("null", i)) { i += 4; return null; }
            int j = i; while (j < s.length() && "-+.0123456789eE".indexOf(s.charAt(j)) >= 0) j++;
            String n = s.substring(i, j); i = j;
            return n.contains(".") || n.contains("e") || n.contains("E") ? (Object) Double.valueOf(n) : (Object) Long.valueOf(n);
        }
        String string() {
            expect('"'); StringBuilder sb = new StringBuilder();
            while (s.charAt(i) != '"') {
                char c = s.charAt(i++);
                if (c == '\\') { char e = s.charAt(i++); switch (e) { case 'n': sb.append('\n'); break; case 't': sb.append('\t'); break; case 'r': sb.append('\r'); break; case 'u': sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16)); i += 4; break; default: sb.append(e); } }
                else sb.append(c);
            }
            i++; return sb.toString();
        }
        void ws() { while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++; }
        void expect(char c) { if (s.charAt(i) != c) throw new IllegalStateException("expected " + c + " at " + i); i++; }
    }

    static void fail(String why) { System.err.println("FAIL: " + why); System.exit(1); }

    @SuppressWarnings("unchecked")
    static void verify2(String pass, byte[] bytes, boolean checkPattern) throws Exception {
        byte[] plain;
        if (Arrays.equals(Arrays.copyOf(bytes, 8), MAGIC2)) plain = open2(bytes, pass);
        else if (Arrays.equals(Arrays.copyOf(bytes, 8), MAGIC)) {
            int headerSize = MAGIC.length + 4 + 16;
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, key(pass, Arrays.copyOfRange(bytes, MAGIC.length + 4, headerSize), ByteBuffer.wrap(bytes, MAGIC.length, 4).getInt()), new GCMParameterSpec(128, Arrays.copyOfRange(bytes, headerSize, headerSize + 12)));
            c.updateAAD(Arrays.copyOfRange(bytes, 0, headerSize));
            plain = c.doFinal(bytes, headerSize + 12, bytes.length - headerSize - 12);
        } else plain = bytes;
        int nl = 0; while (plain[nl] != '\n') nl++;
        Map<String, Object> header = (Map<String, Object>) new Json(new String(plain, 0, nl, StandardCharsets.UTF_8)).value();
        if (!"expenses-backup".equals(header.get("format"))) fail("format " + header.get("format"));
        long version = (Long) header.get("version"), payloadBytes = (Long) header.get("payloadBytes");
        if (version < 1 || version > 2) fail("version " + version);
        int p0 = nl + 1, p1 = (int) (p0 + payloadBytes);
        if (p1 > plain.length) fail("payload is cut off");
        byte[] payload = Arrays.copyOfRange(plain, p0, p1);
        if (!sha256(payload).equals(header.get("sha256"))) fail("payload sha256 differs");
        Map<String, Object> data = (Map<String, Object>) new Json(new String(payload, StandardCharsets.UTF_8)).value();
        List<Object> list = version >= 2 && data.get("attachments") != null ? (List<Object>) data.get("attachments") : new ArrayList<>();
        int pos = p1; long total = 0; int k = 0;
        for (Object o : list) {
            Map<String, Object> att = (Map<String, Object>) o;
            k++;
            int size = ((Long) att.get("sizeBytes")).intValue();
            if (pos + size > plain.length) fail("file " + att.get("id") + " is cut off");
            byte[] f = Arrays.copyOfRange(plain, pos, pos + size);
            if (!sha256(f).equals(att.get("sha256"))) fail("file " + att.get("id") + " sha256 differs");
            if (checkPattern && !Arrays.equals(f, pattern(k, size))) fail("file " + att.get("id") + " is not the sample pattern");
            pos += size; total += size;
        }
        if (version == 1) { for (int i = p1; i < plain.length; i++) if (plain[i] != '\n' && plain[i] != '\r' && plain[i] != ' ') fail("version 1 file has data after the payload"); }
        else if (pos != plain.length) fail((plain.length - pos) + " bytes left after the last file");
        System.out.println("ok: version " + version + ", payload " + payloadBytes + " bytes (sha256 verified), " + list.size() + " files verified (" + total + " bytes)" + (checkPattern ? ", sample pattern matches" : ""));
    }

    public static void main(String[] a) throws Exception {
        String mode = a[0], pass = a[1];
        if (mode.equals("make2")) {
            byte[] plain = sample2();
            Files.write(Path.of(a[2]), pass.equals("-") ? plain : seal2(plain, pass, 64 * 1024));
            System.out.println("wrote " + a[2]);
            return;
        }
        if (mode.equals("verify2")) {
            verify2(pass.equals("-") ? "" : pass, Files.readAllBytes(Path.of(a[2])), a.length > 3 && a[3].equals("pattern"));
            return;
        }
        if (mode.equals("make")) {
            byte[] plain = Files.readAllBytes(Path.of(a[2]));
            byte[] salt = new byte[16], nonce = new byte[12];
            for (int i = 0; i < 16; i++) salt[i] = (byte) (i * 7 + 1);
            for (int i = 0; i < 12; i++) nonce[i] = (byte) (i * 11 + 3);
            byte[] header = ByteBuffer.allocate(MAGIC.length + 4 + 16).put(MAGIC).putInt(ITERATIONS).put(salt).array();
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, key(pass, salt, ITERATIONS), new GCMParameterSpec(128, nonce));
            c.updateAAD(header);
            byte[] body = c.doFinal(plain);
            Files.write(Path.of(a[3]), ByteBuffer.allocate(header.length + 12 + body.length).put(header).put(nonce).put(body).array());
        } else {
            byte[] bytes = Files.readAllBytes(Path.of(a[2]));
            int headerSize = MAGIC.length + 4 + 16;
            int iterations = ByteBuffer.wrap(bytes, MAGIC.length, 4).getInt();
            byte[] header = Arrays.copyOfRange(bytes, 0, headerSize);
            byte[] salt = Arrays.copyOfRange(bytes, MAGIC.length + 4, headerSize);
            byte[] nonce = Arrays.copyOfRange(bytes, headerSize, headerSize + 12);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, key(pass, salt, iterations), new GCMParameterSpec(128, nonce));
            c.updateAAD(header);
            byte[] plain = c.doFinal(bytes, headerSize + 12, bytes.length - headerSize - 12);
            if (!Arrays.equals(plain, Files.readAllBytes(Path.of(a[3])))) { System.err.println("plaintext differs"); System.exit(1); }
            System.out.println("ok: " + plain.length + " bytes decrypted");
        }
    }
}
