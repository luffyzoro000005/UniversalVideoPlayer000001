package com.example.universalvideoplayer

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var libVLC: LibVLC
    private lateinit var mediaPlayer: MediaPlayer
    private lateinit var videoLayout: VLCVideoLayout

    private lateinit var seekBar: SeekBar
    private lateinit var btnPlayPause: Button
    private lateinit var btnSpeed: Button
    private lateinit var tvTime: TextView
    private lateinit var topControls: LinearLayout
    private lateinit var bottomControls: LinearLayout

    private var isSeeking = false
    private var controlsVisible = true
    private val speeds = floatArrayOf(0.5f, 1.0f, 1.25f, 1.5f, 2.0f)
    private var currentSpeedIndex = 1

    private val pickVideoLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { playVideo(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        videoLayout = findViewById(R.id.videoLayout)
        seekBar = findViewById(R.id.seekBar)
        btnPlayPause = findViewById(R.id.btnPlayPause)
        btnSpeed = findViewById(R.id.btnSpeed)
        tvTime = findViewById(R.id.tvTime)
        topControls = findViewById(R.id.topControls)
        bottomControls = findViewById(R.id.bottomControls)

        val btnPickVideo: Button = findViewById(R.id.btnPickVideo)
        val btnRewind: Button = findViewById(R.id.btnRewind)
        val btnForward: Button = findViewById(R.id.btnForward)

        val options = ArrayList<String>().apply {
            add("--avcodec-skiploopfilter")
            add("1")
        }
        libVLC = LibVLC(this, options)
        mediaPlayer = MediaPlayer(libVLC)
        mediaPlayer.attachViews(videoLayout, null, false, false)

        videoLayout.setOnClickListener {
            controlsVisible = !controlsVisible
            val visibility = if (controlsVisible) View.VISIBLE else View.GONE
            topControls.visibility = visibility
            bottomControls.visibility = visibility
        }

        btnPickVideo.setOnClickListener {
            pickVideoLauncher.launch("video/*")
        }

        btnSpeed.setOnClickListener {
            currentSpeedIndex = (currentSpeedIndex + 1) % speeds.size
            val newSpeed = speeds[currentSpeedIndex]
            mediaPlayer.rate = newSpeed
            btnSpeed.text = "${newSpeed}x"
        }

        btnPlayPause.setOnClickListener {
            if (mediaPlayer.isPlaying) {
                mediaPlayer.pause()
                btnPlayPause.text = "Play"
            } else {
                mediaPlayer.play()
                btnPlayPause.text = "Pause"
            }
        }

        btnRewind.setOnClickListener {
            mediaPlayer.time = (mediaPlayer.time - 10000).coerceAtLeast(0)
        }
        btnForward.setOnClickListener {
            mediaPlayer.time = (mediaPlayer.time + 10000).coerceAtMost(mediaPlayer.length)
        }

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser && mediaPlayer.length > 0) {
                    val newTime = (mediaPlayer.length * progress) / 100
                    tvTime.text = "${formatTime(newTime)} / ${formatTime(mediaPlayer.length)}"
                }
            }
            override fun onStartTrackingTouch(bar: SeekBar?) { isSeeking = true }
            override fun onStopTrackingTouch(bar: SeekBar?) {
                if (mediaPlayer.length > 0 && bar != null) {
                    mediaPlayer.time = (mediaPlayer.length * bar.progress) / 100
                }
                isSeeking = false
            }
        })

        mediaPlayer.setEventListener { event ->
            when (event.type) {
                MediaPlayer.Event.TimeChanged -> {
                    if (!isSeeking && mediaPlayer.length > 0) {
                        val current = mediaPlayer.time
                        val total = mediaPlayer.length
                        seekBar.progress = ((current * 100) / total).toInt()
                        tvTime.text = "${formatTime(current)} / ${formatTime(total)}"
                    }
                }
                MediaPlayer.Event.EndReached -> {
                    btnPlayPause.text = "Play"
                    seekBar.progress = 0
                }
            }
        }
    }

    private fun playVideo(uri: Uri) {
        contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
            val media = Media(libVLC, pfd.fileDescriptor)
            media.setHWDecoderEnabled(true, false)
            mediaPlayer.media = media
            media.release()
            mediaPlayer.play()
            btnPlayPause.text = "Pause"
        }
    }

    private fun formatTime(millis: Long): String {
        val minutes = TimeUnit.MILLISECONDS.toMinutes(millis)
        val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60
        return String.format("%02d:%02d", minutes, seconds)
    }

    override fun onStop() {
        super.onStop()
        mediaPlayer.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer.release()
        libVLC.release()
    }
}
