package com.itemreport.app.export;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.zip.Deflater;

/**
 * Minimal streaming PDF writer. JPEG photos are embedded byte-for-byte
 * (DCTDecode), so they keep their full resolution with no recompression.
 * Coordinates are in points with the origin at the top-left of the page.
 */
public final class PdfWriter implements Closeable {

    public static final class Image {
        final int obj;
        final String name;
        public final int width;
        public final int height;

        Image(int obj, String name, int width, int height) {
            this.obj = obj;
            this.name = name;
            this.width = width;
            this.height = height;
        }
    }

    /** Helvetica advance widths for characters 32..126. */
    private static final int[] WIDTHS = {
            278, 278, 355, 556, 556, 889, 667, 191, 333, 333, 389, 584, 278, 333, 278, 278,
            556, 556, 556, 556, 556, 556, 556, 556, 556, 556, 278, 278, 584, 584, 584, 556,
            1015, 667, 667, 722, 722, 667, 611, 778, 722, 278, 500, 667, 556, 833, 722, 778,
            667, 778, 722, 667, 611, 722, 667, 944, 667, 667, 611, 278, 278, 278, 469, 556,
            333, 556, 556, 500, 556, 556, 278, 556, 556, 222, 222, 500, 222, 833, 556, 556,
            556, 556, 333, 500, 278, 556, 500, 722, 500, 500, 500, 334, 260, 334, 584
    };

    private static final Charset CP1252 = Charset.forName("windows-1252");

    private final OutputStream out;
    private long pos = 0;
    private final ArrayList<Long> offsets = new ArrayList<>();
    private final ArrayList<Integer> pages = new ArrayList<>();
    private final String title;

    private StringBuilder content;
    private float pageW;
    private float pageH;
    private LinkedHashMap<String, Integer> pageImages;
    private int imageCount = 0;
    private boolean closed = false;

    public PdfWriter(OutputStream os, String title) throws IOException {
        this.out = new BufferedOutputStream(os, 1 << 16);
        this.title = title == null ? "" : title;
        offsets.add(0L);
        raw("%PDF-1.4\n%âãÏÓ\n");
        alloc(); // 1: catalog
        alloc(); // 2: pages
        int f1 = alloc();
        begin(f1);
        raw("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>\nendobj\n");
        int f2 = alloc();
        begin(f2);
        raw("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold /Encoding /WinAnsiEncoding >>\nendobj\n");
    }

    // ---------- text helpers ----------

    /** True when every character can be drawn with the built-in PDF fonts. */
    public static boolean canEncode(String s) {
        CharsetEncoder enc = CP1252.newEncoder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\n' || c == '\r' || c == '\t') continue;
            if (c < 32 || !enc.canEncode(c)) return false;
        }
        return true;
    }

    public static float textWidth(String s, float size, boolean bold) {
        long total = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            total += (c >= 32 && c <= 126) ? WIDTHS[c - 32] : 556;
        }
        float w = total * size / 1000f;
        return bold ? w * 1.09f : w;
    }

    // ---------- pages ----------

    public void newPage(float width, float height) throws IOException {
        endPage();
        pageW = width;
        pageH = height;
        content = new StringBuilder(4096);
        pageImages = new LinkedHashMap<>();
    }

    public void text(String s, float x, float yTop, float size, boolean bold, int rgb) {
        if (s == null || s.isEmpty()) return;
        float baseline = pageH - (yTop + size * 0.95f);
        content.append("BT /").append(bold ? "F2" : "F1").append(' ').append(n(size)).append(" Tf ")
                .append(color(rgb)).append(" rg 1 0 0 1 ").append(n(x)).append(' ').append(n(baseline))
                .append(" Tm (").append(escape(s)).append(") Tj ET\n");
    }

    public void fillRect(float x, float yTop, float w, float h, int rgb) {
        content.append(color(rgb)).append(" rg ").append(n(x)).append(' ').append(n(pageH - yTop - h))
                .append(' ').append(n(w)).append(' ').append(n(h)).append(" re f\n");
    }

    public void strokeRect(float x, float yTop, float w, float h, int rgb, float lineWidth) {
        content.append(color(rgb)).append(" RG ").append(n(lineWidth)).append(" w ")
                .append(n(x)).append(' ').append(n(pageH - yTop - h))
                .append(' ').append(n(w)).append(' ').append(n(h)).append(" re S\n");
    }

    public void line(float x1, float y1, float x2, float y2, int rgb, float lineWidth) {
        content.append(color(rgb)).append(" RG ").append(n(lineWidth)).append(" w ")
                .append(n(x1)).append(' ').append(n(pageH - y1)).append(" m ")
                .append(n(x2)).append(' ').append(n(pageH - y2)).append(" l S\n");
    }

    public void image(Image img, float x, float yTop, float w, float h) {
        pageImages.put(img.name, img.obj);
        content.append("q ").append(n(w)).append(" 0 0 ").append(n(h)).append(' ')
                .append(n(x)).append(' ').append(n(pageH - yTop - h)).append(" cm /")
                .append(img.name).append(" Do Q\n");
    }

    // ---------- images ----------

    public Image addJpeg(File file) throws IOException {
        JpegInfo info = JpegInfo.read(file);
        long len = file.length();
        Image img = beginImage(info, len);
        byte[] buf = new byte[1 << 16];
        long written = 0;
        try (InputStream in = new BufferedInputStream(new FileInputStream(file))) {
            int r;
            while ((r = in.read(buf)) > 0) {
                out.write(buf, 0, r);
                written += r;
            }
        }
        if (written != len) throw new IOException("Photo changed while exporting");
        pos += written;
        raw("\nendstream\nendobj\n");
        return img;
    }

    public Image addJpeg(byte[] bytes) throws IOException {
        JpegInfo info = JpegInfo.read(bytes);
        Image img = beginImage(info, bytes.length);
        out.write(bytes);
        pos += bytes.length;
        raw("\nendstream\nendobj\n");
        return img;
    }

    private Image beginImage(JpegInfo info, long length) throws IOException {
        String cs;
        String decode = "";
        if (info.components == 1) {
            cs = "/DeviceGray";
        } else if (info.components == 4) {
            cs = "/DeviceCMYK";
            decode = " /Decode [1 0 1 0 1 0 1 0]";
        } else {
            cs = "/DeviceRGB";
        }
        int obj = alloc();
        imageCount++;
        begin(obj);
        raw("<< /Type /XObject /Subtype /Image /Width " + info.width + " /Height " + info.height
                + " /ColorSpace " + cs + " /BitsPerComponent 8 /Filter /DCTDecode" + decode
                + " /Length " + length + " >>\nstream\n");
        return new Image(obj, "Im" + imageCount, info.width, info.height);
    }

    // ---------- structure ----------

    private void endPage() throws IOException {
        if (content == null) return;
        byte[] rawBytes = content.toString().getBytes(StandardCharsets.ISO_8859_1);
        byte[] packed = deflate(rawBytes);
        int c = alloc();
        begin(c);
        raw("<< /Length " + packed.length + " /Filter /FlateDecode >>\nstream\n");
        out.write(packed);
        pos += packed.length;
        raw("\nendstream\nendobj\n");

        StringBuilder sb = new StringBuilder();
        sb.append("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 ").append(n(pageW)).append(' ')
                .append(n(pageH)).append("] /Resources << /Font << /F1 3 0 R /F2 4 0 R >>");
        if (!pageImages.isEmpty()) {
            sb.append(" /XObject <<");
            for (Map.Entry<String, Integer> e : pageImages.entrySet()) {
                sb.append(" /").append(e.getKey()).append(' ').append(e.getValue()).append(" 0 R");
            }
            sb.append(" >>");
        }
        sb.append(" >> /Contents ").append(c).append(" 0 R >>\nendobj\n");
        int p = alloc();
        begin(p);
        raw(sb.toString());
        pages.add(p);
        content = null;
        pageImages = null;
    }

    @Override
    public void close() throws IOException {
        if (closed) return;
        closed = true;
        endPage();

        StringBuilder kids = new StringBuilder();
        for (int p : pages) kids.append(p).append(" 0 R ");
        begin(2);
        raw("<< /Type /Pages /Kids [ " + kids + "] /Count " + pages.size() + " >>\nendobj\n");
        begin(1);
        raw("<< /Type /Catalog /Pages 2 0 R >>\nendobj\n");
        int info = alloc();
        begin(info);
        raw("<< /Title (" + escape(title) + ") /Producer (Item Report) >>\nendobj\n");

        long xref = pos;
        int count = offsets.size();
        StringBuilder sb = new StringBuilder();
        sb.append("xref\n0 ").append(count).append('\n');
        sb.append("0000000000 65535 f \n");
        for (int i = 1; i < count; i++) {
            sb.append(String.format(Locale.US, "%010d 00000 n \n", offsets.get(i)));
        }
        sb.append("trailer\n<< /Size ").append(count).append(" /Root 1 0 R /Info ").append(info)
                .append(" 0 R >>\nstartxref\n").append(xref).append("\n%%EOF\n");
        raw(sb.toString());
        out.flush();
        out.close();
    }

    private int alloc() {
        offsets.add(-1L);
        return offsets.size() - 1;
    }

    private void begin(int obj) throws IOException {
        offsets.set(obj, pos);
        raw(obj + " 0 obj\n");
    }

    private void raw(String s) throws IOException {
        byte[] b = s.getBytes(StandardCharsets.ISO_8859_1);
        out.write(b);
        pos += b.length;
    }

    private static byte[] deflate(byte[] data) {
        Deflater d = new Deflater(Deflater.DEFAULT_COMPRESSION);
        d.setInput(data);
        d.finish();
        ByteArrayOutputStream bos = new ByteArrayOutputStream(data.length / 2 + 64);
        byte[] buf = new byte[8192];
        while (!d.finished()) {
            int r = d.deflate(buf);
            bos.write(buf, 0, r);
        }
        d.end();
        return bos.toByteArray();
    }

    private static String n(float v) {
        return String.format(Locale.US, "%.2f", v);
    }

    private static String color(int rgb) {
        return n(((rgb >> 16) & 0xFF) / 255f) + ' ' + n(((rgb >> 8) & 0xFF) / 255f) + ' '
                + n((rgb & 0xFF) / 255f);
    }

    private static String escape(String s) {
        StringBuilder clean = new StringBuilder(s.length());
        CharsetEncoder enc = CP1252.newEncoder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\n' || c == '\r' || c == '\t') c = ' ';
            clean.append((c < 32 || !enc.canEncode(c)) ? '?' : c);
        }
        byte[] bytes = clean.toString().getBytes(CP1252);
        StringBuilder sb = new StringBuilder(bytes.length + 8);
        for (byte value : bytes) {
            int b = value & 0xFF;
            if (b == '(' || b == ')' || b == '\\') {
                sb.append('\\').append((char) b);
            } else if (b < 32 || b > 126) {
                sb.append('\\').append(String.format(Locale.US, "%03o", b));
            } else {
                sb.append((char) b);
            }
        }
        return sb.toString();
    }
}
