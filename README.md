# Hesewake Bridge (Android)

Aplikasi Android yang mengirim status media yang sedang diputar di HP ke [Hesewake Notch](https://github.com/dirgaastya/hesewake-notch) lewat jaringan lokal. Audio tetap di HP. Tidak ada akun dan tidak ada cloud. Aplikasi memerlukan **akses notifikasi** supaya bisa membaca sesi media aplikasi lain (judul, artis, gambar, posisi) dan menjalankan tombol play, pause, next, previous, dan seek dari PC.

Sisi PC adalah extension **Hesewake Media Bridge** (`hesewake-media-bridge-extension`); protokol di antara keduanya ada di `docs/protocol.md` repo itu.

## Pasang dan pakai

1. Pasang `HesewakeBridge-<versi>.apk` (izinkan pemasangan dari sumber ini bila diminta). APK ditandatangani dengan kunci rilis; APK debug lama tidak bisa ditimpa dan harus dihapus dulu (pairing diulang).
2. Buka aplikasi, beri **akses notifikasi** saat diminta.
3. Di PC, buka Hesewake Notch > Settings > Extensions > Media Bridge > Settings > **Pair a phone**.
4. Di HP, pindai kode QR itu, atau pilih PC dari daftar. Kedua layar menampilkan enam angka yang sama: setujui di keduanya.

Persyaratan: Android 8.0 atau lebih baru, satu jaringan Wi-Fi dengan PC.

## Privasi

- Data hanya dikirim ke PC yang sudah Anda pasangkan, lewat TLS dengan sertifikat yang sidik jarinya disematkan. Token disimpan terenkripsi (AES-GCM) di Android Keystore.
- Judul lagu dan gambar tidak dicatat di log mana pun.
- `allowBackup` mati dan lalu lintas tanpa TLS dilarang.

## Build dan tes

Perlu JDK 17 atau 21 dan Android SDK 36. Di PowerShell:

```powershell
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug
```

APK debug: `app/build/outputs/apk/debug/app-debug.apk` (kunci debug lokal).

### Rilis bertanda tangan

Kunci rilis **tidak ada di repo**. Bila variabel lingkungan `HESEWAKE_KEYSTORE` (path ke `.jks`), `HESEWAKE_KEYSTORE_PASSWORD`, dan (opsional) `HESEWAKE_KEY_ALIAS` (bawaan `hesewake`) diisi, `:app:assembleRelease` menandatangani APK-nya; tanpa itu hasilnya tidak bertanda tangan.

```powershell
.\gradlew.bat :app:assembleRelease   # app/build/outputs/apk/release/
```

**Cadangkan berkas `.jks` dan kata sandinya.** Kehilangan kunci berarti pembaruan tidak bisa menimpa APK yang sudah terpasang.

Rilis lewat GitHub Actions: dorong tag `vX.Y.Z` yang sama dengan `versionName` di `app/build.gradle.kts`. Workflow menjalankan tes, membangun APK bertanda tangan, dan membuat draf rilis berisi APK dan `SHA256SUMS`. Ia butuh dua secret repo: `HESEWAKE_KEYSTORE_B64` (isi `.jks` dalam base64) dan `HESEWAKE_KEYSTORE_PASSWORD`.

## Receiver tiruan untuk pengujian manual

`tools/fake_receiver.py` adalah receiver Python hanya untuk uji: sertifikat sementara, dan perangkat tepercaya hilang saat dihentikan. Jalankan hanya di jaringan yang Anda percaya.

```powershell
python tools/fake_receiver.py --self-test
$jar = (Get-ChildItem "$env:USERPROFILE\.gradle\caches\modules-2\files-2.1\com.google.zxing\core\3.5.3" -Filter '*.jar' -Recurse | Select-Object -First 1).FullName
python tools/fake_receiver.py --serve --address 192.168.1.10 --zxing-jar $jar --qr-output receiver-qr.png
```

Ganti alamat dengan IPv4 PC. Tambahkan `--command pause` (atau `--command seek --position-ms 30000`) untuk mengirim satu perintah setelah autentikasi. `--self-test` hanya menguji pasangan client/server Python di loopback, bukan kode Android.

## Lisensi

MIT, lihat [LICENSE](LICENSE).
