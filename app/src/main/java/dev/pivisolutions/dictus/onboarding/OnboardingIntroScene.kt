package dev.pivisolutions.dictus.onboarding

import androidx.annotation.DrawableRes
import androidx.annotation.RawRes
import androidx.annotation.StringRes
import dev.pivisolutions.dictus.R

/**
 * One page of the intro carousel on the welcome step (#197), ported from iOS
 * `DictusCore/Sources/DictusCore/OnboardingIntroScene.swift`.
 *
 * The three scenes tell one story, the voice goes into the phone and comes out as text:
 * walking, the metro without network, the keyboard in a note. Each page loops the iOS
 * scene video above a headline and a subtitle that are app text, never baked into the
 * video.
 *
 * WHY A LIGHT AND A DARK FILE: the videos are opaque and each carries its page colour
 * (#F2F2F7 light, #0A1628 dark, the Dictus backgrounds), so the edge of the video only
 * disappears against the background it was rendered for. The drawn text in the videos
 * and stills is in English by default and in French under `raw-fr` / `drawable-fr-nodpi`.
 *
 * WHY H.264 WHEN iOS SHIPS HEVC: the iOS files are HEVC Main 10 (10-bit), which Android
 * does not require phones to decode, and the emulator's decoder rejects. The same frames
 * re-encoded to H.264 High 8-bit (`ffmpeg -c:v libx264 -crf 26 -tune animation`, SSIM
 * 0.992 or more against the iOS file) play on every Android phone, and weigh less.
 *
 * @param stillFrameMs where in the video the still was taken (iOS `stillFrameSeconds`).
 *   The video's first play starts there, so the hand-over from the still shows no jump.
 */
enum class OnboardingIntroScene(
    @StringRes val title: Int,
    @StringRes val subtitle: Int,
    @RawRes private val videoLight: Int,
    @RawRes private val videoDark: Int,
    @DrawableRes private val stillLight: Int,
    @DrawableRes private val stillDark: Int,
    val stillFrameMs: Int,
) {
    /** A, walking: « Parlez. Dictus écrit. » */
    Walking(
        R.string.onboarding_intro_walking_title,
        R.string.onboarding_intro_walking_subtitle,
        R.raw.intro_a_light,
        R.raw.intro_a_dark,
        R.drawable.intro_a_still_light,
        R.drawable.intro_a_still_dark,
        stillFrameMs = 4_500,
    ),

    /** B, the metro with no network: « Même sans réseau. » */
    Metro(
        R.string.onboarding_intro_metro_title,
        R.string.onboarding_intro_metro_subtitle,
        R.raw.intro_b_light,
        R.raw.intro_b_dark,
        R.drawable.intro_b_still_light,
        R.drawable.intro_b_still_dark,
        stillFrameMs = 4_500,
    ),

    /** C, the Dictus keyboard in a note: « Dans votre clavier. » */
    Keyboard(
        R.string.onboarding_intro_keyboard_title,
        R.string.onboarding_intro_keyboard_subtitle,
        R.raw.intro_c_light,
        R.raw.intro_c_dark,
        R.drawable.intro_c_still_light,
        R.drawable.intro_c_still_dark,
        stillFrameMs = 3_000,
    );

    @RawRes
    fun video(dark: Boolean): Int = if (dark) videoDark else videoLight

    @DrawableRes
    fun still(dark: Boolean): Int = if (dark) stillDark else stillLight
}
