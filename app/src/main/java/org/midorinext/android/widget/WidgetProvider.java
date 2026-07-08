package org.midorinext.android.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;
import org.midorinext.android.BuildConfig;
import org.midorinext.android.R;

public class WidgetProvider extends AppWidgetProvider {
    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_layout);

        final int requestCode = 0;
        final int flags = PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT;

        final Intent intent = new Intent(BuildConfig.WIDGET_ACTION);
        final PendingIntent pendingIntent = PendingIntent.getActivity(context, requestCode, intent, flags);
        views.setOnClickPendingIntent(R.id.custom_notification_widget_layout, pendingIntent);

        ComponentName watchWidget = new ComponentName(context, WidgetProvider.class);
        appWidgetManager.updateAppWidget(watchWidget, views);
    }
}