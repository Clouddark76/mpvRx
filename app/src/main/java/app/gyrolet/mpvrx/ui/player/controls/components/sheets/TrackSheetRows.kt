package app.gyrolet.mpvrx.ui.player.controls.components.sheets

import app.gyrolet.mpvrx.ui.icons.Icon
import app.gyrolet.mpvrx.ui.icons.Icons

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.gyrolet.mpvrx.R
import app.gyrolet.mpvrx.ui.player.TrackNode
import app.gyrolet.mpvrx.ui.theme.spacing

@Composable
fun AddTrackRow(
  title: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  actions: @Composable RowScope.() -> Unit = {},
) {
  Row(
    modifier =
      modifier
        .fillMaxWidth()
        .clickable(onClick = onClick)
        .height(56.dp)
        .padding(horizontal = MaterialTheme.spacing.medium),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.smaller),
  ) {
    Icon(
      Icons.Default.Add,
      contentDescription = null,
      modifier = Modifier.size(24.dp),
    )
    Text(
      text = title,
      style = MaterialTheme.typography.bodyLarge,
      modifier = Modifier.weight(1f),
    )
    Row(
      horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      actions()
    }
  }
}

@Composable
fun getTrackTitle(track: TrackNode): String {
  val hasTitle = !track.title.isNullOrBlank()
  val hasLang = !track.lang.isNullOrBlank()

  if (track.isSubtitle && track.external == true && !hasTitle && !hasLang && track.externalFilename != null) {
    val decoded = Uri.decode(track.externalFilename)
    val fileName = decoded.substringAfterLast("/")
    return stringResource(R.string.player_sheets_track_title_wo_lang, track.id, fileName)
  }

  return when {
    hasTitle && hasLang ->
      stringResource(
        R.string.player_sheets_track_title_w_lang,
        track.id,
        track.title,
        track.lang,
      )
    hasTitle -> stringResource(R.string.player_sheets_track_title_wo_lang, track.id, track.title)
    hasLang -> stringResource(R.string.player_sheets_track_lang_wo_title, track.id, track.lang)
    track.isSubtitle -> stringResource(R.string.player_sheets_chapter_title_substitute_subtitle, track.id)
    track.isAudio -> stringResource(R.string.player_sheets_chapter_title_substitute_audio, track.id)
    else -> ""
  }
}

/**
 * Returns a short human-readable format badge for a track, or null if nothing meaningful
 * can be derived. Examples: "EAC3", "FLAC", "ASS", "SRT", "AAC 5.1"
 *
 * Priority: codecDesc (human label from mpv) → codec (raw codec id, cleaned up).
 * For audio tracks, channel count is appended when available (e.g. "EAC3 2.0", "AAC 5.1").
 */
fun getTrackCodecBadge(track: TrackNode): String? {
  val rawCodec = track.codec?.trim()?.takeIf { it.isNotBlank() } ?: return null

  // Prefer codecDesc when it's short and meaningful (mpv gives e.g. "E-AC-3", "FLAC", "SubStation Alpha")
  // but skip it when it's too verbose (e.g. "MPEG Audio Layer 3") — use the cleaned raw id instead.
  val baseLabel: String = run {
    val desc = track.codecDesc?.trim()
    when {
      desc.isNullOrBlank() -> cleanCodecId(rawCodec)
      desc.length > 12    -> cleanCodecId(rawCodec)
      else                -> desc
    }
  }

  // Append channel layout for audio tracks (2.0, 5.1, 7.1, etc.)
  val channelSuffix = if (track.isAudio) {
    when (track.demuxChannelCount) {
      1L -> " Mono"
      2L -> " 2.0"
      6L -> " 5.1"
      8L -> " 7.1"
      else -> null
    }
  } else null

  // Bitrate solo para audio, en kb/s redondeado
  val bitrateSuffix = if (track.isAudio) {
    track.demuxBitrate
      ?.takeIf { it > 0 }
      ?.let { bps ->
        val kbps = (bps / 1000.0).let {
          // Redondear a múltiplos de 8 para tasas CBR estándar (224, 320, etc.)
          // Si ya es redondo, mostrarlo directo; si no, un decimal
          if (it % 1.0 == 0.0) "${it.toLong()} kb/s" else "${"%.0f".format(it)} kb/s"
        }
        " · $kbps"
      }
  } else null

  return (baseLabel + (channelSuffix ?: "") + (bitrateSuffix ?: "")).trim().takeIf { it.isNotBlank() }
}

/**
 * Maps raw mpv codec IDs to compact display labels.
 * Covers the most common cases; unknown codecs are returned as-is uppercased (trimmed to 8 chars).
 */
private fun cleanCodecId(codec: String): String {
  return when (codec.lowercase()) {
    "eac3", "a_eac3"         -> "EAC3"
    "ac3", "a_ac3"           -> "AC3"
    "dts", "a_dts"           -> "DTS"
    "truehd", "a_truehd"     -> "TrueHD"
    "aac", "a_aac"           -> "AAC"
    "flac", "a_flac"         -> "FLAC"
    "opus", "a_opus"         -> "OPUS"
    "mp3", "a_mp3"           -> "MP3"
    "vorbis", "a_vorbis"     -> "OGG"
    "pcm_s16le", "pcm_s24le",
    "pcm_s32le", "pcm_f32le" -> "PCM"
    "ass", "s_text/ass",
    "ssa", "s_text/ssa"      -> "ASS"
    "subrip", "srt",
    "s_text/utf8"            -> "SRT"
    "webvtt", "s_text/webvtt"-> "VTT"
    "pgs", "hdmv_pgs_subtitle",
    "s_hdmv/pgs"             -> "PGS"
    "dvd_subtitle",
    "s_vobsub"               -> "VobSub"
    else                     -> codec.uppercase().take(8)
  }
}

/**
 * Small pill badge showing the codec/format label of a track.
 * Rendered inline next to the track title in Audio and Subtitle sheet rows.
 */
@Composable
fun TrackFormatBadge(
  label: String,
  modifier: Modifier = Modifier,
) {
  Text(
    text = label,
    style = MaterialTheme.typography.labelSmall,
    fontSize = 10.sp,
    color = MaterialTheme.colorScheme.onSecondaryContainer,
    modifier = modifier
      .background(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(4.dp),
      )
      .padding(horizontal = 5.dp, vertical = 2.dp),
  )
}
