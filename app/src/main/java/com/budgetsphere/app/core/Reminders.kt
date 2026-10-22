package com.budgetsphere.app.core

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.*
import java.time.LocalDate
import java.util.concurrent.TimeUnit

class ReminderWorker(context: Context,params: WorkerParameters): CoroutineWorker(context,params) {
    override suspend fun doWork(): Result {
        if(inputData.getBoolean("monthly",false) && LocalDate.now().dayOfMonth!=1)return Result.success()
        if(android.os.Build.VERSION.SDK_INT>=33 && ContextCompat.checkSelfPermission(applicationContext,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)return Result.success()
        val manager=applicationContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("reminders","Private reminders",NotificationManager.IMPORTANCE_DEFAULT).apply {description="Offline reminders without financial information"})
        val intent=android.content.Intent(applicationContext,com.budgetsphere.app.MainActivity::class.java)
        val pending=android.app.PendingIntent.getActivity(applicationContext,0,intent,android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT)
        manager.notify(if(inputData.getBoolean("monthly",false))2 else 1,NotificationCompat.Builder(applicationContext,"reminders").setSmallIcon(com.budgetsphere.app.R.drawable.ic_sphere).setContentTitle("BudgetSphere").setContentText(if(inputData.getBoolean("monthly",false))"Take a moment to review your monthly plan." else "Take a moment to record today's activity.").setVisibility(NotificationCompat.VISIBILITY_SECRET).setContentIntent(pending).setAutoCancel(true).build())
        return Result.success()
    }
}
fun scheduleReminder(context: Context,monthly: Boolean,enabled: Boolean) {
    val manager=WorkManager.getInstance(context);val name=if(monthly)"monthly-review" else "daily-expense"
    if(!enabled){manager.cancelUniqueWork(name);return}
    val request=PeriodicWorkRequestBuilder<ReminderWorker>(1,TimeUnit.DAYS).setInputData(workDataOf("monthly" to monthly)).setInitialDelay(1,TimeUnit.DAYS).build()
    manager.enqueueUniquePeriodicWork(name,ExistingPeriodicWorkPolicy.KEEP,request)
}
