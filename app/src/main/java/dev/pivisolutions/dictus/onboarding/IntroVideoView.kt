package dev.pivisolutions.dictus.onboarding

import android.content.Context
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.view.Surface
import android.view.TextureView
import androidx.annotation.RawRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import dev.pivisolutions.dictus.core.ui.rememberSyntheticMotionEnabled

/** Width over height of the videos and stills: 1000 × 1440 px (iOS `IntroVideoView.aspectRatio`). */
private const val INTRO_ASPECT_RATIO = 1000f / 1440f

/**
 * The art of one intro page: the scene's video looping in place, muted, in the render that
 * matches the app's theme (iOS `DictusApp/Onboarding/IntroVideoView.swift`).
 *
 * WHY THE STILL FRAME ON TOP: until the video has drawn its first frame, the still covers
 * the empty player, and the video starts on the very frame the still was taken from
 * ([OnboardingIntroScene.stillFrameMs]), so the hand-over is invisible. A page that is not
 * on screen keeps its still; a video the phone cannot decode never draws a frame, so the
 * still stays. When Android asks to reduce motion, every page shows its still, as iOS does
 * under Reduce Motion.
 *
 * Decorative: TalkBack reads the page's headline and subtitle, not the art.
 *
 * @param isCurrent whether this page is the one on screen. Only that page plays, and only
 *   while the app is in front.
 */
@Composable
fun IntroVideoView(
    scene: OnboardingIntroScene,
    isCurrent: Boolean,
    modifier: Modifier = Modifier,
) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val motionEnabled by rememberSyntheticMotionEnabled()
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val playing = isCurrent && lifecycleState.isAtLeast(Lifecycle.State.RESUMED)

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Box(modifier = Modifier.aspectRatio(INTRO_ASPECT_RATIO)) {
            // A new theme is a new file: a new player for it, and the new still until it has a frame.
            key(dark, motionEnabled) {
                var videoVisible by remember { mutableStateOf(false) }
                if (motionEnabled) {
                    LoopingVideo(
                        videoRes = scene.video(dark),
                        startMs = scene.stillFrameMs,
                        playing = playing,
                        onFirstFrame = { videoVisible = true },
                        modifier = Modifier.matchParentSize(),
                    )
                }
                if (!videoVisible) {
                    Image(
                        painter = painterResource(scene.still(dark)),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.matchParentSize(),
                    )
                }
            }
        }
    }
}

/**
 * A [TextureView] looping one raw video with the platform [MediaPlayer], muted.
 *
 * WHY MediaPlayer AND NOT Media3/ExoPlayer: a short silent file looped in place needs
 * nothing ExoPlayer adds (streaming, tracks, adaptive formats), and the platform player
 * costs no dependency. WHY A TextureView: it is drawn like any view, so the still can sit
 * on top of it and it slides with the pager; a SurfaceView punches a hole in the window.
 */
@Composable
private fun LoopingVideo(
    @RawRes videoRes: Int,
    startMs: Int,
    playing: Boolean,
    onFirstFrame: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val player = remember(videoRes) { LoopingPlayer(context, videoRes, startMs, onFirstFrame) }
    SideEffect { player.setPlaying(playing) }
    DisposableEffect(player) { onDispose { player.release() } }
    AndroidView(
        factory = { TextureView(it).apply { surfaceTextureListener = player } },
        modifier = modifier,
    )
}

/**
 * Owns one [MediaPlayer] for as long as its [TextureView] has a surface.
 *
 * The player is built when the surface arrives and released when it goes, so a page the
 * pager disposes holds no decoder.
 */
private class LoopingPlayer(
    private val context: Context,
    @RawRes private val videoRes: Int,
    private val startMs: Int,
    private val onFirstFrame: () -> Unit,
) : TextureView.SurfaceTextureListener {

    private var player: MediaPlayer? = null
    private var surface: Surface? = null

    /** Set once the first pass is at [startMs]. Playing before would show the file's start. */
    private var positioned = false
    private var wantsToPlay = false

    fun setPlaying(playing: Boolean) {
        wantsToPlay = playing
        val player = player ?: return
        if (!positioned) return
        if (playing && !player.isPlaying) player.start()
        if (!playing && player.isPlaying) player.pause()
    }

    override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
        val surface = Surface(texture).also { surface = it }
        player = MediaPlayer().apply {
            context.resources.openRawResourceFd(videoRes).use { fd ->
                setDataSource(fd.fileDescriptor, fd.startOffset, fd.length)
            }
            setSurface(surface)
            // Silent: the files have no audio track, and muting makes sure no future file
            // can play sound over the user's music.
            setVolume(0f, 0f)
            isLooping = true
            setOnPreparedListener { it.seekTo(startMs.toLong(), MediaPlayer.SEEK_CLOSEST) }
            setOnSeekCompleteListener {
                if (!positioned) {
                    positioned = true
                    setPlaying(wantsToPlay)
                }
            }
            setOnInfoListener { _, what, _ ->
                if (what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) onFirstFrame()
                false
            }
            // A file the phone cannot decode: keep the still, which is already on screen.
            setOnErrorListener { _, _, _ -> true }
            prepareAsync()
        }
    }

    override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
        release()
        return true
    }

    override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) = Unit

    override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit

    fun release() {
        player?.release()
        player = null
        surface?.release()
        surface = null
        positioned = false
    }
}
