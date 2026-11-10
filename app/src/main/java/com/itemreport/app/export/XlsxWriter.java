package com.itemreport.app.export;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Writes an .xlsx workbook with one row per item. Photos are stored inside the
 * workbook byte-for-byte (full resolution) and shown scaled down in the Photo column.
 */
public final class XlsxWriter {

    private static final String[] HEADERS = {
            "ID", "Photo", "QR code", "Item name", "Short description", "Area used in", "Quantity",
            "Length", "Width", "Height", "Dim. unit", "Weight", "Weight unit",
            "Aisle", "Rack", "Shelf", "Condition", "Condition as of", "Photo taken"
    };
    private static final double[] COL_WIDTHS = {
            14, 28, 21, 26, 40, 20, 10, 10, 10, 10, 10, 10, 11, 10, 10, 10, 16, 20, 20
    };

    private static final int EMU = 9525;
    private static final int BOX_W = 189;
    private static final int BOX_H = 140;
    private static final int PAD = 6;
    private static final int QR_BOX = 132;
    private static final int QR_SCALE = 10;

    private XlsxWriter() {
    }

    private static final class Pic {
        final File file;
        final byte[] bytes;
        final String ext;
        final int row;
        final int col;
        final int w;
        final int h;

        Pic(File file, byte[] bytes, String ext, int row, int col, int w, int h) {
            this.file = file;
            this.bytes = bytes;
            this.ext = ext;
            this.row = row;
            this.col = col;
            this.w = w;
            this.h = h;
        }
    }

    /**
     * @param inCell true: pictures are placed inside the cells (Excel "Place in Cell");
     *               false: pictures float over the cells (works in every spreadsheet app).
     */
    public static void write(OutputStream os, ReportData data, boolean inCell) throws IOException {
        ArrayList<Pic> pics = new ArrayList<>();
        StringBuilder rows = new StringBuilder();

        rows.append("<row r=\"1\" ht=\"22\" customHeight=\"1\">");
        for (int c = 0; c < HEADERS.length; c++) text(rows, c, 1, HEADERS[c], 1);
        rows.append("</row>");

        int r = 2;
        for (ExportItem it : data.items) {
            boolean hasPic = false;
            if (it.photoPath != null) {
                File f = new File(it.photoPath);
                if (f.isFile()) {
                    try {
                        JpegInfo info = JpegInfo.read(f);
                        pics.add(new Pic(f, null, "jpeg", r, 1, info.width, info.height));
                        hasPic = true;
                    } catch (IOException ignored) {
                        hasPic = false;
                    }
                }
            }
            rows.append("<row r=\"").append(r).append("\" ht=\"114\" customHeight=\"1\">");
            text(rows, 0, r, it.uid, 4);
            if (hasPic && inCell) picCell(rows, 1, r, pics.size());
            else text(rows, 1, r, hasPic ? "" : "No photo", 3);
            boolean hasQr = it.qr != null && it.qr.length > 0;
            if (hasQr) {
                int side = it.qr.length * QR_SCALE;
                pics.add(new Pic(null, PngWriter.fromMatrix(it.qr, QR_SCALE), "png", r, 2, side, side));
            }
            if (hasQr && inCell) picCell(rows, 2, r, pics.size());
            else text(rows, 2, r, "", 3);
            text(rows, 3, r, it.name, 2);
            text(rows, 4, r, it.description, 2);
            text(rows, 5, r, it.area, 2);
            value(rows, 6, r, it.quantity);
            value(rows, 7, r, it.length);
            value(rows, 8, r, it.width);
            value(rows, 9, r, it.height);
            text(rows, 10, r, it.dimUnit, 3);
            value(rows, 11, r, it.weight);
            text(rows, 12, r, it.weightUnit, 3);
            text(rows, 13, r, it.aisle, 3);
            text(rows, 14, r, it.rack, 3);
            text(rows, 15, r, it.shelf, 3);
            text(rows, 16, r, it.condition, 2);
            text(rows, 17, r, it.conditionAt, 2);
            text(rows, 18, r, it.photoTaken, 2);
            rows.append("</row>");
            r++;
        }
        int lastRow = r - 1;

        try (ZipOutputStream zip = new ZipOutputStream(new BufferedOutputStream(os, 1 << 16))) {
            boolean rich = inCell && !pics.isEmpty();
            boolean floating = !inCell && !pics.isEmpty();
            entry(zip, "[Content_Types].xml", contentTypes(floating, rich));
            entry(zip, "_rels/.rels",
                    "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                            + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                            + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>"
                            + "</Relationships>");
            entry(zip, "xl/workbook.xml",
                    "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                            + "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">"
                            + "<sheets><sheet name=\"Items\" sheetId=\"1\" r:id=\"rId1\"/>"
                            + "<sheet name=\"Report info\" sheetId=\"2\" r:id=\"rId2\"/></sheets></workbook>");
            entry(zip, "xl/_rels/workbook.xml.rels",
                    "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                            + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                            + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/>"
                            + "<Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet2.xml\"/>"
                            + "<Relationship Id=\"rId3\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>"
                            + (rich
                            ? "<Relationship Id=\"rId4\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/sheetMetadata\" Target=\"metadata.xml\"/>"
                            + "<Relationship Id=\"rId5\" Type=\"http://schemas.microsoft.com/office/2022/10/relationships/richValueRel\" Target=\"richData/richValueRel.xml\"/>"
                            + "<Relationship Id=\"rId6\" Type=\"http://schemas.microsoft.com/office/2017/06/relationships/rdRichValue\" Target=\"richData/rdrichvalue.xml\"/>"
                            + "<Relationship Id=\"rId7\" Type=\"http://schemas.microsoft.com/office/2017/06/relationships/rdRichValueStructure\" Target=\"richData/rdrichvaluestructure.xml\"/>"
                            + "<Relationship Id=\"rId8\" Type=\"http://schemas.microsoft.com/office/2017/06/relationships/rdRichValueTypes\" Target=\"richData/rdRichValueTypes.xml\"/>"
                            : "")
                            + "</Relationships>");
            entry(zip, "xl/styles.xml", styles());
            entry(zip, "xl/worksheets/sheet1.xml", sheet1(rows, lastRow, floating));
            entry(zip, "xl/worksheets/sheet2.xml", sheet2(data));

            if (floating) {
                entry(zip, "xl/worksheets/_rels/sheet1.xml.rels",
                        "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                                + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                                + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/drawing\" Target=\"../drawings/drawing1.xml\"/>"
                                + "</Relationships>");
                entry(zip, "xl/drawings/drawing1.xml", drawing(pics));
                entry(zip, "xl/drawings/_rels/drawing1.xml.rels", imageRels(pics));
            }
            if (rich) {
                int n = pics.size();
                StringBuilder meta = new StringBuilder();
                meta.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
                        .append("<metadata xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:xlrd=\"http://schemas.microsoft.com/office/spreadsheetml/2017/richdata\">")
                        .append("<metadataTypes count=\"1\"><metadataType name=\"XLRICHVALUE\" minSupportedVersion=\"120000\" copy=\"1\" pasteAll=\"1\" pasteValues=\"1\" merge=\"1\" splitFirst=\"1\" rowColShift=\"1\" clearFormats=\"1\" clearComments=\"1\" assign=\"1\" coerce=\"1\"/></metadataTypes>")
                        .append("<futureMetadata name=\"XLRICHVALUE\" count=\"").append(n).append("\">");
                for (int i = 0; i < n; i++) {
                    meta.append("<bk><extLst><ext uri=\"{3e2802c4-a4d2-4d8b-9148-e3be6c30e623}\"><xlrd:rvb i=\"")
                            .append(i).append("\"/></ext></extLst></bk>");
                }
                meta.append("</futureMetadata><valueMetadata count=\"").append(n).append("\">");
                for (int i = 0; i < n; i++) {
                    meta.append("<bk><rc t=\"1\" v=\"").append(i).append("\"/></bk>");
                }
                meta.append("</valueMetadata></metadata>");
                entry(zip, "xl/metadata.xml", meta.toString());

                StringBuilder rv = new StringBuilder();
                rv.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
                        .append("<rvData xmlns=\"http://schemas.microsoft.com/office/spreadsheetml/2017/richdata\" count=\"")
                        .append(n).append("\">");
                for (int i = 0; i < n; i++) {
                    rv.append("<rv s=\"0\"><v>").append(i).append("</v><v>5</v></rv>");
                }
                rv.append("</rvData>");
                entry(zip, "xl/richData/rdrichvalue.xml", rv.toString());
                entry(zip, "xl/richData/rdrichvaluestructure.xml",
                        "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                                + "<rvStructures xmlns=\"http://schemas.microsoft.com/office/spreadsheetml/2017/richdata\" count=\"1\">"
                                + "<s t=\"_localImage\"><k n=\"_rvRel:LocalImageIdentifier\" t=\"i\"/><k n=\"CalcOrigin\" t=\"i\"/></s></rvStructures>");
                StringBuilder types = new StringBuilder();
                types.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
                        .append("<rvTypesInfo xmlns=\"http://schemas.microsoft.com/office/spreadsheetml/2017/richdata2\" xmlns:mc=\"http://schemas.openxmlformats.org/markup-compatibility/2006\" mc:Ignorable=\"x\" xmlns:x=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">")
                        .append("<global><keyFlags><key name=\"_Self\"><flag name=\"ExcludeFromFile\" value=\"1\"/><flag name=\"ExcludeFromCalcComparison\" value=\"1\"/></key>");
                String[] keys = {"_DisplayString", "_Flags", "_Format", "_SubLabel", "_Attribution",
                        "_Icon", "_Display", "_CanonicalPropertyNames", "_ClassificationId"};
                for (String k : keys) {
                    types.append("<key name=\"").append(k)
                            .append("\"><flag name=\"ExcludeFromCalcComparison\" value=\"1\"/></key>");
                }
                types.append("</keyFlags></global></rvTypesInfo>");
                entry(zip, "xl/richData/rdRichValueTypes.xml", types.toString());

                StringBuilder rel = new StringBuilder();
                rel.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
                        .append("<richValueRels xmlns=\"http://schemas.microsoft.com/office/spreadsheetml/2022/richvaluerel\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">");
                for (int i = 0; i < n; i++) rel.append("<rel r:id=\"rId").append(i + 1).append("\"/>");
                rel.append("</richValueRels>");
                entry(zip, "xl/richData/richValueRel.xml", rel.toString());
                entry(zip, "xl/richData/_rels/richValueRel.xml.rels", imageRels(pics));
            }
            if (!pics.isEmpty()) {
                byte[] buf = new byte[1 << 16];
                for (int i = 0; i < pics.size(); i++) {
                    Pic p = pics.get(i);
                    zip.putNextEntry(new ZipEntry("xl/media/image" + (i + 1) + "." + p.ext));
                    if (p.bytes != null) {
                        zip.write(p.bytes);
                    } else {
                        try (InputStream in = new BufferedInputStream(new FileInputStream(p.file))) {
                            int n;
                            while ((n = in.read(buf)) > 0) zip.write(buf, 0, n);
                        }
                    }
                    zip.closeEntry();
                }
            }
        }
    }

    private static String imageRels(ArrayList<Pic> pics) {
        StringBuilder rel = new StringBuilder();
        rel.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
                .append("<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">");
        for (int i = 0; i < pics.size(); i++) {
            rel.append("<Relationship Id=\"rId").append(i + 1)
                    .append("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/image\" Target=\"../media/image")
                    .append(i + 1).append('.').append(pics.get(i).ext).append("\"/>");
        }
        rel.append("</Relationships>");
        return rel.toString();
    }

    /** A cell holding picture number {@code index} (1-based) as an in-cell image. */
    private static void picCell(StringBuilder sb, int col, int row, int index) {
        sb.append("<c r=\"").append(colName(col)).append(row).append("\" s=\"3\" t=\"e\" vm=\"")
                .append(index).append("\"><v>#VALUE!</v></c>");
    }

    private static String contentTypes(boolean images, boolean rich) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
                .append("<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">")
                .append("<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>")
                .append("<Default Extension=\"xml\" ContentType=\"application/xml\"/>")
                .append("<Default Extension=\"jpeg\" ContentType=\"image/jpeg\"/>")
                .append("<Default Extension=\"png\" ContentType=\"image/png\"/>")
                .append("<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>")
                .append("<Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>")
                .append("<Override PartName=\"/xl/worksheets/sheet2.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>")
                .append("<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>");
        if (images) {
            sb.append("<Override PartName=\"/xl/drawings/drawing1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.drawing+xml\"/>");
        }
        if (rich) {
            sb.append("<Override PartName=\"/xl/metadata.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheetMetadata+xml\"/>")
                    .append("<Override PartName=\"/xl/richData/rdRichValueTypes.xml\" ContentType=\"application/vnd.ms-excel.rdrichvaluetypes+xml\"/>")
                    .append("<Override PartName=\"/xl/richData/rdrichvalue.xml\" ContentType=\"application/vnd.ms-excel.rdrichvalue+xml\"/>")
                    .append("<Override PartName=\"/xl/richData/rdrichvaluestructure.xml\" ContentType=\"application/vnd.ms-excel.rdrichvaluestructure+xml\"/>")
                    .append("<Override PartName=\"/xl/richData/richValueRel.xml\" ContentType=\"application/vnd.ms-excel.richvaluerel+xml\"/>");
        }
        sb.append("</Types>");
        return sb.toString();
    }

    /** Styles: 0 default, 1 header, 2 wrapped text, 3 centred text, 4 bold ID. */
    private static String styles() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">"
                + "<fonts count=\"3\">"
                + "<font><sz val=\"11\"/><name val=\"Calibri\"/></font>"
                + "<font><b/><sz val=\"11\"/><color rgb=\"FFFFFFFF\"/><name val=\"Calibri\"/></font>"
                + "<font><b/><sz val=\"11\"/><name val=\"Calibri\"/></font>"
                + "</fonts>"
                + "<fills count=\"3\">"
                + "<fill><patternFill patternType=\"none\"/></fill>"
                + "<fill><patternFill patternType=\"gray125\"/></fill>"
                + "<fill><patternFill patternType=\"solid\"><fgColor rgb=\"FF0F5C6E\"/><bgColor indexed=\"64\"/></patternFill></fill>"
                + "</fills>"
                + "<borders count=\"2\">"
                + "<border><left/><right/><top/><bottom/><diagonal/></border>"
                + "<border><left style=\"thin\"><color rgb=\"FFD5D9DE\"/></left><right style=\"thin\"><color rgb=\"FFD5D9DE\"/></right>"
                + "<top style=\"thin\"><color rgb=\"FFD5D9DE\"/></top><bottom style=\"thin\"><color rgb=\"FFD5D9DE\"/></bottom><diagonal/></border>"
                + "</borders>"
                + "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>"
                + "<cellXfs count=\"5\">"
                + "<xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/>"
                + "<xf numFmtId=\"0\" fontId=\"1\" fillId=\"2\" borderId=\"1\" xfId=\"0\" applyFont=\"1\" applyFill=\"1\" applyBorder=\"1\" applyAlignment=\"1\"><alignment horizontal=\"center\" vertical=\"center\" wrapText=\"1\"/></xf>"
                + "<xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"1\" xfId=\"0\" applyBorder=\"1\" applyAlignment=\"1\"><alignment horizontal=\"left\" vertical=\"center\" wrapText=\"1\"/></xf>"
                + "<xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"1\" xfId=\"0\" applyBorder=\"1\" applyAlignment=\"1\"><alignment horizontal=\"center\" vertical=\"center\" wrapText=\"1\"/></xf>"
                + "<xf numFmtId=\"0\" fontId=\"2\" fillId=\"0\" borderId=\"1\" xfId=\"0\" applyFont=\"1\" applyBorder=\"1\" applyAlignment=\"1\"><alignment horizontal=\"center\" vertical=\"center\" wrapText=\"1\"/></xf>"
                + "</cellXfs>"
                + "<cellStyles count=\"1\"><cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles>"
                + "</styleSheet>";
    }

    private static String sheet1(StringBuilder rows, int lastRow, boolean images) {
        StringBuilder sb = new StringBuilder(rows.length() + 2048);
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
                .append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">")
                .append("<sheetViews><sheetView tabSelected=\"1\" workbookViewId=\"0\">")
                .append("<pane ySplit=\"1\" topLeftCell=\"A2\" activePane=\"bottomLeft\" state=\"frozen\"/>")
                .append("</sheetView></sheetViews>")
                .append("<sheetFormatPr defaultRowHeight=\"15\"/><cols>");
        for (int c = 0; c < COL_WIDTHS.length; c++) {
            sb.append("<col min=\"").append(c + 1).append("\" max=\"").append(c + 1)
                    .append("\" width=\"").append(COL_WIDTHS[c]).append("\" customWidth=\"1\"/>");
        }
        sb.append("</cols><sheetData>").append(rows).append("</sheetData>");
        sb.append("<autoFilter ref=\"A1:").append(colName(HEADERS.length - 1))
                .append(Math.max(lastRow, 1)).append("\"/>");
        sb.append("<pageMargins left=\"0.4\" right=\"0.4\" top=\"0.5\" bottom=\"0.5\" header=\"0.3\" footer=\"0.3\"/>");
        sb.append("<pageSetup orientation=\"landscape\" fitToHeight=\"0\"/>");
        if (images) sb.append("<drawing r:id=\"rId1\"/>");
        sb.append("</worksheet>");
        return sb.toString();
    }

    private static String sheet2(ReportData data) {
        String[][] info = {
                {"Report", data.title},
                {"ID prefix", data.prefix},
                {"Report created", data.created},
                {"Exported", data.generated},
                {"Number of items", String.valueOf(data.items.size())}
        };
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
                .append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">")
                .append("<cols><col min=\"1\" max=\"1\" width=\"20\" customWidth=\"1\"/>")
                .append("<col min=\"2\" max=\"2\" width=\"48\" customWidth=\"1\"/></cols><sheetData>");
        for (int i = 0; i < info.length; i++) {
            int r = i + 1;
            sb.append("<row r=\"").append(r).append("\">");
            text(sb, 0, r, info[i][0], 4);
            if (i == 4) value(sb, 1, r, info[i][1]);
            else text(sb, 1, r, info[i][1], 2);
            sb.append("</row>");
        }
        sb.append("</sheetData></worksheet>");
        return sb.toString();
    }

    private static String drawing(ArrayList<Pic> pics) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
                .append("<xdr:wsDr xmlns:xdr=\"http://schemas.openxmlformats.org/drawingml/2006/spreadsheetDrawing\" ")
                .append("xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\" ")
                .append("xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">");
        for (int i = 0; i < pics.size(); i++) {
            Pic p = pics.get(i);
            int boxW = p.col == 1 ? BOX_W : QR_BOX;
            int boxH = p.col == 1 ? BOX_H : QR_BOX;
            double scale = Math.min((double) boxW / p.w, (double) boxH / p.h);
            long cx = Math.round(p.w * scale * EMU);
            long cy = Math.round(p.h * scale * EMU);
            long offX = (long) PAD * EMU + ((long) boxW * EMU - cx) / 2;
            long offY = (long) PAD * EMU + ((long) BOX_H * EMU - cy) / 2;
            sb.append("<xdr:oneCellAnchor><xdr:from><xdr:col>").append(p.col).append("</xdr:col><xdr:colOff>").append(offX)
                    .append("</xdr:colOff><xdr:row>").append(p.row - 1).append("</xdr:row><xdr:rowOff>")
                    .append(offY).append("</xdr:rowOff></xdr:from>")
                    .append("<xdr:ext cx=\"").append(cx).append("\" cy=\"").append(cy).append("\"/>")
                    .append("<xdr:pic><xdr:nvPicPr><xdr:cNvPr id=\"").append(i + 2)
                    .append("\" name=\"Picture ").append(i + 1).append("\"/>")
                    .append("<xdr:cNvPicPr><a:picLocks noChangeAspect=\"1\"/></xdr:cNvPicPr></xdr:nvPicPr>")
                    .append("<xdr:blipFill><a:blip r:embed=\"rId").append(i + 1)
                    .append("\"/><a:stretch><a:fillRect/></a:stretch></xdr:blipFill>")
                    .append("<xdr:spPr><a:xfrm><a:off x=\"0\" y=\"0\"/><a:ext cx=\"").append(cx)
                    .append("\" cy=\"").append(cy).append("\"/></a:xfrm>")
                    .append("<a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom></xdr:spPr>")
                    .append("</xdr:pic><xdr:clientData/></xdr:oneCellAnchor>");
        }
        sb.append("</xdr:wsDr>");
        return sb.toString();
    }

    // ---------- cells ----------

    private static void text(StringBuilder sb, int col, int row, String v, int style) {
        sb.append("<c r=\"").append(colName(col)).append(row).append("\" s=\"").append(style).append('"');
        if (v == null || v.isEmpty()) {
            sb.append("/>");
            return;
        }
        sb.append(" t=\"inlineStr\"><is><t xml:space=\"preserve\">").append(xml(v)).append("</t></is></c>");
    }

    /** Writes a number when the text is numeric, otherwise text. */
    private static void value(StringBuilder sb, int col, int row, String v) {
        String t = v == null ? "" : v.trim().replace(',', '.');
        if (t.matches("-?\\d{1,15}(\\.\\d{1,10})?")) {
            sb.append("<c r=\"").append(colName(col)).append(row).append("\" s=\"3\"><v>")
                    .append(t).append("</v></c>");
        } else {
            text(sb, col, row, v, 3);
        }
    }

    private static String colName(int index) {
        StringBuilder sb = new StringBuilder();
        int n = index + 1;
        while (n > 0) {
            int rem = (n - 1) % 26;
            sb.insert(0, (char) ('A' + rem));
            n = (n - 1) / 26;
        }
        return sb.toString();
    }

    private static String xml(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '&') sb.append("&amp;");
            else if (c == '<') sb.append("&lt;");
            else if (c == '>') sb.append("&gt;");
            else if (c == '"') sb.append("&quot;");
            else if (c == '\r') continue;
            else if (c < 32 && c != '\n' && c != '\t') sb.append(' ');
            else if (c == 0xFFFE || c == 0xFFFF) sb.append(' ');
            else sb.append(c);
        }
        return sb.toString();
    }

    private static void entry(ZipOutputStream zip, String name, String body) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(body.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
}
