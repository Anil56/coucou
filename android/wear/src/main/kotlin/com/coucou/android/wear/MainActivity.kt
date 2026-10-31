package com.coucou.android.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { CoucouWatchApp() }
    }

    override fun onStart() {
        super.onStart()
        PhoneLink.hello(this) // the phone answers with what it shows now
    }
}
