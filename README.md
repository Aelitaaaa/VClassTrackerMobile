# VClass Tracker

<p align="center">
  <strong>VClass companion app for Gunadarma students</strong><br>
  Native Android app built with Kotlin + Jetpack Compose.
</p>

<p align="center">
  <img alt="Android" src="https://img.shields.io/badge/Android-API%2026%2B-3DDC84?logo=android&logoColor=white">
  <img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-2.3.20-7F52FF?logo=kotlin&logoColor=white">
  <img alt="Jetpack Compose" src="https://img.shields.io/badge/Jetpack%20Compose-UI-4285F4?logo=jetpackcompose&logoColor=white">
  <img alt="Version" src="https://img.shields.io/badge/version-1.0.0-blue">
</p>

---

## Tentang VClass Tracker

**VClass Tracker** adalah aplikasi Android pendamping untuk membantu mahasiswa memantau aktivitas VClass Gunadarma dengan tampilan yang lebih ringkas dan nyaman.

Aplikasi terhubung langsung ke VClass dan menyediakan ringkasan kelas, deadline, detail tugas, notifikasi, serta sinkronisasi background.

> VClass Tracker bukan aplikasi resmi Universitas Gunadarma dan tidak menggantikan website VClass. Pengumpulan tugas, pengerjaan quiz, forum, dan aktivitas akademik lain tetap dilakukan melalui VClass resmi.

---

## Fitur

- Login menggunakan akun VClass
- Dashboard ringkas
- Daftar mata kuliah yang terdaftar
- Filter semester
- Pencarian mata kuliah
- Daftar deadline dan aktivitas
- Detail tugas/deadline
- Tombol **Buka di VClass**
- Notifikasi tugas baru dan deadline
- Background sync menggunakan WorkManager
- Sinkronisasi manual melalui halaman Profil
- Deep link dari notifikasi menuju detail tugas
- Session login tersimpan secara terenkripsi
- Bottom navigation: Beranda, Tugas, Kelas, Profil
- Kaede sebagai karakter assistant aplikasi

### Tentang fitur pencarian kelas

Menu pencarian kelas digunakan untuk mencari mata kuliah di VClass.

Fitur tersebut **tidak melakukan enrollment otomatis** ke mata kuliah. Enrollment tetap mengikuti mekanisme yang tersedia di VClass.

---

## Kaede

Kaede adalah assistant kecil di VClass Tracker yang membantu memberi karakter pada pengalaman penggunaan aplikasi.

Ekspresi Kaede dapat berubah sesuai konteks, misalnya ketika ada tugas baru atau deadline yang semakin dekat.

---

## Cara Menggunakan

### 1. Install APK

Unduh APK terbaru dari halaman **Releases** repository ini.

File release menggunakan format nama seperti:

```text
VClass-Tracker-v1.0.0.apk
```

Install APK di perangkat Android.

Jika Android meminta izin instalasi dari sumber lain, aktifkan izin tersebut hanya untuk aplikasi yang digunakan untuk membuka APK.

### 2. Login

Buka VClass Tracker lalu login menggunakan akun VClass Gunadarma.

Aplikasi tidak menyimpan password VClass.

Session login disimpan secara lokal dalam bentuk terenkripsi menggunakan Android Keystore.

### 3. Dashboard

Setelah login, halaman Beranda menampilkan informasi utama akun dan akses cepat menuju fitur aplikasi.

### 4. Kelas

Buka menu **Kelas** untuk melihat mata kuliah yang terdaftar.

Kamu juga dapat menggunakan fitur pencarian kelas untuk mencari mata kuliah di VClass.

### 5. Tugas

Buka menu **Tugas** untuk melihat deadline yang tersedia.

Tekan salah satu tugas untuk melihat detail seperti:

- Nama aktivitas
- Mata kuliah
- Kode mata kuliah
- Jenis aktivitas
- Deadline
- Sisa waktu
- Status deadline

Gunakan tombol **Buka di VClass** untuk membuka halaman aktivitas tersebut di browser.

### 6. Sinkronisasi

Aplikasi melakukan sinkronisasi background secara berkala.

Sinkronisasi manual juga tersedia melalui:

```text
Profil → Sinkronkan sekarang
```

### 7. Notifikasi

Jika izin notifikasi diberikan, VClass Tracker dapat memberi notifikasi untuk aktivitas baru dan deadline tertentu.

Menekan notifikasi dapat membawa kamu ke daftar tugas atau detail tugas terkait.

---

## Privasi & Keamanan

VClass Tracker dirancang tanpa backend tambahan.

Koneksi dilakukan langsung dari aplikasi ke layanan VClass Gunadarma.

- Password VClass tidak disimpan oleh aplikasi
- Session disimpan secara lokal
- Data session dilindungi menggunakan AES/GCM melalui Android Keystore
- Signing key aplikasi tidak disertakan dalam repository
- File konfigurasi lokal dan credential tidak disertakan dalam repository

---

## Build dari Source

### Requirements

- JDK 17 atau lebih baru untuk toolchain project
- Android SDK
- Android SDK Platform 36
- Android Build Tools
- Git

Clone repository:

```bash
git clone <repository-url>
cd VClassTrackerMobile
```

Build debug APK:

```bash
./gradlew assembleDebug
```

Windows PowerShell:

```powershell
.\gradlew.bat assembleDebug
```

APK debug akan tersedia di:

```text
app/build/outputs/apk/debug/app-debug.apk
```

### Release Signing

File signing berikut sengaja **tidak disertakan** di repository:

```text
keystore.properties
*.jks
*.keystore
```

Tanpa file signing pribadi, project tetap dapat digunakan untuk development/debug build.

Untuk membuat signed release milik sendiri, buat keystore dan konfigurasi `keystore.properties` secara lokal.

Jangan pernah commit password atau private signing key ke repository publik.

---

## Teknologi

- Kotlin
- Jetpack Compose
- Android SDK
- OkHttp
- Jsoup
- Kotlin Coroutines
- WorkManager
- Android Keystore
- VClass Gunadarma

---

## Struktur Singkat

```text
app/src/main/java/com/dzaky/vclasstracker/
├── MainActivity.kt
├── assistant/
│   └── KaedePersona.kt
├── data/
│   ├── local/
│   │   └── VClassSessionStore.kt
│   └── remote/
│       └── VClassClient.kt
├── notification/
│   └── NotificationHelper.kt
├── ui/
│   └── main/
│       ├── components/
│       ├── courses/
│       ├── dashboard/
│       ├── login/
│       ├── profile/
│       └── tasks/
└── worker/
    └── VClassSyncWorker.kt
```

---

## Catatan

Karena VClass merupakan layanan eksternal, perubahan pada struktur halaman, endpoint, atau sistem autentikasi VClass dapat memengaruhi beberapa fitur aplikasi.

Jika terjadi perubahan pada VClass, aplikasi mungkin memerlukan penyesuaian pada parser atau proses sinkronisasi.

---

## Credits

### Ayip

Project ini mendapat inspirasi dan referensi awal dari project **VClassTracker** milik **Ayip (ayiprmd)**.

Repository:

https://github.com/ayiprmd/VClassTracker

Terima kasih kepada Ayip karena project tersebut membantu memberikan referensi dan gambaran awal dalam pengembangan VClass Tracker versi Android native ini.

### Development

**Dzaky Putra**

VClass Tracker Android dibuat sebagai project independen menggunakan Kotlin dan Jetpack Compose.

---

## Disclaimer

VClass Tracker adalah project independen dan **bukan aplikasi resmi Universitas Gunadarma**.

Nama, layanan, dan sistem VClass tetap menjadi milik pihak terkait. Aplikasi ini dibuat sebagai companion tool untuk membantu pengguna mengakses dan memantau informasi VClass dengan lebih praktis.

Pengguna tetap bertanggung jawab untuk memastikan seluruh tugas, quiz, forum, dan aktivitas akademik diselesaikan melalui layanan VClass resmi.

---

<p align="center">
  Made with Kotlin, Jetpack Compose, and a little help from Kaede.
</p>
