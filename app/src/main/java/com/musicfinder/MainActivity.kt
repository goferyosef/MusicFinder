package com.musicfinder

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.Person
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.musicfinder.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        publishShareShortcut()

        binding.buttonCamera.setOnClickListener {
            startActivity(Intent(this, CameraActivity::class.java))
        }
    }

    /**
     * Publishes a sharing shortcut so MusicFinder appears in the Direct Share
     * row at the very top of the Android share sheet.
     * Rank 0 = highest priority. LongLived = survives app restarts.
     */
    private fun publishShareShortcut() {
        val shortcut = ShortcutInfoCompat.Builder(this, "musicfinder_share")
            .setShortLabel("MusicFinder")
            .setLongLabel("Find & play music")
            .setIcon(IconCompat.createWithResource(this, R.drawable.ic_music_note))
            .setIntent(Intent(Intent.ACTION_DEFAULT, null, this, ShareActivity::class.java))
            .setCategories(setOf("com.musicfinder.SHARE_TARGET"))
            .setRank(0)
            .setLongLived(true)
            .setPerson(
                Person.Builder()
                    .setName("MusicFinder")
                    .setIcon(IconCompat.createWithResource(this, R.drawable.ic_music_note))
                    .build()
            )
            .build()

        ShortcutManagerCompat.pushDynamicShortcut(this, shortcut)
    }
}
