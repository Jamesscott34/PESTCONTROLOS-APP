package com.grpc.grpc;

import android.content.Context;
import android.content.SharedPreferences;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Reads WorkView jobs for widget display. Uses a persistent "today" cache so the widget
 * still shows that day's work after the user logs out. Cache is updated when WorkView
 * loads today's events (in WorkViewActivity). Also can read from WorkViewLocalEventStore
 * when user is logged in (next 3 upcoming).
 */
public final class WorkViewWidgetHelper {

    private static final String PREFS_NAME = "GRPC";
    private static final String KEY_USER_NAME = "USER_NAME";
    private static final String PREFS_WIDGET_CACHE = "WorkViewWidgetCache";
    private static final String KEY_CACHE_DATE = "cache_date";
    private static final String KEY_JOB_COUNT = "job_count";
    private static final String KEY_JOB_PREFIX = "job";
    private static final int LOOKAHEAD_DAYS = 14;
    private static final int MAX_JOBS = 5;
    private static final int MAX_CACHED_JOBS = 10;
    /** Number of jobs to show in the widget. */
    private static final int WIDGET_JOB_COUNT = 3;

    private WorkViewWidgetHelper() {}

    /**
     * Simple POJO for widget display (name + time + address).
     */
    public static class JobLine {
        public final String name;
        public final String time;
        public final String address;

        public JobLine(String name, String time, String address) {
            this.name = name != null ? name : "";
            this.time = time != null ? time : "";
            this.address = address != null ? address : "";
        }
    }

    /**
     * Date + jobs for widget. If today has no jobs, displayDate is the next day that has jobs and jobs are the next 3.
     */
    public static class WidgetDisplay {
        public final String displayDate;
        public final List<JobLine> jobs;

        public WidgetDisplay(String displayDate, List<JobLine> jobs) {
            this.displayDate = displayDate != null ? displayDate : "";
            this.jobs = jobs != null ? jobs : new ArrayList<JobLine>();
        }
    }

    /**
     * Returns the last user name stored by the app (from MainActivity / GRPC prefs).
     */
    public static String getLastUserName(Context context) {
        if (context == null) return "";
        SharedPreferences prefs = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String u = prefs.getString(KEY_USER_NAME, "");
        return u != null ? u.trim() : "";
    }

    /**
     * Returns the next 3 upcoming jobs from local cache, sorted by date then time.
     * Uses existing WorkViewLocalEventStore; no network.
     */
    public static List<JobLine> getNext3Jobs(Context context) {
        List<JobLine> out = new ArrayList<>();
        if (context == null) return out;

        String userName = getLastUserName(context);
        if (userName.isEmpty()) return out;

        List<WorkViewLocalEventStore.CachedEvent> list = WorkViewLocalEventStore.listUpcomingScheduled(
                context.getApplicationContext(), userName, LOOKAHEAD_DAYS);

        Collections.sort(list, new Comparator<WorkViewLocalEventStore.CachedEvent>() {
            @Override
            public int compare(WorkViewLocalEventStore.CachedEvent a, WorkViewLocalEventStore.CachedEvent b) {
                WorkEvent ea = a.event;
                WorkEvent eb = b.event;
                if (ea == null || eb == null) return 0;
                int d = safe(ea.getDate()).compareTo(safe(eb.getDate()));
                if (d != 0) return d;
                return safe(ea.getTime()).compareTo(safe(eb.getTime()));
            }
        });

        for (int i = 0; i < Math.min(MAX_JOBS, list.size()); i++) {
            WorkEvent e = list.get(i).event;
            if (e == null) continue;
            out.add(new JobLine(
                    e.getEventName(),
                    e.getTime(),
                    e.getAddress()
            ));
        }
        return out;
    }

    private static SharedPreferences widgetCachePrefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS_WIDGET_CACHE, Context.MODE_PRIVATE);
    }

    private static String todayYyyyMmDd() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
    }

    /**
     * Saves today's jobs to a persistent cache (separate from GRPC prefs so logout does not clear it).
     * Call this when WorkView loads today's events so the widget still shows them after logout.
     */
    public static void saveTodayJobsCache(Context context, String dateYyyyMmDd, List<WorkEvent> events) {
        if (context == null || dateYyyyMmDd == null || events == null) return;
        SharedPreferences.Editor ed = widgetCachePrefs(context).edit();
        ed.putString(KEY_CACHE_DATE, dateYyyyMmDd);
        int n = Math.min(events.size(), MAX_CACHED_JOBS);
        ed.putInt(KEY_JOB_COUNT, n);
        for (int i = 0; i < n; i++) {
            WorkEvent e = events.get(i);
            if (e == null) continue;
            String p = KEY_JOB_PREFIX + i + "_";
            ed.putString(p + "name", safe(e.getEventName()));
            ed.putString(p + "time", safe(e.getTime()));
            ed.putString(p + "address", safe(e.getAddress()));
        }
        ed.apply();
    }

    /**
     * Returns the date string for display (e.g. "Mon, 15 Feb 2025"). Uses cached date if it's today.
     */
    public static String getCachedDateForDisplay(Context context) {
        return getWidgetDisplay(context).displayDate;
    }

    /**
     * Returns jobs for widget display (next 3 jobs; if today has none, from next day).
     */
    public static List<JobLine> getCachedJobsForDisplay(Context context) {
        return getWidgetDisplay(context).jobs;
    }

    /**
     * Returns date + next 3 jobs for the widget. If today has cached jobs, shows today and those jobs.
     * If today has no jobs (or cache is stale), shows the next day that has jobs and the next 3 jobs.
     */
    public static WidgetDisplay getWidgetDisplay(Context context) {
        List<JobLine> jobs = new ArrayList<>();
        String displayDate = formatDate(todayYyyyMmDd());
        if (context == null) return new WidgetDisplay(displayDate, jobs);

        String today = todayYyyyMmDd();
        SharedPreferences prefs = widgetCachePrefs(context);
        String cachedDate = prefs.getString(KEY_CACHE_DATE, "");
        int cachedCount = prefs.getInt(KEY_JOB_COUNT, 0);

        if (today.equals(cachedDate) && cachedCount > 0) {
            for (int i = 0; i < Math.min(cachedCount, WIDGET_JOB_COUNT); i++) {
                String p = KEY_JOB_PREFIX + i + "_";
                jobs.add(new JobLine(
                        prefs.getString(p + "name", ""),
                        prefs.getString(p + "time", ""),
                        prefs.getString(p + "address", "")
                ));
            }
            return new WidgetDisplay(displayDate, jobs);
        }

        String userName = getLastUserName(context);
        if (userName.isEmpty()) return new WidgetDisplay(displayDate, jobs);

        List<WorkViewLocalEventStore.CachedEvent> list = WorkViewLocalEventStore.listUpcomingScheduled(
                context.getApplicationContext(), userName, LOOKAHEAD_DAYS);
        Collections.sort(list, new Comparator<WorkViewLocalEventStore.CachedEvent>() {
            @Override
            public int compare(WorkViewLocalEventStore.CachedEvent a, WorkViewLocalEventStore.CachedEvent b) {
                WorkEvent ea = a.event;
                WorkEvent eb = b.event;
                if (ea == null || eb == null) return 0;
                int d = safe(ea.getDate()).compareTo(safe(eb.getDate()));
                if (d != 0) return d;
                return safe(ea.getTime()).compareTo(safe(eb.getTime()));
            }
        });

        int take = Math.min(WIDGET_JOB_COUNT, list.size());
        for (int i = 0; i < take; i++) {
            WorkEvent e = list.get(i).event;
            if (e == null) continue;
            jobs.add(new JobLine(e.getEventName(), e.getTime(), e.getAddress()));
        }
        if (take > 0 && list.get(0).event != null) {
            String firstDate = list.get(0).event.getDate();
            displayDate = formatDateAsTodayTomorrowOrFull(firstDate, today);
        }
        return new WidgetDisplay(displayDate, jobs);
    }

    private static String formatDate(String yyyyMmDd) {
        if (yyyyMmDd == null || yyyyMmDd.isEmpty()) return yyyyMmDd;
        try {
            Date d = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(yyyyMmDd);
            if (d != null) return new SimpleDateFormat("EEE, d MMM yyyy", Locale.getDefault()).format(d);
        } catch (Exception ignored) { }
        return yyyyMmDd;
    }

    private static String formatDateAsTodayTomorrowOrFull(String yyyyMmDd, String todayYyyyMmDd) {
        if (yyyyMmDd == null || yyyyMmDd.isEmpty()) return formatDate(yyyyMmDd);
        if (yyyyMmDd.equals(todayYyyyMmDd)) return "Today";
        try {
            Date today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(todayYyyyMmDd);
            Date d = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(yyyyMmDd);
            if (today != null && d != null) {
                long diff = (d.getTime() - today.getTime()) / (24 * 60 * 60 * 1000);
                if (diff == 1) return "Tomorrow";
            }
        } catch (Exception ignored) { }
        return formatDate(yyyyMmDd);
    }

    private static String safe(String s) {
        return s != null ? s : "";
    }
}
