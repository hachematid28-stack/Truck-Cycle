package com.example.data.model

object Constants {
    val TRUCKS = listOf(
        "KOM303", "KOM304", "KOM305", "KOM315",
        "KOM317", "KOM326", "KOM327", "KOM330",
        "CAT210", "CAT211", "CAT212", "CAT213", "CAT214", "CAT215", "CAT216", "CAT217",
        "HIT001", "HIT002", "HIT003", "HIT004"
    )

    val MACHINES = listOf(
        "KOM1", "KOM3", "KOM5", "KOM6",
        "CAT6040", "CAT1", "CAT2",
        "LIBER4"
    )

    val DUMP_POINTS = listOf(
        "TR1", "TR2",
        "STK TBT", "STK BTN", "STK BTR", "STK MT",
        "DECHARCH"
    )

    val BREAKDOWN_TYPES = listOf(
        "Fuel",
        "Hydraulic oil",
        "Mechanical issue",
        "Other"
    )

    fun formatDuration(millis: Long): String {
        val totalSeconds = millis / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format("%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }
    }

    fun formatDurationExtended(millis: Long): String {
        val totalSeconds = millis / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            "${hours}h ${minutes}m ${seconds}s"
        } else if (minutes > 0) {
            "${minutes}m ${seconds}s"
        } else {
            "${seconds}s"
        }
    }
}
