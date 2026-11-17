# Jaga Daun — Desain Antarmuka Android

## Identitas

Nama tampilan aplikasi **Jaga Daun**. Gunakan simbol daun miring dengan satu ruang negatif sebagai tulang daun, menyatu dengan bingkai bidik terbuka. Ikon adaptif memakai simbol krem sebagai lapisan depan dan hijau tua sebagai latar. Uji potongan lingkaran dan persegi membulat, serta keterbacaan pada ukuran 24 dan 48 px. Pakai wordmark Jaga Daun jika karya referensi masih memuat nama eksplorasi sebelumnya.

| Peran | HEX | Pemakaian |
| --- | --- | --- |
| Primer | `#24543D` | Tombol utama, ikon, dan tab aktif |
| Latar | `#F8F7F2` | Latar aplikasi dan simbol ikon terang |
| Permukaan | `#FFFFFF` | Kartu, formulir, dan dialog |
| Aksen lembut | `#E5EFE5` | Pilihan aktif dan kelompok informasi |
| Teks utama | `#1C2B22` | Judul, nama tanaman, dan isi panduan |
| Teks sekunder | `#526258` | Nama ilmiah dan metadata |
| Perlu perhatian | `#8A5700` di atas `#FFF0CC` | Peringatan hasil meragukan dan foto kurang jelas |

Gunakan **DM Sans** dengan cadangan font sistem. Judul 24 sp semibold, nama kandidat 20 sp semibold, label dan isi 16 sp, metadata 14 sp. Gunakan margin layar 16 dp, jarak antarkelompok 16–24 dp, dan target sentuh minimal 48 × 48 dp. Foto tanaman menjadi visual utama. Gunakan satu tindakan paling menonjol per layar dan label teks bersama ikon.

## Gunakan MCP Mobbin sebelum membuat layout

1. Di OMP, temukan **MCP Mobbin** yang sudah dikonfigurasi. Cari 2–3 referensi Android per alur jika tersedia. Mulai dari pencarian `plant identification camera capture`, `photo scan results candidate list`, `plant collection detail`, dan `care reminder schedule`. Perluas ke aplikasi pemindai atau pengingat jika contoh tanaman terbatas.
2. Pilih contoh berdasarkan keterbacaan label, posisi tombol utama, penanganan hasil kosong, dan kemudahan kembali. Catat nama aplikasi, tautan layar atau alur, serta keputusan tata letak yang diambil.
3. Gunakan pola yang relevan dengan palet, ikon, dan komponen Jaga Daun. Buat desain orisinal dan gunakan foto contoh yang berizin atau foto sendiri.
4. Jika MCP Mobbin belum tersedia, catat bahwa riset Mobbin belum dilakukan dan lanjutkan prototipe dengan spesifikasi layar di bawah. Jangan mencantumkan hasil pencarian yang belum dilakukan.

| Alur yang dicari di Mobbin | Keputusan yang dicatat |
| --- | --- |
| Kamera dan pratinjau | Penempatan **Ambil Foto**, **Pilih dari Galeri**, dan **Ganti Foto**. |
| Hasil pengenalan | Hierarki kandidat, nama ilmiah, penanganan hasil meragukan, dan pilihan simpan. |
| Koleksi dan detail tanaman | Rasio foto, format kartu, urutan panduan perawatan, dan akses edit. |
| Pengingat | Pengelompokan jadwal, kondisi kosong, dan tindakan **Tandai selesai**. |

### Referensi foto yang diterapkan

Riset MCP Mobbin: [Greg — pilihan kamera atau pustaka foto](https://mobbin.com/screens/e96a79f5-12c9-48be-992c-439c53d58d73). Layar memperlihatkan dua pilihan sumber dan pembatalan di atas kamera. Jaga Daun mempertahankan pilihan **Ambil Foto** dan **Pilih dari Galeri** dengan label jelas, menggunakan kamera sistem alih-alih meniru kamera khusus Greg. **Ganti Foto** tidak menghapus foto lama jika pemilihan dibatalkan.

Hasil nyata mengikuti hierarki daftar vertikal pada [Greg — daftar nama tanaman](https://mobbin.com/screens/4984fd98-009a-4793-93fc-163e9601bade): nama umum di atas nama ilmiah. Jaga Daun menambahkan radio pilihan eksplisit, **Skor penyedia**, dan **Gunakan kandidat** yang baru aktif setelah pengguna memilih. Tidak ada kandidat yang dipilih otomatis atau hasil simulasi. Form berikutnya tetap dapat diedit sebelum disimpan.

### Nama dan pemeriksaan masalah

Riset Mobbin menemukan [Greg — hasil foto dengan nama umum di atas nama ilmiah](https://mobbin.com/screens/6c76c20e-54e7-4bf4-8e2c-1f3f103d1b77) dan [Greg — alur menambahkan tanaman](https://mobbin.com/flows/b7415099-ab4a-44d4-a624-543be2c7ff27?tab=screens). Foto dominan, dua pilihan sumber foto, dan hierarki nama diterapkan dengan komponen Jaga Daun. Hasil pencarian tidak memberikan layar diagnosis PictureThis; tampilan pemeriksaan merupakan rancangan Jaga Daun, bukan salinan layar atau kode aplikasi tersebut.

Nama umum Indonesia terkurasi didahulukan pada hasil pengenalan dan form simpan; nama ilmiah tetap tersedia. Layar pemeriksaan memakai **Kemungkinan penyebab**, **Cocokkan gejalanya**, **Langkah awal yang aman**, dan **Pencegahan**, dengan sumber yang bisa dibuka. Hasil meragukan tidak diberi pengobatan spesifik. Semua skor dijelaskan sebagai ranking layanan, bukan persentase kepastian.
Inspeksi REA pada APK PictureThis terpasang (`cn.danatech.xingseus`) kini berhasil. Kamera diagnosis yang diamati meminta foto seluruh tanaman, menyediakan tiga slot foto dan akses galeri, serta menonaktifkan **Done** saat kosong (`ev_1be848b39d0f38ba0133d3cdf1e25b2d80711f3b6d917430d60179f1a2117b9d`). Analisis statis menemukan survei cahaya → siram → lokasi (`ev_36238a58e5bd5ec64e7a042b535eadb7e296c315bd11252ea79cb54a0e32f64b`), model riwayat terkait tanaman (`ev_7ab4a6a3c1cc2f451d3debf34dc0b2770f2460562b49341c6a77aa0042eb1beb`), dan item panduan terstruktur (`ev_677f6d835bc2909b667a6bcbe7d99daca9b6ff93913472211418b173edb44eb4`). Temuan statis tidak membuktikan perilaku server atau keberhasilan membuka riwayat PictureThis. Bukti disimpan di `~/.local/share/jagadaun-rea/picturethis-20261117/`.

Penerapan Jaga Daun: panduan sampai tiga foto, hasil dugaan dan pencegahan bersumber, serta riwayat lokal yang bisa dibuka kembali. Satu foto tetap boleh diperiksa; foto gejala dekat dan sisi lain daun merupakan pilihan desain Jaga Daun. Catatan cahaya, siram, dan perkembangan disimpan lokal, tidak diklaim sebagai masukan model Pl@ntNet. Status **Dalam pengamatan**, **Membaik**, **Memburuk**, dan **Pulih menurut pengguna** adalah pengamatan pengguna, bukan fitur PictureThis yang telah dibuktikan atau verifikasi penyakit. Tidak ada kode, aset bermerek, atau backend PictureThis yang dipakai.


## Navigasi dan layar

Gunakan tab bawah **Kenali**, **Kebun Saya**, dan **Pengingat**. Tab bawah tampil pada ketiga layar utama. Pratinjau foto, hasil, detail tanaman, dan formulir memakai judul serta tombol kembali.

| Layar | Tata letak dan tindakan |
| --- | --- |
| Kenali | Beranda versi 0.2 menampilkan nama **Jaga Daun** dan nomor versi aktual dari BuildConfig. Kartu **Tanaman apa ini?** mengelompokkan kamera/galeri; kartu **Penyakit atau hama?** langsung membuka pemeriksaan 1–3 foto; kartu **Riwayat pemeriksaan** langsung membuka hasil tersimpan tanpa melewati layar pemeriksaan. Gunakan palet hijau/krem, label yang membungkus, serta tinggi kontrol minimum 48 dp. |
| Pratinjau | Foto dominan. Tampilkan **Ganti Foto** dan **Kenali Tanaman**. Beri keterangan singkat bahwa foto dikirim untuk dikenali. Tampilkan status **Sedang mengenali…** saat diproses. |
| Hasil | Tampilkan maksimal tiga kandidat, nama umum bila tersedia dan nama ilmiah. Pemilihan mengaktifkan **Gunakan kandidat**, lalu form penyimpanan. Jelaskan bahwa skor bukan probabilitas kebenaran, beri peringatan bila hasil meragukan, dan tautan atribusi Pl@ntNet. |
| Kebun Saya | Daftar kartu dengan foto, nama, dan pengingat terdekat jika ada. Aksi **Tambah manual** tetap tersedia saat offline. Detail memuat foto, catatan, dan panduan yang tersedia. |
| Pengingat | Kelompokkan **Hari ini** dan **Berikutnya**. Tampilkan nama tanaman, kegiatan, dan jadwal. Sediakan **Tandai selesai**, **Ubah**, dan **Jeda**. |
| Periksa Masalah | Dibuka melalui **Periksa penyakit & hama** di Kenali atau detail tanaman. Tiga slot berlabel **Seluruh tanaman**, **Gejala dari dekat**, dan **Sisi lain daun**, dengan pratinjau dan kamera/galeri untuk slot terpilih. Satu sampai tiga foto dikirim hanya melalui **Periksa foto**. Hasil menampilkan dugaan, ketidakpastian, langkah awal, pencegahan, dan sumber. **Simpan pemeriksaan** menyimpan dugaan terpilih, foto, dan catatan di HP tanpa mengubah foto koleksi. |
| Riwayat pemeriksaan | Daftar hasil bertanggal; dari detail tanaman hanya menampilkan pemeriksaannya, dari Kenali menampilkan semua. Buka hasil dan foto tanpa internet, catat perkembangan menurut pengguna, atau hapus pemeriksaan dengan konfirmasi. Penghapusan pemeriksaan tidak menghapus tanaman. |

## Teks dan keadaan penting

| Keadaan | Teks dan tindakan |
| --- | --- |
| Foto belum jelas | **Tanaman belum bisa dikenali.** Aksi **Ganti Foto** atau **Tambah manual**. |
| Tidak ada internet | **Pengenalan foto perlu internet.** Aksi **Coba lagi** setelah tersambung. |
| Kuota habis | **Batas pengenalan hari ini tercapai.** Sediakan akses **Kebun Saya** dan **Tambah manual**. |
| Koleksi kosong | **Belum ada tanaman tersimpan.** Aksi **Kenali Tanaman** dan **Tambah manual**. |
| Izin notifikasi belum aktif | **Aktifkan notifikasi untuk menerima pengingat.** Jadwal tetap terlihat. |
| Dugaan meragukan | **Belum bisa dipastikan.** Minta foto yang lebih jelas atau foto bagian lain. |

Uji kontras teks minimal 4,5:1, TalkBack, target sentuh, dan pembesaran font sistem hingga 200%. Izinkan teks membungkus. Beri indikator hasil dengan kata dan ikon. Ikuti [PRD.md](PRD.md) untuk kriteria penerimaan dan [ARCHITECURE.md](ARCHITECURE.md) untuk batas teknis.
