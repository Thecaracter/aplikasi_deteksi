package com.example.aplikasi_deteksi_jalan.tracking.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.aplikasi_deteksi_jalan.MainActivity
import com.example.aplikasi_deteksi_jalan.R
import com.example.aplikasi_deteksi_jalan.tracking.manager.TrackingLocationManager

/**
 * FOREGROUND SERVICE untuk Location Tracking
 * 
 * Service ini akan:
 * - Jalan di foreground dengan notification persistent
 * - GPS tracking tetap aktif walaupun app ditutup
 * - Hanya untuk user role (tunanetra)
 */
class LocationForegroundService : Service() {
    
    private lateinit var locationManager: TrackingLocationManager
    
    companion object {
        private const val CHANNEL_ID = "location_tracking_channel"
        private const val NOTIFICATION_ID = 1001
        
        fun startService(context: Context) {
            val intent = Intent(context, LocationForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
        
        fun stopService(context: Context) {
            val intent = Intent(context, LocationForegroundService::class.java)
            context.stopService(intent)
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        locationManager = TrackingLocationManager(applicationContext)
        createNotificationChannel()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Start foreground with notification
        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)
        
        // Start location tracking
        locationManager.startTracking()
        
        return START_STICKY // Service will restart if killed
    }
    
    override fun onDestroy() {
        super.onDestroy()
        locationManager.stopTracking()
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Location Tracking",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Tracking lokasi aktif untuk keamanan"
                setShowBadge(false)
            }
            
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Tracking Lokasi Aktif")
            .setContentText("Lokasi Anda sedang dibagikan ke guardian")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
