package com.example.wifidisplayer

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import kotlin.random.Random

// In a real project, these imports would be handled by Gradle.
// For this simulation, we assume they are present.
// import com.example.wifidisplayer.R

class MainActivity : AppCompatActivity() {

    private lateinit var wifiListView: ListView
    private lateinit var refreshButton: Button
    private lateinit var adapter: ArrayAdapter<String>

    private val wifiNames = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // This R.layout.activity_main corresponds to app/src/main/res/layout/activity_main.xml
        setContentView(R.layout.activity_main)

        // The R.id values correspond to the android:id attributes in the layout XML files
        wifiListView = findViewById(R.id.wifiListView)
        refreshButton = findViewById(R.id.refreshButton)

        // The adapter links the data (wifiNames) to the ListView.
        // It uses the list_item_wifi.xml layout for each row.
        adapter = ArrayAdapter(this, R.layout.list_item_wifi, R.id.wifiNameTextView, wifiNames)
        wifiListView.adapter = adapter

        refreshButton.setOnClickListener {
            updateWifiList()
        }

        // Populate the list with initial data when the app starts
        updateWifiList()
    }

    private fun generateRandomWifiNames(): List<String> {
        val prefixes = listOf("UPC", "Orange_Swiatlowod", "Vectra", "Netia_Spot", "Dom", "AndroidAP", "TP-Link", "ASUS_5G", "Siec_Sasiada")
        val suffixes = listOf("", "_Guest", "_5G", "_2.4G", "_EXT")
        val generatedNames = mutableListOf<String>()
        val count = Random.nextInt(8, 20) // Generate between 8 and 20 networks

        for (i in 1..count) {
            val prefix = prefixes.random()
            val suffix = suffixes.random()
            val number = Random.nextInt(10, 999)
            generatedNames.add("$prefix$suffix-$number")
        }
        return generatedNames
    }

    private fun updateWifiList() {
        val newWifiNames = generateRandomWifiNames()
        wifiNames.clear()
        wifiNames.addAll(newWifiNames)
        // This tells the ListView to refresh itself with the new data
        adapter.notifyDataSetChanged()
    }
}
