# Jaga Daun — Product Requirements Document

**Platform** Android · **Bahasa** Indonesia · **Pengguna awal** Keluarga, terutama usia 40 tahun ke atas · **Referensi produk** PictureThis

## Tujuan

Jaga Daun membantu pengguna mengenali tanaman dari foto, menyimpan koleksi pribadi, dan mengingat jadwal perawatan. Pemeriksaan masalah tanaman ditambahkan setelah hasilnya diuji pada tanaman yang biasa dipelihara pengguna. Gunakan tab bawah **Kenali**, **Kebun Saya**, dan **Pengingat**.

## Rilis keluarga

| ID | Fitur | Kriteria penerimaan |
| --- | --- | --- |
| JD-01 | Ambil atau pilih foto | Pengguna bisa memakai kamera sistem atau memilih satu foto. Pratinjau muncul dengan opsi ganti foto. Foto dikirim setelah pengguna menekan **Kenali Tanaman**. |
| JD-02 | Identifikasi spesies | Tampilkan hingga tiga kandidat dari Pl@ntNet, nama ilmiah, dan nama umum jika tersedia. Pengguna memilih kandidat sebelum menyimpan. Untuk foto yang tidak memberi hasil memadai, tampilkan **Belum bisa dikenali** dan tindakan **Ganti foto**. Putus koneksi atau kuota habis mempertahankan pratinjau. |
| JD-03 | Kebun Saya | Simpan tanaman terpilih dengan foto, nama, dan catatan. Pengguna juga bisa menambah tanaman secara manual, mengedit, dan menghapus. Data tetap tersedia setelah aplikasi ditutup dan dibuka saat offline. |
| JD-04 | Pengingat perawatan | Buat, ubah, jeda, dan hapus pengingat siram atau kegiatan lain. Tampilkan jadwal berikutnya, izin notifikasi, dan aksi **Tandai selesai**. Jika izin ditolak, jadwal masih terlihat di aplikasi. |
| JD-05 | Panduan dasar | Tampilkan kebutuhan cahaya, siram, dan media tanam jika panduan terkurasi sudah tersedia. Sertakan sumber. Pengguna tetap bisa menyimpan catatan dan mengatur jadwal sendiri. |
| JD-06 | Periksa penyakit dan hama, uji keluarga | Pengguna membuka pemeriksaan secara terpisah, mengambil atau memilih satu foto bagian bergejala, lalu mengirim dengan **Periksa foto**. Tampilkan maksimal tiga dugaan Pl@ntNet, nama masalah Indonesia bila dikurasi, saran pengamatan, langkah awal, pencegahan, dan sumber. Hasil meragukan memakai **Belum bisa dipastikan** dan panduan umum. Mengganti foto tidak mengubah foto koleksi. |

JD-01–05 membentuk APK keluarga awal. JD-06 kini tersedia untuk uji keluarga melalui penyedia dan kuota Pl@ntNet yang sama; ketepatannya pada tanaman keluarga masih perlu diuji dengan ahli. Hasil bukan diagnosis pasti dan tidak memastikan tanaman sehat. Identifikasi hewan umum, burung, akun pengguna, dan sinkronisasi berada di luar rilis awal; hama tanaman hanya termasuk sebagai dugaan masalah tanaman. Aplikasi referensi dipakai sebagai acuan fungsi. Jaga Daun memiliki nama, ikon, dan antarmuka sendiri.

## Privasi dan biaya

- Foto tetap lokal saat diambil, dipilih, atau dipakai untuk menambah tanaman manual. Beri tahu pengguna sebelum foto dikirim ke layanan identifikasi atau pemeriksaan.
- Simpan foto koleksi di perangkat. Pertahankan fitur lokal saat layanan identifikasi tidak tersedia.
- Gunakan paket gratis Pl@ntNet dan Cloudflare Workers selama memenuhi syarat, kuota, dan atribusi berlaku. Periksa tarif sebelum rilis. Untuk pemeriksaan berbayar, tetapkan batas penggunaan sebelum mengaktifkan fitur bagi keluarga.
- Verifikasi status cakupan publik, privasi, dan pengungkapan pemrosesan foto sebelum menyiapkan rilis Google Play.

## Uji sebelum rilis

1. Jalankan alur **Foto → Kenali → Pilih kandidat → Simpan → Atur pengingat** pada HP fisik. Minta Ayah dan Ibu mencoba tanpa arahan dan catat langkah yang membuat mereka berhenti.
2. Siapkan foto tanaman keluarga yang spesiesnya sudah diketahui, dengan variasi jarak, sudut, dan pencahayaan. Catat hasil benar pada kandidat pertama dan tiga kandidat teratas, hasil meragukan, serta kegagalan.
3. Untuk JD-06, uji kasus yang sudah diperiksa oleh orang yang kompeten dan masukkan contoh tanaman sehat. Catat dugaan benar, dugaan keliru, dan kasus yang perlu foto tambahan. Skor kandidat per foto diperlakukan sebagai keluaran model.
4. Uji mode pesawat, izin ditolak, foto hilang, kuota habis, pengingat diedit, dan ukuran font sistem besar.

Gunakan **MCP Mobbin** saat tahap desain untuk mencari pola antarmuka yang mudah digunakan. Prosedur dan keluaran riset ada di [DESIGN.md](DESIGN.md). Implementasi dan build mengikuti [ARCHITECURE.md](ARCHITECURE.md).
