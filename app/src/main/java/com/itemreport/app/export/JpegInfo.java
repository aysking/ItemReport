package com.itemreport.app.export;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/** Reads width, height and component count from a JPEG header. */
public final class JpegInfo {
    public final int width;
    public final int height;
    public final int components;

    private JpegInfo(int width, int height, int components) {
        this.width = width;
        this.height = height;
        this.components = components;
    }

    public static JpegInfo read(File file) throws IOException {
        try (InputStream in = new BufferedInputStream(new FileInputStream(file))) {
            return read(in);
        }
    }

    public static JpegInfo read(byte[] bytes) throws IOException {
        return read(new ByteArrayInputStream(bytes));
    }

    public static JpegInfo read(InputStream stream) throws IOException {
        DataInputStream in = new DataInputStream(stream);
        if (in.readUnsignedByte() != 0xFF || in.readUnsignedByte() != 0xD8) {
            throw new IOException("Not a JPEG image");
        }
        while (true) {
            int b = in.readUnsignedByte();
            if (b != 0xFF) continue;
            int marker = in.readUnsignedByte();
            while (marker == 0xFF) marker = in.readUnsignedByte();
            if (marker == 0x00 || marker == 0x01 || (marker >= 0xD0 && marker <= 0xD8)) continue;
            if (marker == 0xD9 || marker == 0xDA) throw new IOException("JPEG has no size header");
            int len = in.readUnsignedShort();
            boolean sof = marker >= 0xC0 && marker <= 0xCF
                    && marker != 0xC4 && marker != 0xC8 && marker != 0xCC;
            if (sof) {
                in.readUnsignedByte();
                int h = in.readUnsignedShort();
                int w = in.readUnsignedShort();
                int c = in.readUnsignedByte();
                if (w <= 0 || h <= 0) throw new IOException("Bad JPEG size");
                return new JpegInfo(w, h, c);
            }
            int skip = len - 2;
            while (skip > 0) {
                int n = in.skipBytes(skip);
                if (n <= 0) {
                    in.readUnsignedByte();
                    n = 1;
                }
                skip -= n;
            }
        }
    }
}
