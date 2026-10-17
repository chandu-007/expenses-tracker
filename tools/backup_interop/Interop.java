import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
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
 */
public class Interop {
    static final byte[] MAGIC = "EXPENC1\n".getBytes(StandardCharsets.UTF_8);
    static final int ITERATIONS = 600_000;

    static SecretKeySpec key(String pass, byte[] salt, int iterations) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(pass.toCharArray(), salt, iterations, 256);
        return new SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded(), "AES");
    }

    public static void main(String[] a) throws Exception {
        String mode = a[0], pass = a[1];
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
