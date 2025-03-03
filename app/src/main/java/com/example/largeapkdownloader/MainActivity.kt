package com.example.largeapkdownloader

class MainActivity : AppCompatActivity() {

    private lateinit var syncButton: Button
    private lateinit var logTextView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        syncButton = findViewById(R.id.syncButton)
        logTextView = findViewById(R.id.logTextView)

        syncButton.setOnClickListener {
            triggerManualSync()
        }

        // Optionally observe log updates, etc.
    }

    // This function can be placed directly inside your MainActivity
    private fun triggerManualSync() {
        Log.d("MainActivity", "Manual sync triggered.")

        // For example, start a background service or enqueue a WorkManager task
    }
}
