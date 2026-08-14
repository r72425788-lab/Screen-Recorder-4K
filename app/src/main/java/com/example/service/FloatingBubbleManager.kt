package com.example.service

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.example.R
import kotlin.math.abs

object FloatingBubbleManager {

    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private var isExpanded = false

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f

    fun showBubble(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
            return
        }

        if (floatingView != null) return

        try {
            windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val inflater = LayoutInflater.from(context)
            floatingView = inflater.inflate(R.layout.layout_floating_bubble, null)

            val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutFlag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = 100
                y = 300
            }

            windowManager?.addView(floatingView, params)
            setupTouchAndListeners(context, params)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun updateTimer(timeFormatted: String, isPaused: Boolean) {
        floatingView?.let { view ->
            val tvTimer = view.findViewById<TextView>(R.id.tv_floating_timer)
            val ivStatus = view.findViewById<ImageView>(R.id.iv_floating_status)
            
            tvTimer?.text = timeFormatted
            if (isPaused) {
                ivStatus?.setImageResource(R.drawable.ic_pause_small)
            } else {
                ivStatus?.setImageResource(R.drawable.ic_record_dot)
            }
        }
    }

    fun hideBubble() {
        try {
            if (floatingView != null) {
                windowManager?.removeView(floatingView)
                floatingView = null
                isExpanded = false
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun setupTouchAndListeners(context: Context, params: WindowManager.LayoutParams) {
        val view = floatingView ?: return

        val bubbleRoot = view.findViewById<LinearLayout>(R.id.ll_bubble_root)
        val expandedMenu = view.findViewById<LinearLayout>(R.id.ll_expanded_menu)
        val btnPauseResume = view.findViewById<ImageButton>(R.id.btn_float_pause)
        val btnStop = view.findViewById<ImageButton>(R.id.btn_float_stop)
        val btnScreenshot = view.findViewById<ImageButton>(R.id.btn_float_screenshot)
        val btnCloseMenu = view.findViewById<ImageButton>(R.id.btn_float_close)

        bubbleRoot.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager?.updateViewLayout(floatingView, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val diffX = abs(event.rawX - initialTouchX)
                    val diffY = abs(event.rawY - initialTouchY)
                    if (diffX < 10 && diffY < 10) {
                        // Tap toggles menu
                        isExpanded = !isExpanded
                        expandedMenu.visibility = if (isExpanded) View.VISIBLE else View.GONE
                    }
                    true
                }
                else -> false
            }
        }

        btnPauseResume?.setOnClickListener {
            val currentState = ScreenRecordService.serviceState.value
            val isCurrentlyPaused = currentState is RecordServiceState.Recording && currentState.isPaused

            val action = if (isCurrentlyPaused) ScreenRecordService.ACTION_RESUME else ScreenRecordService.ACTION_PAUSE
            val intent = Intent(context, ScreenRecordService::class.java).apply { this.action = action }
            context.startService(intent)

            btnPauseResume.setImageResource(
                if (isCurrentlyPaused) R.drawable.ic_pause_small else R.drawable.ic_play_small
            )
        }

        btnStop?.setOnClickListener {
            val intent = Intent(context, ScreenRecordService::class.java).apply {
                action = ScreenRecordService.ACTION_STOP
            }
            context.startService(intent)
            hideBubble()
        }

        btnScreenshot?.setOnClickListener {
            // Open Screen Recorder App
            try {
                val appIntent = Intent(context, com.example.MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                context.startActivity(appIntent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            isExpanded = false
            expandedMenu.visibility = View.GONE
        }

        btnCloseMenu?.setOnClickListener {
            isExpanded = false
            expandedMenu.visibility = View.GONE
        }
    }
}
