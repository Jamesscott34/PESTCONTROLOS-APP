package com.grpc.grpc.workview.data;

import com.grpc.grpc.workview.model.WorkEvent;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Local cache for WorkView events used for OFFLINE popup reminder scheduling/firing.
 *
 * This cache is best-effort: it is populated whenever we see/schedule events while online,
 * and allows WorkManager reminders to be scheduled again without Firestore access.
 */
public final class WorkViewLocalEventStore {
    private WorkViewLocalEventStore() {}

    private static final String PREFS = "workview_popup_cache_v1";

    public static final class CachedEvent {
        public final String collection;
        public final WorkEvent event;

        public CachedEvent(String collection, WorkEvent event) {
            this.collection = collection;
            this.event = event;
        }
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static String userKey(String userName) {
        return (userName == null ? "" : userName.trim().toLowerCase(Locale.getDefault()));
    }

    private static String key(String userName, String eventId) {
        return userKey(userName) + "__" + (eventId == null ? "" : eventId.trim());
    }

    public static void upsert(Context context, String currentUserName, String collection, WorkEvent ev) {
        if (context == null || ev == null) return;
        if (TextUtils.isEmpty(currentUserName)) return;
        if (TextUtils.isEmpty(ev.getId())) return;

        try {
            JSONObject j = new JSONObject();
            j.put("id", ev.getId());
            j.put("date", safe(ev.getDate()));
            j.put("time", safe(ev.getTime()));
            j.put("endTime", safe(ev.getEndTime()));
            j.put("status", safe(ev.getStatus()));
            j.put("eventType", safe(ev.getEventType()));
            j.put("eventName", safe(ev.getEventName()));
            j.put("address", safe(ev.getAddress()));
            j.put("userName", safe(ev.getUserName())); // assigned tech (optional)
            j.put("collection", safe(collection));
            j.put("updatedAtMs", System.currentTimeMillis());

            prefs(context).edit().putString(key(currentUserName, ev.getId()), j.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    public static void remove(Context context, String currentUserName, String eventId) {
        if (context == null) return;
        if (TextUtils.isEmpty(currentUserName) || TextUtils.isEmpty(eventId)) return;
        prefs(context).edit().remove(key(currentUserName, eventId)).apply();
    }

    /** Clears all cached WorkView reminder data (required on logout/shared devices). */
    public static void clearAll(Context context) {
        if (context == null) return;
        try {
            prefs(context).edit().clear().apply();
        } catch (Exception ignored) {}
    }

    public static boolean shouldShowPopup(Context context,
                                         String currentUserName,
                                         String eventId,
                                         String expectedDate,
                                         String expectedTime) {
        if (context == null) return true;
        if (TextUtils.isEmpty(currentUserName) || TextUtils.isEmpty(eventId)) return true;

        try {
            String raw = prefs(context).getString(key(currentUserName, eventId), null);
            if (raw == null) return true; // best-effort: don't block the popup if cache is missing
            JSONObject j = new JSONObject(raw);

            String status = safe(j.optString("status", ""));
            if (!TextUtils.isEmpty(status) && !"scheduled".equalsIgnoreCase(status)) return false;

            String curDate = safe(j.optString("date", ""));
            String curTime = safe(j.optString("time", ""));
            if (!TextUtils.isEmpty(expectedDate) && !TextUtils.equals(expectedDate, curDate)) return false;
            if (!TextUtils.isEmpty(expectedTime) && !TextUtils.equals(expectedTime, curTime)) return false;

            String type = safe(j.optString("eventType", ""));
            if (!TextUtils.isEmpty(type) && !"job".equalsIgnoreCase(type) && !"contract".equalsIgnoreCase(type)) {
                return false;
            }

            return true;
        } catch (Exception ignored) {
            return true;
        }
    }

    public static List<CachedEvent> listUpcomingScheduled(Context context, String currentUserName, int lookaheadDays) {
        List<CachedEvent> out = new ArrayList<>();
        if (context == null) return out;
        if (TextUtils.isEmpty(currentUserName)) return out;

        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            String today = sdf.format(new Date());
            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.DAY_OF_YEAR, Math.max(1, lookaheadDays));
            String end = sdf.format(cal.getTime());

            String prefix = userKey(currentUserName) + "__";
            Map<String, ?> all = prefs(context).getAll();
            for (Map.Entry<String, ?> e : all.entrySet()) {
                String k = e.getKey();
                if (k == null || !k.startsWith(prefix)) continue;
                Object v = e.getValue();
                if (!(v instanceof String)) continue;

                try {
                    JSONObject j = new JSONObject((String) v);
                    String status = safe(j.optString("status", ""));
                    if (!"scheduled".equalsIgnoreCase(status)) continue;

                    String type = safe(j.optString("eventType", ""));
                    if (!"job".equalsIgnoreCase(type) && !"contract".equalsIgnoreCase(type)) continue;

                    String date = safe(j.optString("date", ""));
                    if (TextUtils.isEmpty(date)) continue;
                    // yyyy-MM-dd is lexicographically comparable
                    if (date.compareTo(today) < 0) continue;
                    if (date.compareTo(end) > 0) continue;

                    WorkEvent ev = new WorkEvent();
                    ev.setId(safe(j.optString("id", "")));
                    ev.setDate(date);
                    ev.setTime(safe(j.optString("time", "")));
                    ev.setEndTime(safe(j.optString("endTime", "")));
                    ev.setStatus(status);
                    ev.setEventType(type);
                    ev.setEventName(safe(j.optString("eventName", "")));
                    ev.setAddress(safe(j.optString("address", "")));
                    ev.setUserName(safe(j.optString("userName", "")));

                    String collection = safe(j.optString("collection", ""));
                    if (TextUtils.isEmpty(collection)) {
                        collection = userKey(currentUserName) + "_workview";
                    }

                    if (!TextUtils.isEmpty(ev.getId())) {
                        out.add(new CachedEvent(collection, ev));
                    }
                } catch (Exception ignored) {
                }
            }
        } catch (Exception ignored) {
        }

        return out;
    }

    private static String safe(String s) {
        return s == null ? "" : s.trim();
    }
}
