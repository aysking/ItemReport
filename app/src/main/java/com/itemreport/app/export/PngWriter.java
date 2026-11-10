package com.itemreport.app.export;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.zip.CRC32;
import java.util.zip.Deflater;

/** Encodes a black-and-white module matrix (such as a QR code) as a PNG image. */
public final class PngWriter {

    private PngWriter() {
    }

    public static byte[] fromMatrix(boolean[][] dark, int scale) throws IOException {
        int n = dark.length;
        int side = n * scale;
        byte[] raw = new byte[(side + 1) * side];
        int pos = 0;
        for (int y = 0; y < side; y++) {
            raw[pos++] = 0;
            boolean[] row = dark[y / scale];
            for (int x = 0; x < side; x++) {
                int mx = x / scale;
                raw[pos++] = (byte) ((mx < row.length && row[mx]) ? 0 : 255);
            }
        }
        Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION);
        deflater.setInput(raw);
        deflater.finish();
        ByteArrayOutputStream packed = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        while (!deflater.finished()) {
            int r = deflater.deflate(buf);
            packed.write(buf, 0, r);
        }
        deflater.end();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A});
        ByteArrayOutputStream head = new ByteArrayOutputStream();
        DataOutputStream d = new DataOutputStream(head);
        d.writeInt(side);
        d.writeInt(side);
        d.writeByte(8); // bit depth
        d.writeByte(0); // greyscale
        d.writeByte(0);
        d.writeByte(0);
        d.writeByte(0);
        chunk(out, "IHDR", head.toByteArray());
        chunk(out, "IDAT", packed.toByteArray());
        chunk(out, "IEND", new byte[0]);
        return out.toByteArray();
    }

    private static void chunk(ByteArrayOutputStream out, String type, byte[] data) throws IOException {
        DataOutputStream d = new DataOutputStream(out);
        d.writeInt(data.length);
        byte[] t = type.getBytes("US-ASCII");
        d.write(t);
        d.write(data);
        CRC32 crc = new CRC32();
        crc.update(t);
        crc.update(data);
        d.writeInt((int) crc.getValue());
    }
}
