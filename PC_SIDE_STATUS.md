# Status sisi PC dan perubahan APK (untuk sesi lain)

Dokumen ini ditulis 2 November 2026 supaya sesi lain (Claude atau manusia) langsung tahu apa yang sudah dikerjakan di APK ini dan apa yang
dilakukan sisi PC. Baca dulu sebelum mengubah protokol atau pairing. Dokumen lain di folder ini (`HANDOFF_PC_FONDASI_BRIDGE.md`,
`NOTES_FROM_PC_SIDE.md`) ada di `.gitignore` dan sebagian **sudah usang**; yang berlaku adalah dokumen ini dan `docs/protocol.md` di repo bridge.

## Proyek terkait

| Apa | Di mana |
|---|---|
| APK ini | repo privat `dirgaastya/hesewake-bridge-android` (folder `hesewake-media-bridge-android`) |
| Extension PC (Rust) | repo privat `dirgaastya/hesewake-media-bridge-extension` (folder `hesewake-media-bridge-extension`, kode di `bridge/`) |
| Notch (tuan rumah extension) | repo publik `dirgaastya/hesewake-notch`, rilis 0.13.0 ke atas; protokol extension di `docs/extensions.md` |
| **Kontrak protokol HP-PC** | `hesewake-media-bridge-extension/docs/protocol.md` (sumber kebenaran; ikut diperbarui bila protokol berubah) |

## Yang sudah dilakukan sisi PC (semuanya punya tes otomatis di PC; yang sudah dicoba di HP sungguhan: pairing, sambungan, tampilan lagu; sisanya ada di "Belum teruji")

- `capabilities` di `pair_result` dan `auth_result` sukses: `["artwork_delta_v1", "forget_v1"]`.
- **`artwork_same`**: PC menyimpan gambar terakhir per koneksi dan mengembalikannya saat snapshot berisi `artwork_same: true`. Gambar tidak diwariskan
  ke lagu tanpa gambar, ke `active:false`, atau ke koneksi berikutnya.
- **`device_name`** di `pair_hello` dan `auth` dipakai sebagai nama awal HP; nama yang sudah diganti pengguna tidak ditimpa. Pada `auth`, nama hanya diambil bila HP
  masih bernama bernomor ("Phone N").
- **`forget`** (hanya bila `forget_v1` diiklankan): PC mencabut token, menyimpan ke disk, baru membalas `forget_result`. Bila gagal menyimpan, balasannya `approved:false`
  dan tidak ada yang berubah. *(Sudah ada tes otomatis di PC; belum diuji dari HP sungguhan.)*
- **`device_id`** (baru, lihat di bawah): pair ulang dengan ID yang sama tidak membuat entri ganda.
- **ID perintah (`command.id`) harus UUID huruf kecil.** APK memvalidasinya (`require(id.length == 36 && UUID...)`) dan menutup koneksi bila bukan. PC sempat mengirim 16 hex
  dan itu memutus sambungan setiap kali tombol kontrol ditekan; sudah diperbaiki di PC.
- **Port tetap**: PC mengingat port di `%APPDATA%\hesewake-media-bridge\port.txt` dan memakainya lagi bila bebas (sebelumnya acak tiap start, sehingga alamat yang diingat HP
  usang setiap bridge restart).
- **Alamat yang diberitahukan ke HP** hanya alamat adaptor asli di rentang privat (10/8, 172.16/12, 192.168/16). Adaptor tunnel/virtual (NetBird `wt0`, WireGuard, Tailscale, VM, WSL,
  hotspot Windows) dilewati, baik di QR maupun di pengumuman mDNS. Alamat 100.64/10 (CGNAT, dipakai NetBird/Tailscale) tidak pernah dipakai.
- Log di `%APPDATA%\hesewake-media-bridge\bridge.log`: koneksi masuk (alamat dan pesan pertama), alasan pairing ditolak, alasan koneksi berakhir, perintah dan `ack`, perubahan
  `active/playing` (tanpa judul lagu atau gambar).

## Perubahan di APK ini

1. **`device_id`** (`DeviceId.kt`, `Bridge.kt`): `pair_hello` dan `auth` membawa `device_id` = 32 karakter hex pertama dari SHA-256 (`"hesewake-bridge-device-v1:" + ANDROID_ID`).
   Stabil setelah pasang ulang (dengan kunci tanda tangan yang sama) dan hapus data; nilai Android ID asli tidak dikirim. Tes: `DeviceIdTest`.
   - PC: pair ulang dengan `device_id` yang sama **mempertahankan satu entri** (nama dan urutan tetap) dan mengganti token; token lama mati. HP tanpa ID (APK lama) tetap entri baru
     tiap kali. HP yang dipasangkan sebelum ada ID mendapat ID-nya saat `auth`.
   - Catatan: `ANDROID_ID` bergantung pada kunci tanda tangan aplikasi. APK debug dan APK rilis punya kunci berbeda, jadi ID-nya **berbeda**; berpindah dari debug ke rilis
     (yang memang harus uninstall dulu) menghasilkan entri baru di PC.
2. **Tanda tangan rilis** (`app/build.gradle.kts`): bila `HESEWAKE_KEYSTORE` (path `.jks`), `HESEWAKE_KEYSTORE_PASSWORD` dan opsional `HESEWAKE_KEY_ALIAS` (bawaan `hesewake`) diisi,
   `:app:assembleRelease` menandatangani APK. Tanpa itu hasilnya tidak bertanda tangan. **Kunci tidak ada di repo** (lihat bagian "Kunci rilis").
3. **CI** (`.github/workflows/ci.yml`, `release.yml`): CI menjalankan tes unit dan `assembleDebug`. Rilis (tag `v*`) membangun APK bertanda tangan dan membuat **draf** rilis berisi APK
   dan `SHA256SUMS`. Catatan agar CI jalan: `gradlew` harus berbit eksekusi di git (sudah di-set), dan runner Ubuntu sudah membawa Android SDK (jangan pakai `setup-android`).
4. README ditulis ulang; `.gitignore` diperluas (`.kotlin/`, `*.jks`, `keystore.env`, dst.).

## Hal yang sudah dipelajari (jangan diulang)

- **Mode pairing di PC hanya terbuka 2 menit** setelah **Pair a phone** ditekan di notch. HP yang mulai pairing di luar jendela itu ditolak (log PC: `pairing refused: NotPairing`) dan
  menampilkan "Pairing gagal atau ditolak". Urutan benar: tekan **Pair a phone** di notch dulu, baru mulai di HP. Lima kegagalan berturut-turut mengunci pairing 60 detik.
- **Firewall Windows**: aturan masuk untuk bridge dibuat per profil jaringan. Aturan yang dibuat saat jaringan berprofil Public tidak berlaku di jaringan Private (dan sebaliknya).
  Gejalanya: HP tidak bisa menjangkau port bridge sama sekali (tidak ada satu pun baris `connection from` di log PC).
- Android sering tidak menjawab ping (ICMP), terutama saat layar mati; ping bukan cara menguji bridge. Ujilah dengan membuka `https://<ip-pc>:<port>` di browser HP (harus muncul
  peringatan sertifikat bila jaringan dan firewall beres).
- `soTimeout` socket HP adalah 35 detik dan PC mengirim `ping` tiap 10 detik; HP mengirim `ping` tiap 10 snapshot.
- Format base64: nonce dan token **base64url tanpa padding**; gambar **base64 standar berpadding**.

## Belum teruji dengan HP sungguhan

- Tombol kontrol dari PC setelah perbaikan UUID (play, pause, next, previous, seek) dan penolakan aksi yang tidak didukung pemutar.
- "Lupakan laptop" di HP (berhasil, gagal, dan PC offline) dan token lama ditolak sesudahnya.
- Dua lagu berjudul sama dengan gambar berbeda.

## Status rilis (2 November 2026)

- Notch **0.13.0** sudah terbit (publik).
- Draf rilis **`v0.1.0`** ada di repo APK dan repo bridge (privat), **belum diterbitkan**. Keduanya dibangun **sebelum** perbaikan port tetap, pemilihan alamat, log pairing, dan `device_id`.
  Rencananya: batalkan draf itu dan rilis **`v0.1.1`** dari keduanya (naikkan `versionName` + `versionCode` di `app/build.gradle.kts`, versi di `bridge/Cargo.toml`, tanggal di CHANGELOG bridge).
  Tag yang tidak cocok dengan versi akan membuat workflow rilis gagal dengan sengaja.
- Jangan menerbitkan rilis atau mendorong tag tanpa izin pemilik.

## Kunci rilis

- Berkas `.jks` ada di `C:\Users\ThinkPad\.hesewake-keys\` di PC pemilik (bersama berkas kata sandi), **di luar repo mana pun**. Jangan disalin, dicetak, atau di-commit.
- Dua secret ada di repo APK di GitHub: `HESEWAKE_KEYSTORE_B64` dan `HESEWAKE_KEYSTORE_PASSWORD`. Sidik jari sertifikat rilis: SHA-256 `57:CA:EC:8E:...:E0:E3:47` (diperiksa dengan `apksigner`).
- **Belum dicadangkan** di luar PC itu (penyalinan ke OneDrive ditolak pengaman otomatis dan diserahkan ke pemilik). Kehilangan kunci berarti APK terpasang tidak bisa diperbarui.

## Cara menguji cepat

- PC: `cd bridge && cargo test --lib` (96 tes). Alat `fake_phone` (`cargo run --release --bin fake_phone -- --name X --id Y`) berperan sebagai HP, termasuk `--forget-on-pc`.
- APK: `.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug --offline` dengan `ANDROID_HOME` dan JDK 17/21. APK debug disalin pemilik ke `release-builds\android-debug\` di folder notch.
