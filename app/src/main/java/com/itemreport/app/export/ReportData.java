package com.itemreport.app.export;

import java.util.ArrayList;

public final class ReportData {
    public String title = "";
    public String prefix = "";
    public String created = "";
    public String generated = "";
    public final ArrayList<ExportItem> items = new ArrayList<>();
}
