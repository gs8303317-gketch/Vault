package app.vault.workspace.media

import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks

data class SelectableTrack(
    val groupIndex: Int,
    val trackIndex: Int,
    val label: String,
    val selected: Boolean,
)

fun collectSelectableTracks(tracks: Tracks, type: Int): List<SelectableTrack> {
    val out = mutableListOf<SelectableTrack>()
    tracks.groups.forEachIndexed { groupIndex, group ->
        if (group.type != type || !group.isSupported) return@forEachIndexed
        for (i in 0 until group.length) {
            if (!group.isTrackSupported(i)) continue
            val format = group.getTrackFormat(i)
            val label = format.label
                ?: format.language?.uppercase()
                ?: "Track ${out.size + 1}"
            out += SelectableTrack(
                groupIndex = groupIndex,
                trackIndex = i,
                label = label,
                selected = group.isTrackSelected(i),
            )
        }
    }
    return out
}

fun applyTrackOverride(
    player: Player,
    tracks: Tracks,
    type: Int,
    selection: SelectableTrack?,
) {
    val builder = player.trackSelectionParameters.buildUpon()
        .clearOverridesOfType(type)
        .setTrackTypeDisabled(type, false)
    if (selection == null) {
        // Disable this type (e.g. turn subtitles off)
        builder.setTrackTypeDisabled(type, true)
    } else {
        val group = tracks.groups.getOrNull(selection.groupIndex) ?: return
        builder.setOverrideForType(
            TrackSelectionOverride(group.mediaTrackGroup, listOf(selection.trackIndex)),
        )
    }
    player.trackSelectionParameters = builder.build()
}
