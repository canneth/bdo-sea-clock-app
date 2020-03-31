package com.example.bdoseatime

import android.app.*
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Log.d
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.content_main.*
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit


class MainActivity : AppCompatActivity() {

    lateinit var notification_manager: NotificationManager

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(toolbar)

        // Start service
        val service_intent = Intent(this, NotifClockService::class.java)
        startService(service_intent)

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

        val bdo_day_time = LocalDateTime.of(1, 1, 1, 7, 0, 0)
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

        val thread: Thread = object : Thread() {
            override fun run() {
                try {
                    while (!this.isInterrupted) {
                        runOnUiThread {
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
                            val remainder = (real_seconds_since_reference_real_time%(240*60))
                            if ((remainder >= 0) and (remainder < 200 * 60)) {
                                // Day time
                                // Calculate current_bdo_time
                                current_bdo_time = reference_bdo_time.plusSeconds((remainder * 15 * 60 / 200))
                                // Set day styling
                                toolbar.setBackgroundColor(getResources().getColor(R.color.colorPrimaryDay))
                                getWindow().setStatusBarColor(ContextCompat.getColor(getApplicationContext(), R.color.colorPrimaryDarkDay))
                                dayNightText.text = "Day Time"
                                dayNightText.setTextColor(getResources().getColor(R.color.dayHighlight))
                            } else if (remainder >= 200 * 60) {
                                // Night time
                                // Calculate current_bdo_time
                                current_bdo_time = bdo_night_time.plusSeconds(((remainder - 200 * 60) * 9 * 60 / 40))
                                // Set night styling
                                toolbar.setBackgroundColor(getResources().getColor(R.color.colorPrimaryNight))
                                getWindow().setStatusBarColor(ContextCompat.getColor(getApplicationContext(), R.color.colorPrimaryDarkNight))
                                dayNightText.text = "Night Time"
                                dayNightText.setTextColor(getResources().getColor(R.color.nightHighlight))
                            }
                            // Display formatted current_bdo_time
                            bdo_time.text = current_bdo_time.format(time_formatter)
                        }
                        sleep(1000) // Run loop every 1 second
                    }
                } catch (e: InterruptedException) {
                    onDestroy()
                }
            }
        }
        thread.start()

        killButton.setOnClickListener {
            stopService(service_intent)
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}