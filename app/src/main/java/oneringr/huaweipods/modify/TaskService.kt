package oneringr.huaweipods.modify

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.content.ContextCompat

class TaskService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "cancel") { cancelTask?.invoke(); stopSelf(); return START_NOT_STICKY }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("tasks", "音频处理与传输", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val cancel = PendingIntent.getService(this, 1, Intent(this, TaskService::class.java).setAction("cancel"), PendingIntent.FLAG_IMMUTABLE)
        val connected = intent?.getBooleanExtra("connected", false) == true
        val notification = Notification.Builder(this, "tasks").setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(if (connected) "正在传输音效" else "正在处理音频")
            .setContentText("点击返回应用查看进度").setContentIntent(open).setOngoing(true)
            .addAction(Notification.Action.Builder(null, "取消", cancel).build()).build()
        startForeground(1, notification, if (connected) ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE else ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        return START_NOT_STICKY
    }
    override fun onDestroy() { super.onDestroy(); stopForeground(STOP_FOREGROUND_REMOVE) }
    companion object {
        var cancelTask: (() -> Unit)? = null
        fun start(context: Context, connected: Boolean, cancel: () -> Unit) {
            cancelTask = cancel
            ContextCompat.startForegroundService(context, Intent(context, TaskService::class.java).putExtra("connected", connected))
        }
        fun stop(context: Context) { cancelTask = null; context.stopService(Intent(context, TaskService::class.java)) }
    }
}
