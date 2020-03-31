package com.example.bdoseatime

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Build
import android.os.IBinder
import android.text.Html
import android.text.Html.FROM_HTML_MODE_LEGACY
import android.text.Spanned
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.content_main.*
import java.lang.Math.ceil
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.floor

class NotifClockService : Service() {

	@RequiresApi(Build.VERSION_CODES.O)
	fun timeSpanInSeconds(fromDateTime: LocalDateTime, toDateTime: LocalDateTime): Long {
		var tempDateTime = LocalDateTime.from(fromDateTime)
		val years = tempDateTime.until(toDateTime, ChronoUnit.YEARS)
		tempDateTime = tempDateTime.plusYears(years)
		val months = tempDateTime.until(toDateTime, ChronoUnit.MONTHS)
		tempDateTime = tempDateTime.plusMonths(months)
		val days = tempDateTime.until(toDateTime, ChronoUnit.DAYS)
		tempDateTime = tempDateTime.plusDays(days)
		val hours = tempDateTime.until(toDateTime, ChronoUnit.HOURS)
		tempDateTime = tempDateTime.plusHours(hours)
		val minutes = tempDateTime.until(toDateTime, ChronoUnit.MINUTES)
		tempDateTime = tempDateTime.plusMinutes(minutes)
		val seconds = tempDateTime.until(toDateTime, ChronoUnit.SECONDS)
		// Convert time difference into seconds
		val seconds_between =
			(years*3.154e7
					+ months*2.628e6
					+ days*86400
					+ hours*3600
					+ minutes*60
					+ seconds).toLong()
		return seconds_between
	}

	lateinit var notification_manager: NotificationManager

	override fun onCreate() {
		super.onCreate()
	}

	@RequiresApi(Build.VERSION_CODES.O)
	override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

		// Create service (main) notification channel for the persistent notification indicating app running in foreground
		notification_manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
		val channel_id = "com.example.bdoseatime"
		val channel_name = "main_notif_channel"
		val channel_description = "main_notif_description"
		val channel_importance = NotificationManager.IMPORTANCE_HIGH
		val channel = NotificationChannel(channel_id, channel_name, channel_importance)
		channel.description = channel_description
		channel.enableVibration(false)
		channel.enableLights(false)
		// Create periodic notification channel for notifications upon day/night transitions in BDO
		val channel_periodic_id = "com.example.bdoseatime_periodic"
		val channel_periodic_name = "periodic_notif_channel"
		val channel_periodic_description = "main_notif_description"
		val channel_periodic_importance = NotificationManager.IMPORTANCE_HIGH
		val channel_periodic = NotificationChannel(channel_periodic_id, channel_periodic_name, channel_periodic_importance)
		channel.description = channel_description
		channel.enableVibration(true)
		channel.enableLights(true)
		channel.lightColor = Color.CYAN
		// Register channels with manager
		notification_manager.createNotificationChannel(channel)
		notification_manager.createNotificationChannel(channel_periodic)
		// Create builder for the channels
		val builder = NotificationCompat.Builder(this, channel_id)
		val builder_periodic = NotificationCompat.Builder(this, channel_periodic_id)
		// Intents for notifications
		val intent = Intent(applicationContext, MainActivity::class.java)
		val pending_intent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)

		val bdo_day_time = LocalDateTime.of(1, 1, 1, 7, 0, 0)
		val bdo_next_day_time = LocalDateTime.of(1, 1, 2, 7, 0, 0)
		val bdo_night_time = LocalDateTime.of(1, 1, 1, 22, 0, 0)
		val bdo_midnight_time = LocalDateTime.of(1, 1, 2, 0, 0, 0)

		// Each 24hr BDO day is exactly equivalent to a real 4hr (240 mins) period.
		// Between 0700 BDO and 2200 BDO passes exactly 200 real minutes.
		// Between 2200 BDO to 0700 BDO passes exactly 40 real minutes.
		// At (200 * 60 + 20 * 60) seconds past 0000 HRS real time, time in BDO is exactly 0700 BDO.

		// Set reference_real_time to 0000 HRS of the current real date
		var reference_real_time = LocalDateTime.now()
		reference_real_time = reference_real_time.minusHours(reference_real_time.hour.toLong())
		reference_real_time = reference_real_time.minusMinutes(reference_real_time.minute.toLong())
		reference_real_time = reference_real_time.minusSeconds(reference_real_time.second.toLong())
		reference_real_time = reference_real_time.minusNanos(reference_real_time.nano.toLong())
		// Set reference_bdo_time to 0700 BDO
		val reference_bdo_time = bdo_day_time

		val shared_pref = getSharedPreferences("shared_pref", Context.MODE_PRIVATE)

		val DAY = true
		val NIGHT = false

		val runnable = Runnable {
			while(true) {
				val last_state = shared_pref.getBoolean("last_state", DAY)
				// Grab time from phone
				val current_real_time = LocalDateTime.now()
				// Format time
				val time_formatter = DateTimeFormatter.ofPattern("HH:mm:ss")
				val date_formatter = DateTimeFormatter.ofPattern("dd MMM yyyy")
				val formatted_time = current_real_time.format(time_formatter)
				val formatted_date = current_real_time.format(date_formatter)

				// Find time difference between reference time and current time
				var real_seconds_since_reference_real_time = timeSpanInSeconds(reference_real_time, current_real_time)
				real_seconds_since_reference_real_time += (200 * 60 + 20 * 60)
				// Update bdo_time
				var current_bdo_time = reference_bdo_time
				if (real_seconds_since_reference_real_time >= 0) {
					val remainder = (real_seconds_since_reference_real_time%(240*60))
					Log.d("remainder", "$remainder")
					if ((remainder >= 0) and (remainder < 200 * 60)) {
						// Day time
						// Calculate current_bdo_time
						current_bdo_time = reference_bdo_time.plusSeconds((remainder * 15 * 60 / 200))
						if (last_state == NIGHT) {
							// Build notification
							val sun_emoji = String(Character.toChars(0x2600));
							builder_periodic.setContentTitle("$sun_emoji  It's a new day!")
							builder_periodic.setSmallIcon(R.mipmap.ic_launcher_round)
							builder_periodic.setContentIntent(pending_intent)
							builder_periodic.setOnlyAlertOnce(true)
							builder_periodic.setAutoCancel(true)
							// Send notification
							notification_manager.notify(2, builder_periodic.build())
						} else {
							val sun_emoji = String(Character.toChars(0x2600));
							builder.setContentTitle("$sun_emoji  Day Time")
							// Build notification
							val formatted_current_bdo_time = current_bdo_time.format(time_formatter)
							val real_seconds_to_bdo_night = timeSpanInSeconds(current_bdo_time, bdo_night_time)/(15*60/200.0)
							val real_minutes_to_bdo_night = floor(real_seconds_to_bdo_night/60.0)
							val real_remainder_seconds_to_bdo_night = floor(real_seconds_to_bdo_night%60.0)
							val recoverable_energy = floor(real_minutes_to_bdo_night/3)
							val body: String = getString(R.string.day_notif_body, formatted_current_bdo_time, real_minutes_to_bdo_night.toInt(), real_remainder_seconds_to_bdo_night.toInt(), recoverable_energy.toInt())
							val styled_body: Spanned = Html.fromHtml(body, FROM_HTML_MODE_LEGACY)
							builder.setContentText("It is $formatted_current_bdo_time now in BDO SEA")
								.setStyle(NotificationCompat.BigTextStyle()
									.bigText(styled_body))
							builder.setSmallIcon(R.mipmap.ic_launcher_round)
							builder.setContentIntent(pending_intent)
							builder.setOngoing(true)
							builder.setOnlyAlertOnce(true)
							// Send notification
							startForeground(1, builder.build())
						}
						shared_pref.edit().apply {
							putBoolean("last_state", DAY)
						}.apply()
					} else if (remainder >= 200 * 60) {
						// Night time
						// Calculate current_bdo_time
						current_bdo_time = bdo_night_time.plusSeconds(((remainder - 200 * 60) * 9 * 60 / 40))
						if (last_state == DAY) {
							// Build notification
							val moon_emoji = String(Character.toChars(0x1F319))
							builder_periodic.setContentTitle("$moon_emoji  Night has fallen!")
							builder_periodic.setSmallIcon(R.mipmap.ic_launcher_round)
							builder_periodic.setContentIntent(pending_intent)
							builder_periodic.setOnlyAlertOnce(true)
							builder_periodic.setAutoCancel(true)
							// Send notification
							notification_manager.notify(2, builder_periodic.build())
						} else {
							// Build notification
							val moon_emoji = String(Character.toChars(0x1F319))
							builder.setContentTitle("$moon_emoji  Night Time")
							val formatted_current_bdo_time = current_bdo_time.format(time_formatter)
							val real_seconds_to_bdo_day = timeSpanInSeconds(current_bdo_time, bdo_next_day_time)/(9*60/45)
							val real_minutes_to_bdo_day = floor(real_seconds_to_bdo_day/60.0)
							val real_remainder_seconds_to_bdo_day = floor(real_seconds_to_bdo_day%60.0)
							val recoverable_energy = floor(real_minutes_to_bdo_day/3)
							val body: String = getString(R.string.night_notif_body, formatted_current_bdo_time, real_minutes_to_bdo_day.toInt(), real_remainder_seconds_to_bdo_day.toInt(), recoverable_energy.toInt())
							val styled_body: Spanned = Html.fromHtml(body, FROM_HTML_MODE_LEGACY)
							builder.setContentText("It is $formatted_current_bdo_time now in BDO SEA")
								.setStyle(NotificationCompat.BigTextStyle()
									.bigText(styled_body))
							builder.setSmallIcon(R.mipmap.ic_launcher_round)
							builder.setContentIntent(pending_intent)
							builder.setOngoing(true)
							builder.setOnlyAlertOnce(true)
							// Send notification
							startForeground(1, builder.build())
						}
						shared_pref.edit().apply {
							putBoolean("last_state", NIGHT)
						}.apply()
					}
				}
				Thread.sleep(1000)
			}
		}
		val thread = Thread(runnable)
		thread.start()

		return super.onStartCommand(intent, flags, startId)
	}

	override fun onDestroy() {
		super.onDestroy()
	}

	override fun onBind(intent: Intent): IBinder? {
		return null
	}
}
