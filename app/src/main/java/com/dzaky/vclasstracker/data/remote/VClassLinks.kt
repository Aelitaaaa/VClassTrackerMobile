package com.dzaky.vclasstracker.data.remote

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

internal object VClassLinks {
    fun open(context: Context, value: String) {
        val url = VClassSessionPolicy.officialUrl(value)
        if (url == null) {
            Toast.makeText(context, "Link VClass tidak tersedia.", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url.toString())))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "Tidak ada browser untuk membuka VClass.", Toast.LENGTH_SHORT).show()
        }
    }
}
