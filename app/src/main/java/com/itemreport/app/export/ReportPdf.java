package com.itemreport.app.export;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;

/** Lays the report out on A4 pages. */
public final class ReportPdf {

    /** Draws text the built-in PDF fonts cannot (Arabic, Hindi, CJK, emoji...). */
    public interface TextRasterizer {
        Raster render(String text, float sizePt, boolean bold, float maxWidthPt);
    }

    public static final class Raster {
        public final byte[] jpeg;
        public final float widthPt;
        public final float heightPt;

        public Raster(byte[] jpeg, float widthPt, float heightPt) {
            this.jpeg = jpeg;
            this.widthPt = widthPt;
            this.heightPt = heightPt;
        }
    }

    private static final float PW = 595.28f;
    private static final float PH = 841.89f;
    private static final float M = 40f;
    private static final float CW = PW - 2 * M;
    private static final float BOTTOM = PH - 48f;

    private static final int INK = 0x1B1F24;
    private static final int MUTED = 0x6B7280;
    private static final int ACCENT = 0x0F5C6E;
    private static final int RULE = 0xD5D9DE;
    private static final int SOFT = 0xF1F3F5;
    private static final int WHITE = 0xFFFFFF;

    private static final class Block {
        ArrayList<String> lines;
        Raster raster;
        float size;
        float lineH;
        float height;
        boolean bold;
        int color;
    }

    private final PdfWriter pdf;
    private final ReportData data;
    private final TextRasterizer rz;
    private int pageNo = 0;
    private float y;

    private ReportPdf(PdfWriter pdf, ReportData data, TextRasterizer rz) {
        this.pdf = pdf;
        this.data = data;
        this.rz = rz;
    }

    public static void write(OutputStream os, ReportData data, boolean largePhotos,
                             TextRasterizer rz) throws IOException {
        PdfWriter pdf = new PdfWriter(os, data.title);
        ReportPdf r = new ReportPdf(pdf, data, rz);
        r.newPage();
        if (data.items.isEmpty()) {
            pdf.text("No items in this report.", M, r.y, 11, false, MUTED);
        }
        boolean first = true;
        for (ExportItem it : data.items) {
            if (largePhotos) {
                if (!first) r.newPage();
                r.item(it, true);
            } else {
                r.item(it, false);
            }
            first = false;
        }
        pdf.close();
    }

    private void newPage() throws IOException {
        pdf.newPage(PW, PH);
        pageNo++;
        if (pageNo == 1) {
            Block t = prep(data.title, CW, 20, true, INK, 2);
            draw(t, M, M);
            float yy = M + t.height + 4;
            StringBuilder sub = new StringBuilder();
            sub.append("Generated ").append(data.generated);
            sub.append("   |   ").append(data.items.size())
                    .append(data.items.size() == 1 ? " item" : " items");
            if (data.prefix != null && !data.prefix.isEmpty()) {
                sub.append("   |   ID prefix ").append(data.prefix);
            }
            Block s = prep(sub.toString(), CW, 9, false, MUTED, 1);
            draw(s, M, yy);
            yy += s.height + 8;
            pdf.line(M, yy, M + CW, yy, ACCENT, 1.5f);
            y = yy + 14;
        } else {
            Block t = prep(data.title, CW, 9, false, MUTED, 1);
            draw(t, M, M - 8);
            pdf.line(M, M + 8, M + CW, M + 8, RULE, 0.5f);
            y = M + 22;
        }
        String pg = "Page " + pageNo;
        pdf.text(pg, M + CW - PdfWriter.textWidth(pg, 8, false), PH - 34, 8, false, MUTED);
        pdf.text("Item Report", M, PH - 34, 8, false, MUTED);
    }

    private void item(ExportItem it, boolean large) throws IOException {
        float photoW = large ? CW : 185f;
        float photoH = large ? 400f : 150f;
        float gap = 16f;
        float fx = large ? M : M + photoW + gap;
        float fw = large ? CW : CW - photoW - gap;
        float labelW = large ? 110f : 78f;

        String uid = it.uid == null ? "" : it.uid;
        float chipW = PdfWriter.textWidth(uid, 10, true) + 14;
        Block name = prep(it.name, CW - chipW - 10, 11.5f, true, INK, 2);
        float headH = Math.max(18f, name.height) + 8;

        ArrayList<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"Description", it.description});
        rows.add(new String[]{"Area used in", it.area});
        rows.add(new String[]{"Quantity", it.quantity});
        rows.add(new String[]{"Dimensions", it.dimensionsText});
        rows.add(new String[]{"Weight", it.weightText});
        rows.add(new String[]{"Storage", it.locationText});
        rows.add(new String[]{"Condition", it.condition});
        rows.add(new String[]{"Condition as of", it.conditionAt});
        if (it.photoTaken != null && !it.photoTaken.isEmpty()) {
            rows.add(new String[]{"Photo taken", it.photoTaken});
        }

        boolean hasQr = it.qr != null && it.qr.length > 0;
        float qrSize = 66f;
        float valueW = fw - labelW - (hasQr ? qrSize + 10 : 0);

        ArrayList<Block> values = new ArrayList<>();
        float fieldsH = 0;
        for (String[] row : rows) {
            Block v = prep(row[1], valueW, 9.5f, false, INK, 12);
            values.add(v);
            fieldsH += Math.max(v.height, 12f) + 4;
        }
        if (hasQr) fieldsH = Math.max(fieldsH, qrSize + 4);

        float total = large ? headH + photoH + 14 + fieldsH + 10
                : headH + Math.max(photoH, fieldsH) + 22;
        if (y + total > BOTTOM && y > M + 60) newPage();

        // heading: ID chip + item name
        pdf.fillRect(M, y, chipW, 18, ACCENT);
        pdf.text(uid, M + 7, y + 3.5f, 10, true, WHITE);
        draw(name, M + chipW + 10, y + 1.5f);
        y += headH;

        // photo
        float top = y;
        PdfWriter.Image img = null;
        if (it.photoPath != null) {
            File f = new File(it.photoPath);
            if (f.isFile()) {
                try {
                    img = pdf.addJpeg(f);
                } catch (IOException e) {
                    img = null;
                }
            }
        }
        pdf.fillRect(M, top, photoW, photoH, SOFT);
        if (img != null) {
            float scale = Math.min(photoW / img.width, photoH / img.height);
            float dw = img.width * scale;
            float dh = img.height * scale;
            pdf.image(img, M + (photoW - dw) / 2, top + (photoH - dh) / 2, dw, dh);
        } else {
            String np = "No photo";
            pdf.text(np, M + (photoW - PdfWriter.textWidth(np, 9, false)) / 2,
                    top + photoH / 2 - 6, 9, false, MUTED);
        }

        // fields
        float fy = large ? top + photoH + 14 : top;
        float fieldsTop = fy;
        if (hasQr) drawQr(it.qr, M + CW - qrSize, fieldsTop, qrSize);
        for (int i = 0; i < rows.size(); i++) {
            Block v = values.get(i);
            pdf.text(rows.get(i)[0], fx, fy + 1, 8, false, MUTED);
            draw(v, fx + labelW, fy);
            fy += Math.max(v.height, 12f) + 4;
        }

        if (hasQr) fy = Math.max(fy, fieldsTop + qrSize + 4);
        float end = large ? fy : Math.max(top + photoH, fy);
        y = end + 10;
        if (!large) {
            pdf.line(M, y, M + CW, y, RULE, 0.5f);
            y += 12;
        }
    }

    private void drawQr(boolean[][] qr, float x, float yTop, float size) {
        int n = qr.length;
        float m = size / n;
        pdf.fillRect(x, yTop, size, size, WHITE);
        for (int row = 0; row < n; row++) {
            boolean[] line = qr[row];
            int col = 0;
            while (col < line.length) {
                if (!line[col]) {
                    col++;
                    continue;
                }
                int start = col;
                while (col < line.length && line[col]) col++;
                // Slight overlap hides hairline gaps between neighbouring modules.
                pdf.fillRect(x + start * m, yTop + row * m, (col - start) * m + 0.03f, m + 0.03f, 0x000000);
            }
        }
    }

    // ---------- text blocks ----------

    private Block prep(String text, float maxW, float size, boolean bold, int color, int maxLines) {
        Block b = new Block();
        b.size = size;
        b.bold = bold;
        b.color = color;
        b.lineH = size * 1.32f;
        if (text == null || text.trim().isEmpty()) text = "-";
        text = text.trim();
        if (!PdfWriter.canEncode(text) && rz != null) {
            Raster r = null;
            try {
                r = rz.render(text, size, bold, maxW);
            } catch (RuntimeException e) {
                r = null;
            }
            if (r != null && r.jpeg != null && r.widthPt > 0 && r.heightPt > 0) {
                b.raster = r;
                float scale = r.widthPt > maxW ? maxW / r.widthPt : 1f;
                b.height = r.heightPt * scale;
                return b;
            }
        }
        b.lines = wrap(text, maxW, size, bold, maxLines);
        b.height = b.lines.size() * b.lineH;
        return b;
    }

    private void draw(Block b, float x, float yTop) throws IOException {
        if (b.raster != null) {
            PdfWriter.Image img = pdf.addJpeg(b.raster.jpeg);
            float w = b.raster.widthPt;
            float scale = b.height / b.raster.heightPt;
            pdf.image(img, x, yTop, w * scale, b.height);
            return;
        }
        for (int i = 0; i < b.lines.size(); i++) {
            pdf.text(b.lines.get(i), x, yTop + i * b.lineH, b.size, b.bold, b.color);
        }
    }

    private static ArrayList<String> wrap(String text, float maxW, float size, boolean bold,
                                          int maxLines) {
        ArrayList<String> lines = new ArrayList<>();
        String[] paragraphs = text.replace("\r", "").split("\n");
        for (String para : paragraphs) {
            String[] words = para.trim().split("\\s+");
            StringBuilder cur = new StringBuilder();
            for (String word : words) {
                if (word.isEmpty()) continue;
                while (PdfWriter.textWidth(word, size, bold) > maxW && word.length() > 1) {
                    if (cur.length() > 0) {
                        lines.add(cur.toString());
                        cur.setLength(0);
                    }
                    int cut = word.length() - 1;
                    while (cut > 1 && PdfWriter.textWidth(word.substring(0, cut), size, bold) > maxW) {
                        cut--;
                    }
                    lines.add(word.substring(0, cut));
                    word = word.substring(cut);
                }
                String trial = cur.length() == 0 ? word : cur + " " + word;
                if (PdfWriter.textWidth(trial, size, bold) <= maxW) {
                    cur.setLength(0);
                    cur.append(trial);
                } else {
                    lines.add(cur.toString());
                    cur.setLength(0);
                    cur.append(word);
                }
            }
            if (cur.length() > 0) lines.add(cur.toString());
        }
        if (lines.isEmpty()) lines.add("-");
        if (lines.size() > maxLines) {
            ArrayList<String> cut = new ArrayList<>(lines.subList(0, maxLines));
            String last = cut.get(maxLines - 1);
            while (last.length() > 1 && PdfWriter.textWidth(last + "...", size, bold) > maxW) {
                last = last.substring(0, last.length() - 1);
            }
            cut.set(maxLines - 1, last + "...");
            return cut;
        }
        return lines;
    }
}
