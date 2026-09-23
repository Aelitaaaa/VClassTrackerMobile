package com.dzaky.vclasstracker.assistant

import com.dzaky.vclasstracker.data.remote.VClassDeadline

object KaedePersona {

    const val NAME = "Kaede"

    fun getUserCallName(
        displayName: String
    ): String {
        val parts = displayName
            .trim()
            .split(
                Regex("\\s+")
            )
            .filter {
                it.isNotBlank()
            }

        if (parts.isEmpty()) {
            return "Master"
        }

        if (parts.size >= 2) {
            val firstName = parts[0]
                .lowercase()

            val commonFirstNames = setOf(
                "muhamad",
                "muhammad",
                "mohamad",
                "mohammad",
                "mohammed",
                "ahmad",
                "achmad"
            )

            if (
                firstName in commonFirstNames
            ) {
                return capitalizeName(
                    parts[1]
                )
            }
        }

        return capitalizeName(
            parts[0]
        )
    }

    fun greeting(
        displayName: String
    ): String {
        val userName =
            getUserCallName(
                displayName
            )

        return listOf(
            "Hmph, akhirnya buka VClass juga, $userName.",
            "O-oh, $userName. Kaede cuma sedang memastikan tugasmu tidak berantakan.",
            "$userName datang juga. Jangan salah paham, Kaede tidak menunggumu.",
            "Selamat datang, $userName. Cepat cek tugasmu sebelum menumpuk."
        ).random()
    }

    fun newTask(
        displayName: String,
        deadline: VClassDeadline
    ): String {
        val userName =
            getUserCallName(
                displayName
            )

        return listOf(
            "D-$userName! Ada ${deadline.type.lowercase()} baru: ${deadline.title}. Kaede cuma ngasih tahu, jangan geer!",
            "$userName, ${deadline.courseName} punya ${deadline.type.lowercase()} baru. Jangan bilang Kaede tidak mengingatkanmu.",
            "Hei, $userName! Ada ${deadline.type.lowercase()} baru di ${deadline.courseName}. Cek sekarang!",
            "Kaede menemukan tugas baru, $userName. Hmph... sebaiknya jangan ditunda."
        ).random()
    }

    fun deadlineReminder(
        displayName: String,
        deadline: VClassDeadline
    ): String {
        return when (
            deadline.urgency
        ) {
            "urgent" -> urgent(
                displayName,
                deadline
            )

            "soon" -> soon(
                displayName,
                deadline
            )

            else -> safe(
                displayName,
                deadline
            )
        }
    }

    fun urgent(
        displayName: String,
        deadline: VClassDeadline
    ): String {
        val userName =
            getUserCallName(
                displayName
            )

        return listOf(
            "D-$userName! ${deadline.timeLeft}! Cepat kerjakan ${deadline.title}, baka!",
            "$userName! Ini sudah mepet! ${deadline.title} tinggal ${deadline.timeLeft.lowercase()}!",
            "Kaede serius sekarang, $userName. ${deadline.title} tinggal ${deadline.timeLeft.lowercase()}!",
            "Hei! Jangan santai, $userName! Deadline ${deadline.courseName} tinggal ${deadline.timeLeft.lowercase()}!"
        ).random()
    }

    fun soon(
        displayName: String,
        deadline: VClassDeadline
    ): String {
        val userName =
            getUserCallName(
                displayName
            )

        return listOf(
            "$userName, ${deadline.title} ${deadline.timeLeft.lowercase()}. Kaede sudah ingetin ya.",
            "Jangan ditunda terus, $userName. Deadline ${deadline.courseName} ${deadline.timeLeft.lowercase()}.",
            "Hmph... Kaede cuma mengingatkan, ${deadline.title} sudah semakin dekat.",
            "$userName, lebih baik cicil dari sekarang. ${deadline.title} ${deadline.timeLeft.lowercase()}."
        ).random()
    }

    fun safe(
        displayName: String,
        deadline: VClassDeadline
    ): String {
        val userName =
            getUserCallName(
                displayName
            )

        return listOf(
            "Masih ${deadline.timeLeft.lowercase()}, $userName. Bukan berarti boleh malas.",
            "$userName, deadline ${deadline.title} masih cukup lama. Kerjakan sebelum Kaede harus marah.",
            "Kaede cuma kasih pengingat awal. ${deadline.title} masih ${deadline.timeLeft.lowercase()}.",
            "Hmph, masih ada waktu, $userName. Jangan dipakai buat menunda semuanya."
        ).random()
    }

    fun noDeadline(
        displayName: String
    ): String {
        val userName =
            getUserCallName(
                displayName
            )

        return listOf(
            "Tidak ada deadline aktif, $userName. Jangan senang dulu, cek lagi nanti.",
            "Untuk sekarang aman, $userName. Hmph... Kaede tetap akan mengawasi.",
            "Belum ada deadline dekat. Jangan jadikan alasan buat malas, $userName.",
            "Sepertinya aman untuk sekarang, $userName. Kaede cuma bilang untuk sekarang."
        ).random()
    }

    fun syncSuccess(
        displayName: String,
        courseCount: Int,
        deadlineCount: Int
    ): String {
        val userName =
            getUserCallName(
                displayName
            )

        return when {
            deadlineCount == 0 -> {
                "$userName, Kaede sudah cek $courseCount kelas. Tidak ada deadline aktif sekarang."
            }

            deadlineCount == 1 -> {
                "$userName, Kaede menemukan 1 deadline dari $courseCount kelas. Jangan diabaikan."
            }

            else -> {
                "$userName, Kaede menemukan $deadlineCount deadline dari $courseCount kelas. Hmph, lumayan banyak."
            }
        }
    }

    fun completed(
        displayName: String,
        taskTitle: String
    ): String {
        val userName =
            getUserCallName(
                displayName
            )

        return listOf(
            "Hmph... $taskTitle selesai juga. Lumayan, $userName.",
            "Bagus, $userName. Jangan salah paham, Kaede cuma menghargai pekerjaan yang selesai.",
            "$taskTitle sudah selesai? Y-ya baguslah, $userName.",
            "Akhirnya selesai juga. Kaede kira kamu bakal menundanya lagi, $userName."
        ).random()
    }

    fun late(
        displayName: String,
        deadline: VClassDeadline
    ): String {
        val userName =
            getUserCallName(
                displayName
            )

        return listOf(
            "$userName... deadline ${deadline.title} sudah lewat. Kaede kan sudah mengingatkan.",
            "Hmph! Telat juga akhirnya. Lain kali dengarkan Kaede, $userName.",
            "${deadline.title} sudah melewati deadline, $userName. Jangan ulangi lagi.",
            "Kaede benar-benar kecewa kalau kamu terus telat seperti ini, $userName."
        ).random()
    }

    fun notificationTitle(
        deadline: VClassDeadline
    ): String {
        return when (
            deadline.urgency
        ) {
            "urgent" ->
                "Kaede · Deadline mepet!"

            "soon" ->
                "Kaede · Jangan lupa ya"

            else ->
                "Kaede · Pengingat VClass"
        }
    }

    private fun capitalizeName(
        value: String
    ): String {
        return value
            .lowercase()
            .replaceFirstChar {
                it.uppercase()
            }
    }
}