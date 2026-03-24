package com.example.safeway

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity

class FindHelpNearYouActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_find_help_near_you)

        findViewById<ImageButton>(R.id.btn_back_find_help).setOnClickListener { finish() }

        bindServiceActions(
            callButtonId = R.id.btn_call_hospital,
            directionsButtonId = R.id.btn_directions_hospital,
            phoneNumber = "112",
            mapsQuery = "Nairobi Hospital Emergency"
        )

        bindServiceActions(
            callButtonId = R.id.btn_call_counseling,
            directionsButtonId = R.id.btn_directions_counseling,
            phoneNumber = "0724999999",
            mapsQuery = "Counseling Center Nairobi"
        )

        bindServiceActions(
            callButtonId = R.id.btn_call_legal_aid,
            directionsButtonId = R.id.btn_directions_legal_aid,
            phoneNumber = "0800123456",
            mapsQuery = "Amnesty International Kenya"
        )

        bindServiceActions(
            callButtonId = R.id.btn_call_police,
            directionsButtonId = R.id.btn_directions_police,
            phoneNumber = "999",
            mapsQuery = "Police Gender Desk Nairobi"
        )
    }

    private fun bindServiceActions(
        callButtonId: Int,
        directionsButtonId: Int,
        phoneNumber: String,
        mapsQuery: String
    ) {
        findViewById<Button>(callButtonId).setOnClickListener {
            startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phoneNumber")))
        }

        findViewById<Button>(directionsButtonId).setOnClickListener {
            val encodedQuery = Uri.encode(mapsQuery)
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://maps.google.com/?q=$encodedQuery")))
        }
    }
}

