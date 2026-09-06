package app.vault.workspace.media

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.extractor.ConstantBitrateSeekMap
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorInput
import androidx.media3.extractor.ExtractorOutput
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.extractor.PositionHolder
import androidx.media3.extractor.SeekMap

/**
 * Wraps [DefaultExtractorsFactory] so an unseekable [SeekMap] with a known duration
 * and content length is replaced by a [ConstantBitrateSeekMap] estimate.
 *
 * Fragmented MP4 / WEB-DL without sidx/mfra often reports [SeekMap.isSeekable] == false;
 * ExoPlayer [ProgressiveMediaPeriod] then forces every seek to position 0. A CBR map
 * lets scrub/seek land near the requested time (keyframe-approximate).
 *
 * [knownContentLength] should be the plaintext size (e.g. from VAULT1 header). Length
 * is also refreshed from [ExtractorInput.getLength] when available during sniff/read.
 */
class SeekableFallbackExtractorsFactory(
    private val knownContentLength: Long,
    private val delegate: DefaultExtractorsFactory =
        DefaultExtractorsFactory().setConstantBitrateSeekingEnabled(true),
) : ExtractorsFactory {

    override fun createExtractors(): Array<Extractor> =
        wrapAll(delegate.createExtractors())

    override fun createExtractors(
        uri: Uri,
        responseHeaders: Map<String, List<String>>,
    ): Array<Extractor> = wrapAll(delegate.createExtractors(uri, responseHeaders))

    private fun wrapAll(extractors: Array<Extractor>): Array<Extractor> =
        Array(extractors.size) { i ->
            SeekableFallbackExtractor(extractors[i], knownContentLength)
        }
}

/**
 * Pure SeekMap rewrite used by the extractor wrapper — unit-testable on JVM.
 */
object SeekableFallback {
    /**
     * If [seekMap] is already seekable, or duration/length are unknown, return it unchanged.
     * Otherwise build a CBR estimate:
     * `bitrate ≈ (contentLength * 8 * 1_000_000 / durationUs)`.
     */
    fun maybeReplace(seekMap: SeekMap, contentLength: Long): SeekMap {
        if (seekMap.isSeekable) return seekMap
        val durationUs = seekMap.durationUs
        if (durationUs == C.TIME_UNSET || durationUs <= 0L) return seekMap
        if (contentLength <= 0L) return seekMap
        val bitrate = ((contentLength * 8L * 1_000_000L) / durationUs)
            .toInt()
            .coerceAtLeast(1)
        return ConstantBitrateSeekMap(
            /* inputLength= */ contentLength,
            /* firstFrameBytePosition= */ 0L,
            /* bitrate= */ bitrate,
            /* frameSize= */ 1,
            /* allowSeeksIfLengthUnknown= */ true,
        )
    }
}

internal class SeekableFallbackExtractor(
    private val delegate: Extractor,
    private val knownContentLength: Long,
) : Extractor {
    @Volatile
    private var observedLength: Long = knownContentLength

    override fun sniff(input: ExtractorInput): Boolean {
        captureLength(input)
        return delegate.sniff(input)
    }

    override fun init(output: ExtractorOutput) {
        delegate.init(
            SeekableFallbackExtractorOutput(output) { observedLength },
        )
    }

    override fun read(input: ExtractorInput, seekPosition: PositionHolder): Int {
        captureLength(input)
        return delegate.read(input, seekPosition)
    }

    override fun seek(position: Long, timeUs: Long) {
        delegate.seek(position, timeUs)
    }

    override fun release() {
        delegate.release()
    }

    override fun getUnderlyingImplementation(): Extractor =
        delegate.underlyingImplementation

    private fun captureLength(input: ExtractorInput) {
        val len = input.length
        if (len > 0L) {
            observedLength = len
        } else if (knownContentLength > 0L) {
            observedLength = knownContentLength
        }
    }
}

internal class SeekableFallbackExtractorOutput(
    private val delegate: ExtractorOutput,
    private val contentLength: () -> Long,
) : ExtractorOutput {
    override fun track(id: Int, type: Int) = delegate.track(id, type)

    override fun endTracks() = delegate.endTracks()

    override fun seekMap(seekMap: SeekMap) {
        delegate.seekMap(SeekableFallback.maybeReplace(seekMap, contentLength()))
    }
}
