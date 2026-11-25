package de.basecore.klangkiste2.data
import java.util.UUID

data class PickedDocument(val uriString: String, val name: String, val relativePath: String)
data class ImportCandidate(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val audioFiles: List<PickedDocument> = emptyList(),
    val cover: PickedDocument? = null,
    val cue: PickedDocument? = null,
    val description: String = "",
    val ageRecommendation: Int? = null,
    val genre: String = "Hörspiel",
    val runtimeMinutes: Int? = null,
    val tags: List<String> = emptyList(),
    val expectedAudioNames: List<String> = audioFiles.map { it.name },
    val coverFileName: String? = cover?.name,
    val legacyTagId: String? = null,
    val remoteAudioUrl: String? = null,
    val remoteCoverUrl: String? = null,
    val remoteCueUrl: String? = null,
    val selected: Boolean = true
)
