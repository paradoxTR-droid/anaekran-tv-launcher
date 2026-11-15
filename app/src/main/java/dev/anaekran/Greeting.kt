package dev.anaekran

import kotlin.random.Random

enum class TimeSlot { SABAH, GUNDUZ, AKSAM, GECE }

/** 05–11 sabah, 11–17 gündüz, 17–22 akşam, 22–05 gece. */
fun slotFor(hour: Int): TimeSlot = when (hour) {
    in 5..10 -> TimeSlot.SABAH
    in 11..16 -> TimeSlot.GUNDUZ
    in 17..21 -> TimeSlot.AKSAM
    else -> TimeSlot.GECE
}

val GREETINGS: Map<TimeSlot, List<String>> = mapOf(
    TimeSlot.SABAH to listOf(
        "Günaydın, kahve hazır mı?",
        "Güne güzel bir başlangıç",
        "Sabah sabah ne izliyoruz?",
        "Günaydın, bugün senin günün",
    ),
    TimeSlot.GUNDUZ to listOf(
        "Öğleden sonra molası",
        "Biraz ara vermenin tam zamanı",
        "Gün ortası keyfi",
        "Kısa bir mola, uzun bir film?",
    ),
    TimeSlot.AKSAM to listOf(
        "Akşam keyfi",
        "Günün yorgunluğunu at",
        "Çaylar demlendi, perde açılıyor",
        "Bu akşam ne izliyoruz?",
    ),
    TimeSlot.GECE to listOf(
        "Gece sineması başlasın",
        "Işıkları kıs, sesi aç",
        "Bir bölüm daha mı?",
        "Gece uzun, liste kısa",
    ),
)

fun pickGreeting(slot: TimeSlot, random: Random): String = GREETINGS.getValue(slot).random(random)
