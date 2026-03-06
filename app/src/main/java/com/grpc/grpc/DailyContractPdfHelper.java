package com.grpc.grpc;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;


import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * On first login every 24 hours: automatically generate behinds list and due list PDFs
 * for the logged-in user and notify them in-app. Admin/oversight users may generate combined PDFs.
 */
public final class DailyContractPdfHelper {

    private static final String PREFS_NAME = "GRPC";
    private static final String PREFIX_LAST_RUN = "DAILY_PDF_LAST_";
    private static final long INTERVAL_MS = 24 * 60 * 60 * 1000L;

    /**
     * Call from MainActivity after user is set. Schedules a check: if last run was more than 24h ago
     * (or never), runs PDF generation on a background thread and then writes an in-app notification.
     */
    public static void scheduleDailyPdfIfNeeded(Context context, String userName) {
        if (context == null || userName == null || userName.trim().isEmpty()) return;
        Handler mainHandler = new Handler(Looper.getMainLooper());
        mainHandler.postDelayed(() -> {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            String key = PREFIX_LAST_RUN + userName.trim().toLowerCase(Locale.getDefault());
            long lastRun = prefs.getLong(key, 0L);
            long now = System.currentTimeMillis();
            if (lastRun > 0 && (now - lastRun) < INTERVAL_MS) {
                return; // Already ran in last 24h
            }
            Executors.newSingleThreadExecutor().execute(() -> runDailyPdfAndNotify(context, userName, prefs, key, now));
        }, 3000); // Delay so app UI is ready
    }

    private static void runDailyPdfAndNotify(Context context, String userName, SharedPreferences prefs, String prefsKey, long runTime) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        List<String> techniciansToRun = new ArrayList<>();

        // RBAC:
        // - Admin (or SeesAllJobs flag) gets combined PDFs.
        // - Tech gets own only.
        SessionManager.ensureLoaded(context, null);
        if (SessionManager.seesAllJobs(context)) {
            try {
                for (StaffDirectory.StaffProfile p : StaffDirectory.getCachedStaffProfiles()) {
                    if (p == null) continue;
                    String ck = p.contractKey != null ? p.contractKey.trim() : "";
                    if (!ck.isEmpty()) techniciansToRun.add(ck);
                }
            } catch (Exception ignored) {}
            if (techniciansToRun.isEmpty()) {
                // Fallback: don't guess other technicians; run only for the current user.
                techniciansToRun.add(userName);
            }
        } else {
            // Use current user's StaffID -> ContractKey when possible.
            String staffId = SessionManager.getStaffId(context);
            if (staffId != null && !staffId.trim().isEmpty()) {
                techniciansToRun.add(StaffDirectory.getContractsCollectionName(staffId).replace(" Contracts", ""));
            } else {
                techniciansToRun.add(userName);
            }
        }

        List<String> generatedFor = new ArrayList<>();
        for (String technician : techniciansToRun) {
            String collectionName = technician + " Contracts";
            try {
                List<Map<String, Object>> contracts = new ArrayList<>();
                QuerySnapshot result = Tasks.await(db.collection(collectionName).get(), 30, TimeUnit.SECONDS);
                if (result != null) {
                    for (QueryDocumentSnapshot doc : result) {
                        Map<String, Object> contract = doc.getData();
                        if (contract != null) {
                            contract.put("documentId", doc.getId());
                            contract.put("owner", technician);
                            contracts.add(contract);
                        }
                    }
                }

                List<Map<String, Object>> behinds = new ArrayList<>();
                List<Map<String, Object>> due = new ArrayList<>();
                for (Map<String, Object> c : contracts) {
                    String nextVisit = calculateNextVisit(c);
                    if (isPastDue(nextVisit)) behinds.add(c);
                    else if (isDueSoon(nextVisit)) due.add(c);
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    if (!behinds.isEmpty()) {
                        File f = BehindsListPDFGenerator.generateBehindsListPDF(technician, behinds, context);
                        if (f != null) generatedFor.add(technician + " (behinds)");
                    }
                    if (!due.isEmpty()) {
                        File f = DueListPDFGenerator.generateDueListPDF(technician, due, context);
                        if (f != null) generatedFor.add(technician + " (due)");
                    }
                }
            } catch (Exception e) {
                Log.e("DailyContractPdfHelper", "Error generating PDFs for " + technician, e);
            }
        }

        prefs.edit().putLong(prefsKey, runTime).apply();

        // On 31 December each year, pre-create next year's ReportsYY folder with month subfolders in Firebase Storage.
        try {
            Calendar cal = Calendar.getInstance();
            cal.setTimeInMillis(runTime);
            int year = cal.get(Calendar.YEAR);
            int month = cal.get(Calendar.MONTH);
            int day = cal.get(Calendar.DAY_OF_MONTH);
            if (month == Calendar.DECEMBER && day == 31) {
                int nextYear = year + 1;
                String yy = String.format(Locale.getDefault(), "%02d", nextYear % 100);
                String base = "Reports" + yy;

                com.google.firebase.storage.FirebaseStorage storage = com.google.firebase.storage.FirebaseStorage.getInstance();
                com.google.firebase.storage.StorageReference root = storage.getReference();

                String[] months = new String[] {
                        "January","February","March","April","May","June",
                        "July","August","September","October","November","December"
                };
                byte[] empty = new byte[0];
                for (String m : months) {
                    root.child(base + "/" + m + "/.keep").putBytes(empty);
                }
            }
        } catch (Exception ignored) {}

        // Always notify the user that the daily job ran (so they see it in Notifications).
        String title = "Daily reports generated";
        String body;
        if (!generatedFor.isEmpty()) {
            String summary = String.join(", ", generatedFor);
            body = "Behinds list and due list PDFs have been created for: " + summary + ". Open Contracts to view or share.";
        } else {
            body = "Daily report check complete. No behinds or due contracts; no new PDFs were generated. Open Contracts to view existing lists.";
        }
        Map<String, Object> data = new HashMap<>();
        data.put("source", "daily_pdf");
        data.put("technicians", generatedFor.isEmpty() ? "" : String.join(", ", generatedFor));

        String userKey = NotificationUtils.resolveNotificationRecipientKey(userName);
        if (!userKey.isEmpty()) {
            String docId = "daily_pdf_" + runTime;
            Map<String, Object> notif = new HashMap<>();
            notif.put("title", title);
            notif.put("body", body);
            notif.put("type", "daily_pdf");
            notif.put("data", data);
            notif.put("timestamp", FieldValue.serverTimestamp());
            notif.put("read", false);
            try {
                Tasks.await(
                    db.collection("notifications").document(userKey).collection("items").document(docId).set(notif),
                    10, TimeUnit.SECONDS
                );
            } catch (Exception e) {
                Log.e("DailyContractPdfHelper", "Failed to write daily PDF notification", e);
                // Fallback: fire-and-forget so at least one attempt is made
                NotificationUtils.writeInAppNotification(userName, docId, title, body, "daily_pdf", data);
            }
        }
    }

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("dd/MM/yy", Locale.getDefault());

    private static String calculateNextVisit(Map<String, Object> contract) {
        String lastVisit = contract.get("lastVisit") != null ? contract.get("lastVisit").toString() : "N/A";
        int visits = 0;
        try {
            Object v = contract.get("visits");
            visits = v != null ? Integer.parseInt(v.toString()) : 0;
        } catch (NumberFormatException ignored) {}
        if ("N/A".equals(lastVisit) || visits == 0) return "N/A";
        try {
            Calendar cal = Calendar.getInstance();
            cal.setTime(DATE_FORMAT.parse(lastVisit));
            switch (visits) {
                case 8: cal.add(Calendar.WEEK_OF_YEAR, 6); break;
                case 12: cal.add(Calendar.WEEK_OF_YEAR, 4); break;
                case 6: cal.add(Calendar.WEEK_OF_YEAR, 8); break;
                case 4: cal.add(Calendar.WEEK_OF_YEAR, 12); break;
                default: return "N/A";
            }
            return DATE_FORMAT.format(cal.getTime());
        } catch (Exception e) {
            return "N/A";
        }
    }

    private static boolean isPastDue(String nextVisit) {
        if (nextVisit == null || nextVisit.trim().isEmpty() || "N/A".equalsIgnoreCase(nextVisit)) return true;
        try {
            Date d = DATE_FORMAT.parse(nextVisit);
            return d != null && d.before(new Date());
        } catch (Exception e) {
            return true;
        }
    }

    private static boolean isDueSoon(String nextVisit) {
        if (nextVisit == null || nextVisit.trim().isEmpty() || "N/A".equalsIgnoreCase(nextVisit)) return false;
        try {
            Date next = DATE_FORMAT.parse(nextVisit);
            if (next == null) return false;
            Date now = new Date();
            Calendar cal = Calendar.getInstance();
            cal.setTime(now);
            cal.add(Calendar.DAY_OF_YEAR, 7);
            Date sevenDays = cal.getTime();
            return !next.before(now) && !next.after(sevenDays);
        } catch (Exception e) {
            return false;
        }
    }
}
