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

## Redesign disetujui — 21 November 2026

Pilihan pengguna sebelum implementasi/build: **struktur Greg + deretan kartu Lovi**. Awalnya dikerjakan dengan tiga tab ala Play, lalu **pengguna meminta navigasi seperti PictureThis**: lima tujuan dengan tombol kamera menonjol di tengah, warna hijau sendiri, langsung membuka kamera, dan galeri baru ditawarkan di dalam kamera. Kemiripan ada pada hierarki, pengelompokan, foto, kartu, dan posisi kamera; bukan menyalin merek/aset, tema gelap, atau ukuran teks kecil.

| Referensi Mobbin | Keputusan yang diterapkan |
| --- | --- |
| [Greg — ambil/unggah foto](https://mobbin.com/screens/817811ad-04cc-411f-8734-dff40d15fc7d) | Kenali Tanaman paling menonjol, Pilih dari Galeri langsung terlihat. Referensi ini onboarding; beranda gabungan adalah rancangan Jaga Daun. |
| [Greg — koleksi grid](https://mobbin.com/screens/fc2790f8-695a-4cad-9b3a-a630f53062cc) | Grid foto ditambah nama dan jadwal nyata di bawah foto; tidak menyalin profil sosial. |
| [Greg — detail foto](https://mobbin.com/screens/5995dd7d-2433-4099-b1b5-90da558c263b) dan [panduan](https://mobbin.com/screens/93bbddc2-4da8-4edd-b3ba-95ec25473202) | Foto besar, nama umum/ilmiah, bagian jadwal, panduan bersumber, dan catatan yang mudah dipindai. |
| [Greg — jadwal](https://mobbin.com/screens/c802b78e-a223-4ea6-b64a-b67b397e04ba) | Thumbnail + nama + waktu, kelompok Hari Ini/Berikutnya/Dijeda; tidak menyalin takaran air referensi. |
| [Lovi — kartu horizontal](https://mobbin.com/screens/7e9161bc-36b9-41f2-a6d4-0e99cef2936a) | Foto dan kartu tanaman dalam deret dengan sebagian kartu berikutnya terlihat, tanpa carousel otomatis. Teks penting di luar foto. |
| [Play — navigasi tiga tujuan](https://mobbin.com/screens/5a796023-ef12-4dc2-bf87-59a9c3c6daf9) | Permukaan putih membulat, kapsul hijau pucat aktif, ikon dan label tetap terlihat, dengan inset navigasi sistem. |

Palet tetap #24543D / #F8F7F2 / #E5EFE5 / putih. DM Sans, isi 16 sp, metadata 14 sp, jarak 8/12/16/24 dp, target minimal 48 dp. Tinggi teks tidak dikunci; aksi pengingat membungkus. Transisi fade 120/90 ms dan perubahan pilihan 100 ms mengikuti skala animasi sistem. Tab aktif tidak memuat ulang atau menggulir ke atas; kembali memulihkan posisi dan pencarian.

## Navigasi dan layar

Navigasi bawah menyediakan lima tujuan: **Beranda**, **Periksa**, **kamera**, **Kebun**, **Lainnya**. Ikon kamera bulat berwarna hijau tua menonjol ±26 dp di atas permukaan bar dan membuka kamera langsung; **Pilih dari Galeri** ada di dalam layar kamera. Halaman sekunder, kamera, detail, dan formulir punya tombol kembali tanpa bottom bar.

| Layar | Tata letak dan tindakan |
| --- | --- |
| Kenali | Kenali Tanaman dominan, Pilih dari Galeri terlihat, Periksa Masalah, kartu horizontal tanaman terbaru + Lihat Semua, dua pengingat terdekat, sekitar, riwayat, dan panduan. Semua berasal dari data nyata. |
| Pratinjau | Deret foto horizontal; Analisis tanaman, tambah sudut, dan tambah manual. Pengiriman hanya setelah tindakan eksplisit. Jelaskan satu percobaan keluarga menjalankan dua analisis Pl@ntNet. |
| Hasil tanaman | Kandidat dengan skor minimal 0,30 dapat dipilih dalam deret horizontal, tanpa persentase kepastian. Pilihan awal hasil teratas; status Dipilih terlihat. Pilihan tidak membuang hasil kondisi, catatan, atau foto. |
| Kebun Saya | Pencarian nama umum/ilmiah, deret terbaru maksimal empat tanaman, Lihat Semua ke grid adaptif, nama dan perawatan di bawah foto. Empty/no-match memiliki tindakan lanjutan; Tambah manual tetap ada. |
| Detail Tanaman | Foto utama; galeri horizontal hanya jika beberapa foto tersimpan benar-benar ada (koleksi dan pemeriksaan terkait). Nama, ilmiah, jadwal nyata, panduan bersumber, catatan, ubah, dan hapus. |
| Pengingat | Hari Ini termasuk terlambat, Berikutnya, Dijeda. Foto kecil, nama, kegiatan, tanggal/jam. Tandai Selesai dominan, Ubah/Jeda jelas, Hapus terpisah. Umpan balik sukses hanya setelah transaksi tersimpan. |
| Periksa | Pemeriksaan terarah 1–5 foto, daftar tanaman yang dapat diperiksa, serta riwayat; hasil bukan diagnosis pasti. |
| Riwayat pemeriksaan | Daftar hasil bertanggal; dari detail tanaman hanya pemeriksaannya, dari menu utama semua. Baca hasil dan foto tanpa internet, catat perkembangan menurut pengguna, atau hapus dengan konfirmasi. Penghapusan pemeriksaan tidak menghapus tanaman atau foto yang masih dipakai tanaman. |
| Panduan & alat | Pengingat, riwayat, pemeriksaan terarah, sekitar, dan panduan lokal bersumber. Tidak menampilkan alat tanpa implementasi. |

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
