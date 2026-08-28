package com.guanlixitong.app;
import android.app.*;import android.content.*;import androidx.core.app.NotificationCompat;
public class ReminderReceiver extends BroadcastReceiver{
 @Override public void onReceive(Context c,Intent i){NotificationManager nm=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);Intent open=new Intent(c,MainActivity.class);PendingIntent pi=PendingIntent.getActivity(c,0,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);Notification n=new NotificationCompat.Builder(c,"study_reminder").setSmallIcon(R.drawable.ic_notification).setContentTitle("🎯 管理系统").setContentText("今天的学习任务还在等你，完成一点也算前进。").setPriority(NotificationCompat.PRIORITY_DEFAULT).setAutoCancel(true).setContentIntent(pi).build();nm.notify(20260827,n);}
}
