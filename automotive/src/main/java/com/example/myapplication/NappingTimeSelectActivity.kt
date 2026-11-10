package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.FragmentActivity

class NappingTimeSelectActivity : FragmentActivity() {

    private var selectedMinutes = 20
    private val cards = mutableMapOf<Int, LinearLayout>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_napping_time_select)

        findViewById<TextView>(R.id.tvBack).setOnClickListener { finish() }
        findViewById<TextView>(R.id.btnClose).setOnClickListener {
            finishAffinity()
            System.exit(0)
        }

        cards[10] = findViewById(R.id.card10)
        cards[15] = findViewById(R.id.card15)
        cards[20] = findViewById(R.id.card20)
        cards[30] = findViewById(R.id.card30)

        cards.forEach { (minutes, card) ->
            card.setOnClickListener {
                selectedMinutes = minutes
                updateCardSelection()
            }
        }

        updateCardSelection()

        findViewById<Button>(R.id.btnStart).setOnClickListener {
            val intent = Intent(this, NappingSettingActivity::class.java)
            intent.putExtra("TOTAL_SECONDS", selectedMinutes * 60)
            startActivity(intent)
        }
    }

    private fun updateCardSelection() {
        cards.forEach { (minutes, card) ->
            if (minutes == selectedMinutes) {
                card.setBackgroundResource(R.drawable.bg_time_card_selected)
                (card.getChildAt(0) as? TextView)?.setTextColor(0xFFDCC6AB.toInt())
            } else {
                card.setBackgroundResource(R.drawable.bg_time_card_normal)
                (card.getChildAt(0) as? TextView)?.setTextColor(0xFFF6F6F7.toInt())
            }
        }
    }
}