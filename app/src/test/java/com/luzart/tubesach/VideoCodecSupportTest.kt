package com.luzart.tubesach

import android.media.MediaCodecInfo.CodecProfileLevel
import android.media.MediaFormat
import com.luzart.tubesach.player.VideoCodecSupport
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.MediaCodecInfoBuilder
import org.robolectric.shadows.ShadowMediaCodecList

/**
 * Kiểm tra khả năng giải mã VP9 bằng API thật của Android (MediaCodecList) với danh sách bộ giải mã giả lập
 * giống điện thoại thật: có/không có bộ giải mã phần cứng, giới hạn độ phân giải.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class VideoCodecSupportTest {

    @Before
    fun setUp() {
        ShadowMediaCodecList.reset()
        VideoCodecSupport.resetForTest()
    }

    @After
    fun tearDown() {
        ShadowMediaCodecList.reset()
        VideoCodecSupport.resetForTest()
    }

    /** Bộ giải mã VP9 khai báo giống file media_codecs.xml của nhà sản xuất. */
    private fun vp9Decoder(name: String, hardware: Boolean, maxW: Int, maxH: Int, maxFps: Int = 60) {
        val blocks = ((maxW + 15) / 16) * ((maxH + 15) / 16)
        val format = MediaFormat().apply {
            setString(MediaFormat.KEY_MIME, MediaFormat.MIMETYPE_VIDEO_VP9)
            setString("size-range", "16x16-${maxW}x$maxH")
            setString("frame-rate-range", "1-$maxFps")
            setString("block-size", "16x16")
            setString("alignment", "2x2")
            setString("block-count-range", "1-$blocks")
            setString("blocks-per-second-range", "1-${blocks.toLong() * maxFps}")
            setString("bitrate-range", "1-100000000")
        }
        val caps = MediaCodecInfoBuilder.CodecCapabilitiesBuilder.newBuilder()
            .setMediaFormat(format)
            .setIsEncoder(false)
            .setColorFormats(intArrayOf(android.media.MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible))
            .setProfileLevels(arrayOf(CodecProfileLevel().apply { profile = CodecProfileLevel.VP9Profile0; level = CodecProfileLevel.VP9Level62 }))
            .build()
        ShadowMediaCodecList.addCodec(
            MediaCodecInfoBuilder.newBuilder()
                .setName(name)
                .setIsEncoder(false)
                .setIsHardwareAccelerated(hardware)
                .setIsSoftwareOnly(!hardware)
                .setIsVendor(hardware)
                .setCapabilities(caps)
                .build(),
        )
    }

    @Test
    fun noVp9DecoderMeansH264() {
        assertFalse(VideoCodecSupport.vp9Available())
        assertFalse(VideoCodecSupport.vp9Supported(1280, 720, 30))
    }

    @Test
    fun softwareOnlyVp9IsNotUsed() {
        // Bộ giải mã phần mềm của Android: giải mã được nhưng nóng máy, hao pin → không dùng.
        vp9Decoder("c2.android.vp9.decoder", hardware = false, maxW = 4096, maxH = 2304)
        assertFalse(VideoCodecSupport.vp9Available())
        assertFalse(VideoCodecSupport.vp9Supported(1920, 1080, 30))
    }

    @Test
    fun hardware4kDecoder() {
        vp9Decoder("c2.exynos.vp9.decoder", hardware = true, maxW = 3840, maxH = 2160)
        assertTrue(VideoCodecSupport.vp9Available())
        assertTrue(VideoCodecSupport.vp9Supported(1920, 1080, 30))
        assertTrue(VideoCodecSupport.vp9Supported(3840, 2160, 30))
        assertTrue("Shorts dọc 1080×1920", VideoCodecSupport.vp9Supported(1080, 1920, 30))
        assertFalse("8K vượt khả năng", VideoCodecSupport.vp9Supported(7680, 4320, 30))
    }

    @Test
    fun hardwareDecoderLimitedTo1080p() {
        // Máy tầm trung: phần cứng chỉ tới 1080p → không có 2K/4K.
        vp9Decoder("OMX.MTK.VIDEO.DECODER.VP9", hardware = true, maxW = 1920, maxH = 1088)
        assertTrue(VideoCodecSupport.vp9Supported(1920, 1080, 30))
        assertFalse(VideoCodecSupport.vp9Supported(2560, 1440, 30))
        assertFalse(VideoCodecSupport.vp9Supported(3840, 2160, 30))
    }

    @Test
    fun hardwareDecoderTooSlowFor60fps4k() {
        vp9Decoder("c2.qti.vp9.decoder", hardware = true, maxW = 3840, maxH = 2160, maxFps = 30)
        assertTrue(VideoCodecSupport.vp9Supported(3840, 2160, 30))
        assertFalse("4K 60 khung/giây vượt khả năng", VideoCodecSupport.vp9Supported(3840, 2160, 60))
    }

    @Test
    @Config(sdk = [23])
    fun oldAndroidAlwaysH264() {
        vp9Decoder("OMX.qcom.video.decoder.vp9", hardware = true, maxW = 3840, maxH = 2160)
        assertFalse("Android 6: luôn dùng H.264", VideoCodecSupport.vp9Supported(1920, 1080, 30))
    }
}
