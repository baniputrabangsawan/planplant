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

Riset MCP Mobbin: [Greg — pilihan kamera atau pustaka foto](https://mobbin.com/screens/e96a79f5-12c9-48be-992c-439c53d58d73). Dua pilihan sumber tetap berlabel jelas: **Ambil Foto** membuka kamera terpandu dalam aplikasi dan **Pilih dari Galeri** membuka Photo Picker. **Ganti Foto** tidak menghapus foto lama jika pemilihan dibatalkan.

Hierarki nama umum di atas nama ilmiah mengikuti [Greg — daftar nama tanaman](https://mobbin.com/screens/4984fd98-009a-4793-93fc-163e9601bade). Versi 0.4 menampilkan hanya hasil teratas tanpa radio atau skor. Pengguna tetap mengonfirmasi melalui **Tambahkan ke koleksi** dan formulir yang dapat diedit.

### Nama dan pemeriksaan masalah

Riset Mobbin menemukan [Greg — hasil foto dengan nama umum di atas nama ilmiah](https://mobbin.com/screens/6c76c20e-54e7-4bf4-8e2c-1f3f103d1b77) dan [Greg — alur menambahkan tanaman](https://mobbin.com/flows/b7415099-ab4a-44d4-a624-543be2c7ff27?tab=screens). Foto dominan, dua pilihan sumber foto, dan hierarki nama diterapkan dengan komponen Jaga Daun. Hasil pencarian tidak memberikan layar diagnosis PictureThis; tampilan pemeriksaan merupakan rancangan Jaga Daun, bukan salinan layar atau kode aplikasi tersebut.

Nama Indonesia terkurasi didahulukan; nama ilmiah tetap tersedia. Hasil menampilkan satu identitas dan satu kondisi teratas, lalu dua langkah **Lakukan sekarang**. Pencegahan, sumber, dan langkah tambahan dibuka sesuai kebutuhan. Hasil kurang kuat meminta **Ambil ulang** dengan arahan singkat dan memakai langkah umum, bukan pengobatan rekaan.

Versi 0.5 menambahkan kartu **Tentang Pepaya/Mangga** pada hasil pengenalan dan detail tanaman koleksi. Ringkasan langsung terlihat, sedangkan **Jenis & ciri buah** membuka ciri pembeda dan sumber bacaan. Daftar itu menjelaskan varietas dari spesies yang dikenali, bukan kandidat hasil foto atau pilihan yang mengubah nama tanaman. Gunakan buah matang dan label bibit untuk mencocokkan varietas; nama panggilan tidak menentukan kartu bacaan.
Inspeksi REA pada APK PictureThis terpasang (`cn.danatech.xingseus`) kini berhasil. Kamera diagnosis yang diamati meminta foto seluruh tanaman, menyediakan tiga slot foto dan akses galeri, serta menonaktifkan **Done** saat kosong (`ev_1be848b39d0f38ba0133d3cdf1e25b2d80711f3b6d917430d60179f1a2117b9d`). Analisis statis menemukan survei cahaya → siram → lokasi (`ev_36238a58e5bd5ec64e7a042b535eadb7e296c315bd11252ea79cb54a0e32f64b`), model riwayat terkait tanaman (`ev_7ab4a6a3c1cc2f451d3debf34dc0b2770f2460562b49341c6a77aa0042eb1beb`), dan item panduan terstruktur (`ev_677f6d835bc2909b667a6bcbe7d99daca9b6ff93913472211418b173edb44eb4`). Temuan statis tidak membuktikan perilaku server atau keberhasilan membuka riwayat PictureThis. Bukti disimpan di `~/.local/share/jagadaun-rea/picturethis-20261117/`.

Penerapan Jaga Daun: panduan sampai tiga foto, hasil dugaan dan pencegahan bersumber, serta riwayat lokal yang bisa dibuka kembali. Satu foto tetap boleh diperiksa; foto gejala dekat dan sisi lain daun merupakan pilihan desain Jaga Daun. Catatan cahaya, siram, dan perkembangan disimpan lokal, tidak diklaim sebagai masukan model Pl@ntNet. Status **Dalam pengamatan**, **Membaik**, **Memburuk**, dan **Pulih menurut pengguna** adalah pengamatan pengguna, bukan fitur PictureThis yang telah dibuktikan atau verifikasi penyakit. Tidak ada kode, aset bermerek, atau backend PictureThis yang dipakai.

Inspeksi menu tambahan memakai REA menunjukkan **Home**, **Diagnose**, kamera pusat, **My Plants**, dan **More**. Home menampilkan tindakan identifikasi, diagnosis, kebun, dan perawatan hari ini; More memuat alat yang tidak seluruhnya disediakan Pl@ntNet. Bukti: `ev_50670270f3f8cacb2c323e6bf5fc359988a5ee32e4d0727413bd0b2e809a4f69` (koleksi), `ev_dc34c47a6634580fc017ccafe6045915879933caef9e7191116fef99abaf51fa` (diagnosis), dan `ev_1c60cd96ee47db0859578250b345ad90248a0b65f58c981a1c024c97110bb311` (menu tambahan). Pengguna menyetujui cakupan inti berkebun: fungsi tanpa penyedia/data tidak ditiru sebagai tombol kosong. Bukti menu disimpan privat di `~/.local/share/jagadaun-rea/picturethis-parity-20261117/menu-evidence.json`; menu yang terlihat tidak membuktikan kesamaan model atau layanan server.


REA mengamati kamera identifikasi PictureThis dengan bingkai fokus (`ev_7e356d0d2d784304f9f26011a8c0a530e23769d09460f1de2a41477973f351d5`) dan mode **360 Identify** dengan tiga slot: seluruh tanaman dari depan, dekat dari satu sisi, lalu sisi lain (`ev_8979bdf2d4ad5670f37628c74c8947026d4f70f76f303a786419deb818da9a2f`, `ev_4470c105dc0910647841cfdbe84612a9144d987269332b5a860cc0b747d3d26d`, `ev_141cb1cbb6cbe7b253923f359a1c702583aadf23afa1d31ec134a5cd7d9512d1`). Done aktif setelah satu foto; tiga foto wajib dalam mode 360° Jaga Daun merupakan pilihan desain sendiri. Tidak ada unggahan referensi atau pembelian. Bukti privat: `~/.local/share/jagadaun-rea/camera-20261118/`.

## Navigasi dan layar

Navigasi bawah menyediakan **Beranda**, **Periksa**, **Foto**, **Kebun**, dan **Lainnya**. **Foto** adalah tindakan pemilihan kamera/galeri, bukan layar kosong. Pengingat dibuka dari Beranda atau Lainnya. Pratinjau, hasil, detail tanaman, pengingat, riwayat, dan formulir memiliki tombol kembali.

| Layar | Tata letak dan tindakan |
| --- | --- |
| Beranda | Nama **Jaga Daun**, versi aktual BuildConfig, kamera/galeri untuk analisis terpadu, pemeriksaan terarah, ringkasan perawatan hari ini dari jadwal nyata, serta akses koleksi dan riwayat. Tidak menampilkan cuaca atau hasil simulasi. |
| Pratinjau | Foto dominan; **Analisis tanaman**, **Ganti foto**, dan tambah manual. Jelaskan satu percobaan keluarga menjalankan dua analisis Pl@ntNet. Pengiriman tidak dimulai otomatis saat membuka atau memutar layar. |
| Hasil tanaman | Satu hasil jenis dan satu kondisi teratas, dua langkah tindakan, serta pencegahan/sumber yang dapat dibuka. Tidak menampilkan daftar kandidat atau skor. Hasil kurang kuat meminta foto ulang secara singkat. Kegagalan parsial tidak menghapus bagian yang berhasil. Simpan riwayat mempertahankan semua foto; identitas dapat disimpan ke koleksi memakai foto pertama. |
| Kebun Saya | Pencarian nama umum/ilmiah pada koleksi lokal, kartu foto dan pengingat terdekat, keadaan kosong dan tidak ada hasil yang berbeda. **Tambah manual** tetap tersedia. Detail memuat catatan, panduan yang tersedia, dan pemeriksaan terarah. |
| Pengingat | Kelompokkan **Hari ini** dan **Berikutnya**. Tampilkan nama tanaman, kegiatan, dan jadwal. Sediakan **Tandai selesai**, **Ubah**, dan **Jeda**. |
| Periksa | Petunjuk pengambilan 1–3 foto, tombol pemeriksaan baru, daftar tanaman koleksi yang dapat diperiksa, dan riwayat. Pemeriksaan terarah mempertahankan slot **Seluruh tanaman**, **Gejala dari dekat**, dan **Sisi lain daun**. |
| Riwayat pemeriksaan | Daftar hasil bertanggal; dari detail tanaman hanya pemeriksaannya, dari menu utama semua. Baca hasil dan foto tanpa internet, catat perkembangan menurut pengguna, atau hapus dengan konfirmasi. Penghapusan pemeriksaan tidak menghapus tanaman atau foto yang masih dipakai tanaman. |
| Lainnya | Pengingat, riwayat, pemeriksaan terarah, serta panduan umum lokal yang dapat dibuka/tutup dan memiliki tautan sumber. Tidak menampilkan alat tanpa implementasi. |

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
