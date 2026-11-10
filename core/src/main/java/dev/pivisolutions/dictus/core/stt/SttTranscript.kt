package dev.pivisolutions.dictus.core.stt

/**
 * What an engine returned within the dictation timeout (#179).
 *
 * @param text Raw text (before post-processing).
 * @param segmentCount Segments the text is made of, or null for an engine without segments.
 * @param complete False when the timeout cut the decode: [text] then holds only the segments
 *   decoded before it, and the end of the dictation is missing.
 */
data class SttTranscript(val text: String, val segmentCount: Int?, val complete: Boolean)
