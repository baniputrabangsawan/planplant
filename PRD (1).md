# Jaga Daun — Product Requirements Document

**Platform** Android · **Bahasa** Indonesia · **Pengguna awal** Keluarga, terutama usia 40 tahun ke atas · **Referensi produk** PictureThis

## Tujuan

Jaga Daun membantu pengguna mengenali tanaman dari foto, memeriksa dugaan masalah, menyimpan koleksi pribadi, dan mengingat jadwal perawatan. Navigasi bawah menyediakan **Beranda**, **Periksa**, **Foto**, **Kebun**, dan **Lainnya**. **Foto** membuka kamera/galeri; pengingat tersedia dari Beranda dan Lainnya.

## Rilis keluarga

| ID | Fitur | Kriteria penerimaan |
| --- | --- | --- |
| JD-01 | Ambil atau pilih foto | Kamera dalam aplikasi menyediakan bingkai fokus, petunjuk cahaya, dan mode satu foto atau **360°** dengan tiga sudut tanaman yang sama. Galeri tetap tersedia. Pratinjau bisa diganti; semua foto dikirim hanya setelah **Analisis tanaman**. |
| JD-02 | Identifikasi spesies | Tampilkan satu hasil berperingkat teratas dari Pl@ntNet, nama Indonesia dan nama ilmiah. Tombol tambah koleksi mengonfirmasi hasil sebelum form simpan. Jika hasil kurang kuat, minta **Ambil ulang** dengan petunjuk singkat. Putus koneksi atau kuota habis mempertahankan semua foto. |
| JD-03 | Kebun Saya | Simpan tanaman terpilih dengan foto, nama, dan catatan. Pengguna juga bisa menambah tanaman secara manual, mengedit, dan menghapus. Data tetap tersedia setelah aplikasi ditutup dan dibuka saat offline. |
| JD-04 | Pengingat perawatan | Buat, ubah, jeda, dan hapus pengingat siram atau kegiatan lain. Tampilkan jadwal berikutnya, izin notifikasi, dan aksi **Tandai selesai**. Jika izin ditolak, jadwal masih terlihat di aplikasi. |
| JD-05 | Panduan dasar | Tampilkan kebutuhan cahaya, siram, dan media tanam jika panduan terkurasi sudah tersedia. Sertakan sumber. Pengguna tetap bisa menyimpan catatan dan mengatur jadwal sendiri. |
| JD-06 | Periksa penyakit dan hama, uji keluarga | **Analisis tanaman** mengenali jenis dan kondisi pada satu halaman, memakai semua 1–3 foto. Kegagalan satu bagian tidak menghapus bagian yang berhasil. Tampilkan satu hasil teratas dan dua langkah awal; pencegahan dan sumber dapat dibuka. Hasil kurang kuat meminta foto ulang, bukan menyatakan sehat. Pemeriksaan terarah tetap tersedia. Riwayat, semua foto, catatan, dan perkembangan disimpan lokal setelah konfirmasi. |

JD-01–05 membentuk APK keluarga awal. JD-06 kini tersedia untuk uji keluarga melalui penyedia dan kuota Pl@ntNet yang sama; ketepatannya pada tanaman keluarga masih perlu diuji dengan ahli. Hasil bukan diagnosis pasti dan tidak memastikan tanaman sehat. Identifikasi hewan umum, burung, akun pengguna, dan sinkronisasi berada di luar rilis awal; hama tanaman hanya termasuk sebagai dugaan masalah tanaman. Aplikasi referensi dipakai sebagai acuan fungsi. Jaga Daun memiliki nama, ikon, dan antarmuka sendiri.

Cakupan paritas adalah fungsi inti berkebun. Mode **360°** berarti tiga foto terpandu dari sudut berbeda, bukan pemindaian 3D atau pengukuran orbit. Tidak menyertakan burung, identifikasi serangga umum, racun/alergen, lingkar pohon, buku, konsultasi ahli, atau alat tanpa dukungan layanan/data. Nama dan aset tetap Jaga Daun; tidak menjanjikan kesamaan akurasi model PictureThis.

## Privasi dan biaya

- Foto tetap lokal saat diambil, dipilih, atau dipakai untuk menambah tanaman manual. Beri tahu pengguna sebelum foto dikirim ke layanan identifikasi atau pemeriksaan.
- Simpan foto koleksi di perangkat. Pertahankan fitur lokal saat layanan identifikasi tidak tersedia.
- Gunakan paket gratis Pl@ntNet dan Cloudflare Workers selama memenuhi syarat, kuota, dan atribusi berlaku. Periksa tarif sebelum rilis. Untuk pemeriksaan berbayar, tetapkan batas penggunaan sebelum mengaktifkan fitur bagi keluarga.
- Analisis terpadu memakai satu unggahan dan satu percobaan kuota keluarga, tetapi menjalankan dua panggilan Pl@ntNet. Beri tahu sebelum pengiriman; analisis ulang hanya setelah ketukan eksplisit dan memakai kuota baru.
- Verifikasi status cakupan publik, privasi, dan pengungkapan pemrosesan foto sebelum menyiapkan rilis Google Play.

## Uji sebelum rilis

1. Jalankan alur **Foto → Analisis tanaman → Hasil identitas dan kondisi → Pilih kandidat → Simpan → Atur pengingat** pada HP fisik. Periksa juga simpan/buka ulang riwayat dugaan dan notifikasi perawatan. Minta Ayah dan Ibu mencoba tanpa arahan dan catat langkah yang membuat mereka berhenti.
2. Siapkan foto tanaman keluarga yang spesiesnya sudah diketahui, dengan variasi jarak, sudut, dan pencahayaan. Catat hasil benar pada kandidat pertama dan tiga kandidat teratas, hasil meragukan, serta kegagalan.
3. Untuk JD-06, uji kasus yang sudah diperiksa oleh orang yang kompeten dan masukkan contoh tanaman sehat. Catat dugaan benar, dugaan keliru, dan kasus yang perlu foto tambahan. Skor kandidat per foto diperlakukan sebagai keluaran model.
4. Uji mode pesawat, izin ditolak, foto hilang, kuota habis, pengingat diedit, dan ukuran font sistem besar.

Gunakan **MCP Mobbin** saat tahap desain untuk mencari pola antarmuka yang mudah digunakan. Prosedur dan keluaran riset ada di [DESIGN.md](DESIGN.md). Implementasi dan build mengikuti [ARCHITECURE.md](ARCHITECURE.md).
