package dev.anaekran

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.TextView
import java.time.LocalTime
import kotlin.random.Random

class HomeActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(TextView(this).apply {
            text = pickGreeting(slotFor(LocalTime.now().hour), Random.Default)
            textSize = 40f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        })
    }
}
