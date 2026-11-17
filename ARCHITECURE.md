# Jaga Daun — Arsitektur Android

## Stack dan pembagian tugas

| Lapisan | Implementasi |
| --- | --- |
| Android | Kotlin, Jetpack Compose, Material 3, ViewModel, StateFlow, dan Navigation Compose. Satu modul aplikasi. |
| Data lokal | Room dengan KSP untuk metadata dan penyimpanan privat aplikasi untuk foto. |
| Kamera dan galeri | Kamera sistem melalui TakePicture dengan URI FileProvider. Android Photo Picker untuk galeri. |
| Pengingat | WorkManager untuk penjadwalan lokal yang toleran terhadap pergeseran waktu dan notifikasi Android. |
| Identifikasi | Cloudflare Worker TypeScript sebagai penghubung ke Pl@ntNet. Wrangler untuk pengujian dan deploy. |
| Pemeriksaan | Endpoint `/v1/health-check` pada Worker yang sama, memakai Pl@ntNet Diseases API. Hasil adalah dugaan, dengan panduan pencegahan terkurasi lokal. |
| Alat kerja | OMP, VS Code, Git, JDK, Android SDK, Gradle Wrapper, dan ADB. Node.js/npm dipakai pada folder Worker. |

Gunakan kombinasi JDK, AGP, Kotlin, KSP, dan Gradle yang kompatibel. Catat versi dalam repositori. Pilih minSdk setelah memeriksa HP keluarga. Pisahkan proyek dari Jaga Uang.

## Struktur awal

- `app/` berisi navigasi, layar, tema, data lokal, kamera, dan pengingat.
- `worker/` berisi route identifikasi, adapter penyedia, validasi unggahan, dan konfigurasi Wrangler.
- Gunakan repositori untuk menghubungkan ViewModel dengan Room, file foto, dan layanan pengenalan. Kelola dependensi dengan satu kontainer manual pada aplikasi.

## Model data

| Entitas | Bidang inti |
| --- | --- |
| Plant | ID, nama tampilan, nama ilmiah opsional, catatan, referensi foto, asal data manual atau teridentifikasi, dan waktu dibuat. |
| Reminder | ID, ID tanaman, jenis perawatan, interval, waktu pilihan, zona waktu jadwal, status aktif, serta waktu pengingat berikutnya. |
| CareEvent | ID, ID pengingat, dan waktu kegiatan selesai. Gunakan identitas kejadian agar ketukan ulang tidak menggandakan catatan. |
| HealthRecord | ID pemeriksaan, ID tanaman opsional, waktu, dugaan terpilih (kode/label/skor), ketidakpastian, satu sampai tiga foto lokal, catatan, dan status perkembangan menurut pengguna. |

Simpan profil tanaman secara atomik di Room. Salin foto yang dipilih ke penyimpanan privat dan hanya simpan referensi filenya di database. Hapus salinan sementara jika simpan gagal atau draf dibatalkan. Saat tanaman dihapus, bersihkan pengingat dan file yang tidak lagi dirujuk. Simpan skema Room di repositori dan uji migrasi dengan data lama.

## Alur identifikasi

1. Ambil foto dan tampilkan pratinjau. Buat salinan unggahan dengan resolusi yang dibatasi dan metadata lokasi dibuang. Uji batas ukuran dan kualitas pada foto tanaman sasaran.
2. Android mengirim foto yang sudah dipilih melalui HTTPS ke Worker saat pengguna meminta identifikasi. Worker membatasi akses, ukuran unggahan, waktu proses, dan jumlah permintaan.
3. Worker meneruskan foto ke Pl@ntNet tanpa menyimpan foto di server. Simpan API key sebagai secret. Untuk distribusi keluarga, gunakan autentikasi endpoint yang dapat dicabut dan batas pemakaian per perangkat atau pengguna. Hindari menaruh kunci penyedia di APK atau Git.
4. Worker mengembalikan struktur kandidat yang stabil, termasuk nama ilmiah, nama umum opsional, dan skor penyedia. Bedakan status foto sulit dikenali, kuota habis, serta layanan gagal. Tangani permintaan berbayar secara eksplisit agar pengiriman ulang tidak otomatis menghabiskan kredit.
5. Simpan kandidat yang dikonfirmasi pengguna ke Room. Pertahankan form dan foto saat proses jaringan gagal. Terapkan atribusi penyedia sesuai ketentuan layanan.

Gunakan endpoint terpisah untuk pemeriksaan masalah. Identifikasi dan pemeriksaan berbagi batas percobaan keluarga yang sama karena keduanya memakai kuota Pl@ntNet yang sama. Catat metrik teknis agregat tanpa foto atau catatan pribadi. Pengingat, koleksi, dan tambah manual berjalan tanpa jaringan. Pemrosesan foto oleh penyedia mengikuti kebijakan penyedia terkait.

## Status implementasi foto lokal

- Kamera memakai `TakePicture` dan FileProvider non-ekspor; hanya direktori cache `photo-captures/` yang dibagikan sementara. Galeri memakai Photo Picker tanpa izin baca seluruh penyimpanan.
- `PhotoStore` menyalin gambar ke draf privat, membatasi masukan 30 MiB, memperbaiki orientasi EXIF, dan menyimpan JPEG maksimal 2048 piksel tanpa metadata lokasi. Dekode dan penyalinan berjalan di dispatcher IO.
- Form tambah/ubah mempertahankan teks dan referensi draf saat layar dibuat ulang. Membatalkan form menghapus draf, bukan foto tanaman tersimpan. Foto yang hilang ditampilkan sebagai tidak tersedia.
- `PlantRepository.save` membuat salinan permanen sebelum transaksi Room; kegagalan transaksi membersihkan salinan permanen baru dan mempertahankan draf untuk dicoba lagi. Penggantian/hapus tanaman membersihkan foto lama yang tidak lagi dirujuk.
- Database versi 2 menambah tabel riwayat pemeriksaan lewat migrasi `1 → 2`; tidak memakai destructive fallback. Tabel tanaman, pengingat, dan kejadian perawatan dipertahankan; koleksi contoh tidak dibuat ulang saat kosong.
- Identifikasi nyata sudah terhubung melalui `https://jagadaun-identify.baniputrabangsawan0.workers.dev/v1/identify`. Pengiriman hanya setelah **Kenali Tanaman** atau **Coba lagi**; hasil maksimal tiga kandidat wajib dipilih sebelum form simpan. Nama, foto, dan asal teridentifikasi tersimpan bersama di Room.
- Uji foto di HP tanpa melepas aplikasi utama: build dengan `./gradlew testDebugUnitTest assembleDebug assembleDebugAndroidTest installDebug`, pasang test APK lewat `adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`, lalu jalankan `adb shell am instrument -w com.jagadaun.app.test/androidx.test.runner.AndroidJUnitRunner`. Hindari `connectedDebugAndroidTest` pada HP berisi data penting: runner Gradle dapat melepas APK setelah pengujian. `PhotoPersistenceTest` memakai database uji terpisah untuk orientasi/metadata, simpan-buka ulang-ganti-hapus, dan rollback salinan foto saat transaksi gagal.

## Deployment identifikasi keluarga

- Worker `jagadaun-identify` dan database D1 `jagadaun-identify-quota` dibuat melalui MCP Cloudflare. Worker lain tidak disentuh. Sumber, skema, dan binding non-secret ada di `worker/`; periksa dengan `npm ci`, `npm test`, `npm run typecheck`, dan `npm run build`.
- Secret Worker: `PLANTNET_API_KEY` dan `FAMILY_TOKEN`. Salinan lokal hanya di `~/.config/jagadaun/plantnet-api-key` dan `~/.config/jagadaun/family-token` dengan izin `600`, di luar repo. Kunci Pl@ntNet tidak dimasukkan ke APK.
- Gradle membaca token endpoint keluarga dari file lokal saat build. Token ini dapat diekstrak dari APK: distribusikan hanya ke keluarga, rotasi secret Worker dan build ulang untuk mencabut akses. UUID instalasi membagi kuota, bukan bukti identitas. Batas global tetap melindungi kuota jika UUID diganti.
- D1 menyimpan ID permintaan acak, tanggal UTC, dan hash UUID perangkat selama tujuh hari; tidak menyimpan foto, nama tanaman, atau catatan. Reservasi SQL atomik membatasi 20 percobaan per instalasi per hari dan 100 seluruh keluarga per hari. Kegagalan penyedia tetap dihitung; tidak ada retry otomatis.
- Identifikasi menerima satu JPEG maksimal 4 MiB. Pemeriksaan menerima multipart berisi satu sampai tiga JPEG, masing-masing maksimal 4 MiB, dengan batas total 12 MiB + 16 KiB overhead multipart. Baca unggahan maksimal 15 detik dan panggilan penyedia maksimal 20 detik. Semua foto dalam satu pemeriksaan memakai satu reservasi kuota dan satu panggilan penyedia. Respons memakai `no-store`; observability Worker dinonaktifkan agar URL upstream yang memuat API key tidak dicatat.
- Workers hanya mendukung `redirect: "manual"` atau `"follow"`; gunakan `manual` dan tolak respons 3xx agar secret tidak diteruskan ke tujuan lain. Browser-standard `redirect: "error"` menyebabkan `TypeError` pada runtime Workers.
- Health check `/health` hanya memeriksa binding, bukan menghabiskan kuota. Uji live dengan foto contoh dari dokumentasi Pl@ntNet menghasilkan **Hibiscus rosa-sinensis**; skor adalah ranking penyedia, bukan jaminan identitas. Uji otomatis memakai respons penyedia tiruan supaya tidak memakai kuota.

## Nama Indonesia dan pemeriksaan masalah

- Identifikasi meminta `lang=id`; `PlantNames` menambahkan nama Indonesia terkurasi berdasarkan kecocokan spesies persis, misalnya **Kembang sepatu**, **Tomat**, dan **Cabai**. Nama ilmiah tetap terlihat. Nama tanaman yang sudah disimpan atau diganti pengguna tidak ditimpa.
- `POST /v1/health-check` menerima multipart `images` berulang, satu sampai tiga JPEG yang dikode ulang tanpa metadata lokasi. Android dan Worker menggunakan kontrak baru ini; endpoint identifikasi tetap menerima JPEG tunggal. Semua foto diperiksa sebelum reservasi D1 lalu diteruskan dalam satu panggilan Pl@ntNet `/v2/diseases/identify`. Respons tetap maksimal tiga dugaan `{code, label, score}`, `uncertain`, dan atribusi; tanpa retry otomatis atau penyimpanan foto di Worker.
- Peringatan tambahan muncul jika skor teratas di bawah 0,5 atau selisih dua skor teratas di bawah 0,15. Ini aturan kehati-hatian tampilan, bukan ambang probabilitas diagnosis. Layanan hanya mendukung sebagian tanaman dan masalah; hasil kosong atau foto ditolak tidak berarti tanaman sehat.
- `HealthGuides` mencocokkan kode EPPO persis dengan panduan kutu daun, kutu putih, tungau, embun tepung, antraknosa, bercak daun, serta kode busuk akar/pangkal tertentu. Panduan memakai sumber UC IPM yang dapat dibuka dari layar. Kode yang belum dikurasi dan hasil meragukan memakai panduan pengamatan umum, bukan pengobatan rekaan.
- Pemeriksaan dapat dibuka dari **Kenali** atau detail tanaman. Foto hanya diunggah setelah **Periksa foto** atau **Coba lagi**; penggantian foto membatalkan hasil lama. Foto koleksi tidak diubah, draf pemeriksaan dibersihkan saat kembali, dan panduan umum tetap tersedia tanpa internet.
- **Simpan pemeriksaan** menyimpan dugaan pilihan, foto, tanggal, dan catatan secara atomik di Room. Salinan permanen baru dibersihkan jika transaksi gagal; draf dipertahankan untuk dicoba lagi. Foto yang masih dipakai tanaman atau pemeriksaan lain tidak dihapus. Menghapus tanaman juga membersihkan pemeriksaannya; menghapus pemeriksaan tidak menghapus tanaman. Riwayat dapat dibaca dan statusnya diperbarui tanpa internet; **Pulih menurut pengguna** bukan penilaian model.
- Uji live pada foto kutu daun dari [dokumentasi Pl@ntNet Diseases](https://my.plantnet.org/doc/api/diseases) mengembalikan kode `APHIFA` dan `APHISP` dengan status meragukan. Foto contoh: Ephytia — D.Blancard — INRAE, CC-BY-SA, dipakai hanya untuk QA dan tidak dibundel. Ketepatan penyakit pada kebun keluarga belum divalidasi oleh ahli; fitur ini untuk uji keluarga, bukan diagnosis pasti.
- Verifikasi pembaruan berbasis REA: 31 tes Worker, typecheck, dan bundle lulus; 20 tes unit Android dan 8 tes riwayat/migrasi di HP lulus. APK final dipasang dengan `adb install -r`. Alur galeri tiga foto → Pl@ntNet → dugaan kutu daun → simpan → buka ulang → ubah status → buka ulang → hapus teramati di HP. Status **Membaik**, catatan lokal, dan ketiga foto tersimpan dikonfirmasi lewat SQLite. Sampel QA memakai satu foto dokumentasi dan dua potongannya, bukan validasi akurasi tiga foto lapangan.
- Perbandingan SQLite sebelum/sesudah migrasi dan QA menunjukkan dua tanaman, tiga pengingat, serta kejadian perawatan yang sama persis; foto koleksi tidak berubah. Catatan dan foto QA, termasuk fixture tes yang sempat terinterupsi saat HP terkunci, sudah dibersihkan. Aplikasi final dibuka tanpa galat AndroidRuntime pada prosesnya. Uji ini tidak membuktikan akurasi penyakit secara umum atau kompatibilitas TalkBack/font 200%. Bukti REA kamera PictureThis, ringkasan model statis, dan tangkapan layar Jaga Daun tersimpan di `~/.local/share/jagadaun-rea/picturethis-20261117/`.
- Versi Android `0.2` (`versionCode=2`) memperjelas fitur yang sebelumnya tersembunyi di layar pemeriksaan: beranda Kenali memiliki kartu identifikasi, pemeriksaan penyakit/hama, akses riwayat langsung, dan nomor versi. `assembleDebug` lulus, pemasangan upgrade dan kedua rute baru/utama diverifikasi di HP. Semua baris tanaman, pengingat, kejadian, pemeriksaan, dan foto tersimpan dibandingkan sebelum/sesudah pemasangan dan tetap sama. Layar Kebun Saya dan Pengingat tidak didesain ulang dalam perbaikan keterlihatan ini.


## Penjadwalan dan pengujian

- Jadwalkan pekerjaan unik per pengingat. Saat jadwal diubah, ganti pekerjaan lama. Saat dijeda atau dihapus, batalkan pekerjaan. Validasi jadwal tersimpan sebelum menampilkan notifikasi. Jadwal dapat bergeser karena pembatasan baterai Android.
- Uji unit untuk pemetaan kandidat, status kesalahan, dan perhitungan jadwal. Uji Room serta perpindahan dari pratinjau ke penyimpanan lokal. Uji Worker memakai respons penyedia tiruan agar pengujian otomatis tidak menghabiskan kuota.
- Jalankan `./gradlew testDebugUnitTest lintDebug assembleDebug`. Gunakan `adb devices` lalu `./gradlew installDebug` pada HP berstatus `device`. Periksa kamera, izin notifikasi, offline, dan pemasangan versi baru di atas versi sebelumnya.
- Panduan penggunaan MCP Mobbin dan peninjauan UI ada di [DESIGN.md](DESIGN.md). Cakupan fitur ada di [PRD.md](PRD.md).

Referensi · [Room](https://developer.android.com/training/data-storage/room) · [Android Photo Picker](https://developer.android.com/training/data-storage/shared/photo-picker) · [Pl@ntNet API](https://my.plantnet.org/) · [Pl@ntNet Diseases](https://my.plantnet.org/doc/api/diseases) · [Cloudflare Workers Secrets](https://developers.cloudflare.com/workers/configuration/secrets/)
