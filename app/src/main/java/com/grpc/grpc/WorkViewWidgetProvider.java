package com.grpc.grpc;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

import java.util.List;

/**
 * Home screen widget: logo, "Work View", today's date, and today's upcoming jobs (time, name, location).
 * Uses a persistent cache so that once the user has opened Work View in the morning, the widget
 * still shows that day's work even after logout. Tap opens WorkViewActivity.
 */
public class WorkViewWidgetProvider extends AppWidgetProvider {

    private static final String TITLE = "Work View";

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId);
        }
    }

    static void updateAppWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_workview);

        views.setTextViewText(R.id.widget_workview_title, TITLE);
        WorkViewWidgetHelper.WidgetDisplay display = WorkViewWidgetHelper.getWidgetDisplay(context);
        views.setTextViewText(R.id.widget_workview_date, display.displayDate);

        List<WorkViewWidgetHelper.JobLine> jobs = display.jobs;

        setJobRow(views, jobs, 0, R.id.widget_job1_time, R.id.widget_job1_name, R.id.widget_job1_address);
        setJobRow(views, jobs, 1, R.id.widget_job2_time, R.id.widget_job2_name, R.id.widget_job2_address);
        setJobRow(views, jobs, 2, R.id.widget_job3_time, R.id.widget_job3_name, R.id.widget_job3_address);

        Intent openApp = new Intent(context, WorkViewActivity.class);
        String userName = WorkViewWidgetHelper.getLastUserName(context);
        if (userName != null && !userName.isEmpty()) {
            openApp.putExtra("USER_NAME", userName);
        }
        openApp.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pending = PendingIntent.getActivity(context, 0, openApp,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widget_workview_root, pending);

        appWidgetManager.updateAppWidget(appWidgetId, views);
    }

    private static void setJobRow(RemoteViews views, List<WorkViewWidgetHelper.JobLine> jobs, int index,
                                   int idTime, int idName, int idAddress) {
        String time = "";
        String name = "";
        String address = "";
        if (jobs != null && index < jobs.size()) {
            WorkViewWidgetHelper.JobLine j = jobs.get(index);
            time = j.time != null ? j.time : "";
            name = j.name != null ? j.name : "";
            address = (j.address != null && !"N/A".equalsIgnoreCase(j.address)) ? j.address : "";
        }
        if (index == 0 && name.isEmpty() && time.isEmpty()) {
            name = "No jobs today";
            address = "Open Work View to load";
        }
        views.setTextViewText(idTime, time);
        views.setTextViewText(idName, name);
        views.setTextViewText(idAddress, address);
    }

    /**
     * Call from app to refresh the widget (e.g. after saving today's cache).
     */
    public static void refreshAllWidgets(Context context) {
        AppWidgetManager mgr = AppWidgetManager.getInstance(context);
        int[] ids = mgr.getAppWidgetIds(new android.content.ComponentName(context, WorkViewWidgetProvider.class));
        if (ids != null && ids.length > 0) {
            for (int id : ids) {
                updateAppWidget(context, mgr, id);
            }
        }
    }
}
