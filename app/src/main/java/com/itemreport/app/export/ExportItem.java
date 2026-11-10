package com.itemreport.app.export;

/** One item row, already formatted for output. */
public final class ExportItem {
    public String uid = "";
    public String name = "";
    public String description = "";
    public String area = "";
    public String quantity = "";
    public String length = "";
    public String width = "";
    public String height = "";
    public String dimUnit = "";
    public String weight = "";
    public String weightUnit = "";
    public String aisle = "";
    public String rack = "";
    public String shelf = "";
    public String condition = "";
    public String conditionAt = "";
    public String photoTaken = "";
    /** Absolute path of the JPEG to embed, or null. */
    public String photoPath;

    /** Combined text used in the PDF. */
    public String dimensionsText = "";
    public String weightText = "";
    public String locationText = "";

    /** QR code modules, [row][column], true = dark. Includes the quiet zone. May be null. */
    public boolean[][] qr;
}
