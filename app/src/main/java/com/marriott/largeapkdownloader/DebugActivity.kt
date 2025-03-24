package com.marriott.largeapkdownloader

import android.os.Bundle
import android.provider.Settings
import android.view.GestureDetector
import android.view.MotionEvent
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class DebugActivity : AppCompatActivity() {

    private lateinit var debugTextView: TextView
    private lateinit var gestureDetector: GestureDetector

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_debug)

        debugTextView = findViewById(R.id.debugTextView)

        // Initially display a prompt.
        debugTextView.text = "Tap here to reveal device ID"

        // Create a GestureDetector to listen for a double-tap.
        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                val androidId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown"
                debugTextView.text = "Device Unique ID: $androidId"
                return true
            }
        })

        // Attach the gesture detector to the TextView.
        debugTextView.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            true
        }
    }
}
