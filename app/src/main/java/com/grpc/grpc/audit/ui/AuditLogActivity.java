package com.grpc.grpc.audit.ui;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.grpc.grpc.BuildConfig;
import com.grpc.grpc.R;
import com.grpc.grpc.audit.adapter.AuditLogAdapter;
import com.grpc.grpc.audit.data.AuditLogRepository;
import com.grpc.grpc.audit.model.AuditLogEntry;
import com.grpc.grpc.core.DemoFirebaseExpiryHelper;
import com.grpc.grpc.core.FirestorePaths;
import com.grpc.grpc.core.SessionManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;

/**
 * Read-only company activity. Admin and super_admin only.
 */
public class AuditLogActivity extends AppCompatActivity {

    private static final String ALL_USERS = "All Users";
    private static final String ALL_ACTIVITY = "All Activity";
    private static final String RANGE_TODAY = "today";
    private static final String RANGE_7 = "7";
    private static final String RANGE_ALL = "all";

    private final List<AuditLogEntry> allEntries = new ArrayList<>();
    private final List<AuditLogEntry> visibleEntries = new ArrayList<>();
    private AuditLogAdapter adapter;
    private TextView emptyView;
    private String range = RANGE_TODAY;
    private String userFilter = ALL_USERS;
    private String moduleFilter = "";
    private final List<String> staffNames = new ArrayList<>();
    @Nullable
    private Spinner userSpinner;
    @Nullable
    private ListenerRegistration registration;
    private boolean deleting;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (BuildConfig.IS_OFFLINE) {
            Toast.makeText(this, R.string.audit_unavailable, Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        if (DemoFirebaseExpiryHelper.finishIfBlocked(this)) return;
        setContentView(R.layout.activity_audit_logs);
        emptyView = findViewById(R.id.auditEmpty);
        ListView listView = findViewById(R.id.auditList);
        adapter = new AuditLogAdapter(this);
        listView.setAdapter(adapter);
        listView.setOnItemClickListener((parent, view, position, id) -> {
            AuditLogAdapter.Row row = adapter.getItem(position);
            if (row == null || row.header || row.entry == null) return;
            showDetail(row.entry);
        });

        findViewById(R.id.auditToday).setOnClickListener(v -> setRange(RANGE_TODAY));
        findViewById(R.id.auditSeven).setOnClickListener(v -> setRange(RANGE_7));
        findViewById(R.id.auditAll).setOnClickListener(v -> setRange(RANGE_ALL));
        findViewById(R.id.auditDeleteAll).setOnClickListener(v -> confirmDeleteAll());
        findViewById(R.id.auditSavePdf).setOnClickListener(v -> savePdf());
        findViewById(R.id.auditSaveExcel).setOnClickListener(v -> saveExcel());

        Spinner users = findViewById(R.id.auditUserFilter);
        userSpinner = users;
        Spinner activity = findViewById(R.id.auditActivityFilter);
        users.setOnItemSelectedListener(simpleSelect(value -> {
            userFilter = value;
            render();
        }));
        activity.setOnItemSelectedListener(simpleSelect(value -> {
            moduleFilter = moduleForLabel(value);
            render();
        }));
        bindActivitySpinner(activity);

        SessionManager.ensureLoaded(this, session -> runOnUiThread(() -> {
            if (session == null || !session.isAdmin) {
                finish();
                return;
            }
            if (registration != null) registration.remove();
            registration = AuditLogRepository.listenNewest((entries, error) -> runOnUiThread(() -> {
                if (error != null) {
                    Toast.makeText(this, R.string.audit_load_failed, Toast.LENGTH_LONG).show();
                    return;
                }
                allEntries.clear();
                if (entries != null) allEntries.addAll(entries);
                render();
            }));
            loadStaffNames();
        }));
    }

    @Override
    protected void onDestroy() {
        if (registration != null) registration.remove();
        super.onDestroy();
    }

    private void setRange(String next) {
        range = next;
        styleRange();
        render();
    }

    private void confirmDeleteAll() {
        if (deleting) return;
        new AlertDialog.Builder(this)
                .setTitle(R.string.audit_delete_title)
                .setMessage(R.string.audit_delete_message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.audit_delete_confirm, (dialog, which) -> deleteAll())
                .show();
    }

    private void deleteAll() {
        if (deleting) return;
        deleting = true;
        View deleteButton = findViewById(R.id.auditDeleteAll);
        if (deleteButton != null) deleteButton.setEnabled(false);
        AuditLogRepository.deleteAll(success -> runOnUiThread(() -> {
            deleting = false;
            if (isFinishing()) return;
            if (deleteButton != null) deleteButton.setEnabled(true);
            Toast.makeText(this, success ? R.string.audit_deleted : R.string.audit_delete_failed, Toast.LENGTH_LONG).show();
        }));
    }

    private void styleRange() {
        tint(R.id.auditToday, RANGE_TODAY.equals(range));
        tint(R.id.auditSeven, RANGE_7.equals(range));
        tint(R.id.auditAll, RANGE_ALL.equals(range));
    }

    private void tint(int id, boolean selected) {
        Button button = findViewById(id);
        if (button == null) return;
        button.setAlpha(selected ? 1f : 0.55f);
    }

    private void bindActivitySpinner(Spinner spinner) {
        String[] labels = new String[]{
                ALL_ACTIVITY, "Reports", "Contracts", "Job Work", "Management", "Quotations", "Billing", "Stock"
        };
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, labels);
        spinner.setAdapter(adapter);
    }

    private void loadStaffNames() {
        FirebaseFirestore.getInstance()
                .collection(FirestorePaths.USERS)
                .get()
                .addOnSuccessListener(snapshot -> runOnUiThread(() -> {
                    if (isFinishing() || snapshot == null) return;
                    Set<String> names = new LinkedHashSet<>();
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        if (doc == null || doc.getId() == null || doc.getId().matches("\\d{3}")) continue;
                        String contractKey = firstText(doc, "contractKey", "ContractKey", "contract_key");
                        if (contractKey.isEmpty()) continue;
                        String name = firstText(doc, "name", "Name");
                        if (name.isEmpty()) continue;
                        names.add(name);
                    }
                    staffNames.clear();
                    staffNames.addAll(names);
                    Collections.sort(staffNames, String.CASE_INSENSITIVE_ORDER);
                    if (userSpinner != null) bindUserSpinner(userSpinner);
                }));
    }

    private static String firstText(DocumentSnapshot doc, String... keys) {
        for (String key : keys) {
            String value = doc.getString(key);
            if (value != null && !value.trim().isEmpty()) return value.trim();
        }
        return "";
    }

    private void bindUserSpinner(Spinner spinner) {
        String previous = userFilter;
        List<String> options = new ArrayList<>();
        options.add(ALL_USERS);
        options.addAll(staffNames);
        ArrayAdapter<String> nameAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, options);
        spinner.setOnItemSelectedListener(null);
        spinner.setAdapter(nameAdapter);
        int index = options.indexOf(previous);
        spinner.setSelection(index >= 0 ? index : 0);
        userFilter = index >= 0 ? previous : ALL_USERS;
        spinner.setOnItemSelectedListener(simpleSelect(value -> {
            userFilter = value;
            render();
        }));
    }

    private void render() {
        styleRange();
        long cutoff = cutoffMillis();
        List<AuditLogAdapter.Row> rows = new ArrayList<>();
        visibleEntries.clear();
        String lastDate = "";
        for (AuditLogEntry entry : allEntries) {
            if (entry.createdAt == null) continue;
            if (cutoff > 0 && entry.createdAt.getTime() < cutoff) continue;
            if (!ALL_USERS.equals(userFilter) && !userFilter.equals(entry.displayName())) continue;
            if (!moduleFilter.isEmpty() && !moduleFilter.equals(entry.module)) continue;
            String date = entry.displayDate();
            if (!date.equals(lastDate)) {
                rows.add(AuditLogAdapter.Row.header(date));
                lastDate = date;
            }
            rows.add(AuditLogAdapter.Row.entry(entry));
            visibleEntries.add(entry);
        }
        adapter.setRows(rows);
        boolean empty = rows.isEmpty();
        emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
    }

    private long cutoffMillis() {
        if (RANGE_ALL.equals(range)) return 0L;
        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("Europe/Dublin"), Locale.UK);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        if (RANGE_7.equals(range)) calendar.add(Calendar.DAY_OF_YEAR, -6);
        return calendar.getTimeInMillis();
    }

    private boolean hasRowsToSave() {
        if (!visibleEntries.isEmpty()) return true;
        Toast.makeText(this, R.string.audit_export_empty, Toast.LENGTH_SHORT).show();
        return false;
    }

    @Nullable
    private File exportFile(String extension) {
        File root = getExternalFilesDir(null);
        if (root == null) return null;
        File dir = new File(root, "BEHINDS LIST");
        if (!dir.exists() && !dir.mkdirs()) return null;
        String stamp = new SimpleDateFormat("dd-MM-yy_HHmmss", Locale.UK).format(new Date());
        return new File(dir, "Audit_log_" + stamp + extension);
    }

    private void savePdf() {
        if (!hasRowsToSave()) return;
        List<String[]> rows = snapshotRows(false);
        File file = exportFile(".pdf");
        if (file == null) {
            Toast.makeText(this, R.string.audit_export_failed, Toast.LENGTH_SHORT).show();
            return;
        }
        setExportEnabled(false);
        new Thread(() -> {
            boolean saved = writePdf(file, rows);
            runOnUiThread(() -> finishExport(saved, file));
        }).start();
    }

    private void saveExcel() {
        if (!hasRowsToSave()) return;
        List<String[]> rows = snapshotRows(true);
        File file = exportFile(".xlsx");
        if (file == null) {
            Toast.makeText(this, R.string.audit_export_failed, Toast.LENGTH_SHORT).show();
            return;
        }
        setExportEnabled(false);
        new Thread(() -> {
            boolean saved = writeExcel(file, rows);
            runOnUiThread(() -> finishExport(saved, file));
        }).start();
    }

    private void finishExport(boolean saved, File file) {
        if (isFinishing()) return;
        setExportEnabled(true);
        if (!saved) {
            if (file.exists() && !file.delete()) {
                file.deleteOnExit();
            }
            Toast.makeText(this, R.string.audit_export_failed, Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(this, R.string.audit_export_saved, Toast.LENGTH_LONG).show();
    }

    private void setExportEnabled(boolean enabled) {
        View pdf = findViewById(R.id.auditSavePdf);
        View excel = findViewById(R.id.auditSaveExcel);
        if (pdf != null) pdf.setEnabled(enabled);
        if (excel != null) excel.setEnabled(enabled);
    }

    private List<String[]> snapshotRows(boolean includeModule) {
        List<String[]> rows = new ArrayList<>();
        for (AuditLogEntry entry : visibleEntries) {
            if (includeModule) {
                rows.add(new String[]{
                        entry.displayDate(),
                        entry.displayTime(),
                        entry.displayName(),
                        entry.displayAction(),
                        entry.displayDetail(),
                        entry.module
                });
            } else {
                rows.add(new String[]{
                        entry.displayDate(),
                        entry.displayTime(),
                        entry.displayName(),
                        entry.displayAction(),
                        entry.displayDetail()
                });
            }
        }
        return rows;
    }

    private boolean writePdf(File file, List<String[]> rows) {
        PdfDocument document = new PdfDocument();
        try {
            Paint titlePaint = new Paint();
            titlePaint.setColor(Color.BLACK);
            titlePaint.setTextSize(16);
            titlePaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            Paint body = new Paint();
            body.setColor(Color.BLACK);
            body.setTextSize(10);
            Paint header = new Paint(body);
            header.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            int pageWidth = 842;
            int pageHeight = 595;
            int margin = 28;
            int y = 0;
            int pageNumber = 1;
            PdfDocument.Page page = null;
            Canvas canvas = null;
            for (String[] row : rows) {
                if (page == null || y > pageHeight - margin - 18) {
                    if (page != null) document.finishPage(page);
                    PdfDocument.PageInfo info = new PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create();
                    page = document.startPage(info);
                    canvas = page.getCanvas();
                    y = margin + 16;
                    canvas.drawText("Audit Logs", margin, y, titlePaint);
                    y += 22;
                    canvas.drawText("Date", margin, y, header);
                    canvas.drawText("Time", margin + 80, y, header);
                    canvas.drawText("User", margin + 140, y, header);
                    canvas.drawText("Action", margin + 280, y, header);
                    canvas.drawText("Detail", margin + 460, y, header);
                    y += 16;
                    pageNumber++;
                }
                if (canvas == null) return false;
                canvas.drawText(pdfText(row, 0, 12), margin, y, body);
                canvas.drawText(pdfText(row, 1, 8), margin + 80, y, body);
                canvas.drawText(pdfText(row, 2, 22), margin + 140, y, body);
                canvas.drawText(pdfText(row, 3, 28), margin + 280, y, body);
                canvas.drawText(pdfText(row, 4, 48), margin + 460, y, body);
                y += 16;
            }
            if (page != null) document.finishPage(page);
            try (FileOutputStream out = new FileOutputStream(file)) {
                document.writeTo(out);
            }
            return true;
        } catch (Throwable ignored) {
            return false;
        } finally {
            document.close();
        }
    }

    private static String pdfText(String[] row, int index, int max) {
        String value = row != null && index < row.length ? row[index] : "";
        String text = clip(value, max);
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= 32 && c != 127) out.append(c);
        }
        return out.toString();
    }

    private boolean writeExcel(File file, List<String[]> rows) {
        String[] headers = {"Date", "Time", "User", "Action", "Detail", "Module"};
        List<List<String>> body = new ArrayList<>();
        for (String[] row : rows) {
            List<String> line = new ArrayList<>();
            for (int i = 0; i < headers.length; i++) {
                line.add(row != null && i < row.length && row[i] != null ? row[i] : "");
            }
            body.add(line);
        }
        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(file))) {
            writeZipEntry(zip, "[Content_Types].xml",
                    "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                            + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
                            + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
                            + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
                            + "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>"
                            + "<Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>"
                            + "<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>"
                            + "</Types>");
            writeZipEntry(zip, "_rels/.rels",
                    "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                            + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                            + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>"
                            + "</Relationships>");
            writeZipEntry(zip, "xl/workbook.xml",
                    "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                            + "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" "
                            + "xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">"
                            + "<sheets><sheet name=\"Audit Logs\" sheetId=\"1\" r:id=\"rId1\"/></sheets>"
                            + "</workbook>");
            writeZipEntry(zip, "xl/_rels/workbook.xml.rels",
                    "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                            + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                            + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/>"
                            + "<Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>"
                            + "</Relationships>");
            writeZipEntry(zip, "xl/styles.xml",
                    "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                            + "<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">"
                            + "<fonts count=\"1\"><font><sz val=\"11\"/><name val=\"Calibri\"/></font></fonts>"
                            + "<fills count=\"1\"><fill><patternFill patternType=\"none\"/></fill></fills>"
                            + "<borders count=\"1\"><border/></borders>"
                            + "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>"
                            + "<cellXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/></cellXfs>"
                            + "</styleSheet>");
            List<String> headerList = new ArrayList<>();
            Collections.addAll(headerList, headers);
            writeZipEntry(zip, "xl/worksheets/sheet1.xml", buildSheetXml(headerList, body));
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void writeZipEntry(ZipOutputStream zip, String name, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static String buildSheetXml(List<String> headers, List<List<String>> rows) {
        StringBuilder sheet = new StringBuilder();
        sheet.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        sheet.append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>");
        appendXlsxRow(sheet, 1, headers);
        for (int i = 0; i < rows.size(); i++) {
            appendXlsxRow(sheet, i + 2, rows.get(i));
        }
        sheet.append("</sheetData></worksheet>");
        return sheet.toString();
    }

    private static void appendXlsxRow(StringBuilder sheet, int rowIndex, List<String> values) {
        sheet.append("<row r=\"").append(rowIndex).append("\">");
        for (int i = 0; i < values.size(); i++) {
            String cellRef = excelColumn(i + 1) + rowIndex;
            sheet.append("<c r=\"").append(cellRef).append("\" t=\"inlineStr\"><is><t xml:space=\"preserve\">")
                    .append(escapeExcel(values.get(i)))
                    .append("</t></is></c>");
        }
        sheet.append("</row>");
    }

    private static String excelColumn(int columnNumber) {
        StringBuilder name = new StringBuilder();
        int column = columnNumber;
        while (column > 0) {
            column--;
            name.insert(0, (char) ('A' + (column % 26)));
            column /= 26;
        }
        return name.toString();
    }

    private static String escapeExcel(String value) {
        String excelValue = value != null ? value : "";
        return excelValue
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    private static String clip(String value, int max) {
        if (value == null) return "";
        String text = value.replace('\n', ' ').trim();
        if (text.length() <= max) return text;
        return text.substring(0, Math.max(0, max - 3)) + "...";
    }

    private void showDetail(AuditLogEntry entry) {
        StringBuilder message = new StringBuilder();
        message.append("User:\n").append(entry.displayName()).append("\n\n");
        message.append("Action:\n").append(entry.eventLabel.isEmpty() ? entry.displayAction() : entry.eventLabel).append("\n\n");
        if (!entry.fileName.isEmpty()) {
            message.append("Report:\n").append(entry.fileName).append("\n\n");
        }
        if (!entry.entityName.isEmpty() && !entry.entityName.equals(entry.fileName)) {
            message.append("Site:\n").append(entry.entityName).append("\n\n");
        }
        message.append("Date:\n").append(entry.displayDate().isEmpty() ? "Not recorded yet" : entry.displayDate()).append("\n\n");
        message.append("Time:\n").append(entry.displayTime().isEmpty() ? "Not recorded yet" : entry.displayTime());
        new AlertDialog.Builder(this)
                .setTitle("Audit Entry")
                .setMessage(message.toString().trim())
                .setPositiveButton("Close", null)
                .show();
    }

    private static String moduleForLabel(String label) {
        if ("Reports".equals(label)) return "reports";
        if ("Contracts".equals(label)) return "contracts";
        if ("Job Work".equals(label)) return "job_work";
        if ("Management".equals(label)) return "management";
        if ("Quotations".equals(label)) return "quotations";
        if ("Billing".equals(label)) return "billing";
        if ("Stock".equals(label)) return "stock";
        return "";
    }

    private interface ValueListener {
        void onValue(String value);
    }

    private AdapterView.OnItemSelectedListener simpleSelect(ValueListener listener) {
        return new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                Object item = parent.getItemAtPosition(position);
                listener.onValue(item == null ? "" : item.toString());
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        };
    }
}
