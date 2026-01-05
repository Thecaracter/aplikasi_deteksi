# Panduan Penggunaan Aplikasi Deteksi Jalan

## Instalasi Model TensorFlow Lite

Untuk menggunakan aplikasi ini, Anda perlu menambahkan model `best_float16.tflite` ke dalam proyek:

### Langkah-langkah:

1. **Buat folder assets** (jika belum ada):

   - Buka folder: `app/src/main/`
   - Buat folder baru bernama `assets`
   - Path lengkap: `app/src/main/assets`

2. **Copy model ke folder assets**:

   - Copy file `best_float16.tflite` ke folder `app/src/main/assets/`

3. **Konfigurasi Model** (jika diperlukan):
   - Buka file `ObjectDetectorHelper.kt`
   - Model sudah dikonfigurasi untuk:
     - `inputSize`: 640 (ukuran input model)
     - `numClasses`: 1 (single class: jalan_berlubang)
     - `labels`: ["jalan_berlubang"] (label untuk pothole detection)
   - Sesuaikan `threshold` jika perlu (default: 0.5)

## Fitur-fitur Aplikasi

### 1. Splash Screen

- Tampil otomatis saat aplikasi dibuka
- Durasi: 2.5 detik
- Menampilkan logo dan nama aplikasi

### 2. Upload Gambar untuk Deteksi

- Pilih gambar dari galeri
- Model akan mendeteksi lubang jalan (pothole) dalam gambar
- Menampilkan bounding box dengan warna merah untuk setiap lubang yang terdeteksi
- Menampilkan estimasi jarak objek dari kamera
- Menampilkan confidence level untuk setiap deteksi

### 3. Deteksi Real-time

- Menggunakan kamera belakang ponsel
- Deteksi lubang jalan secara real-time
- Menampilkan overlay bounding box merah langsung di kamera
- Menghitung dan menampilkan jarak objek
- Panel hasil deteksi di bagian bawah layar

## Model Configuration

Model Anda memiliki konfigurasi:

- **Class**: 1 (jalan_berlubang - pothole detection)
- **Total Images**: 782 (714 train + 68 validation)
- **Total Annotations**: 1811 (1660 train + 151 validation)
- **Input Size**: 640x640
- **Model Format**: TensorFlow Lite Float16

## Kalibrasi Jarak

Untuk meningkatkan akurasi perhitungan jarak, Anda perlu mengkalibrasi focal length kamera:

1. Buka file `ObjectDetectorHelper.kt`
2. Temukan fungsi `calculateDistance()`
3. Sesuaikan nilai:
   - `focalLength`: Default 700 (sesuaikan dengan kamera Anda)
   - `knownRealHeight`: Tinggi rata-rata objek dalam meter (default: 0.3m)

### Cara Kalibrasi:

```kotlin
// Rumus: focalLength = (imageHeight * distance) / realHeight
// Contoh: objek dengan tinggi 0.3m berada pada jarak 2m
// dan tampak setinggi 105 pixel di layar
// focalLength = (105 * 2) / 0.3 = 700
```

## Permission yang Diperlukan

Aplikasi memerlukan izin:

- **CAMERA**: Untuk deteksi real-time
- **READ_EXTERNAL_STORAGE**: Untuk memilih gambar dari galeri (Android < 13)
- **READ_MEDIA_IMAGES**: Untuk memilih gambar dari galeri (Android >= 13)

## Troubleshooting

### Model tidak terdeteksi

- Pastikan file `best_float16.tflite` ada di `app/src/main/assets/`
- Sync Gradle dan rebuild proyek

### Hasil deteksi tidak akurat

- Sesuaikan nilai `threshold` di `ObjectDetectorHelper` (default: 0.5)
- Pastikan label kelas sesuai dengan model Anda

### Kamera tidak berfungsi

- Pastikan izin kamera sudah diberikan
- Cek di Settings > Apps > Aplikasi Deteksi Jalan > Permissions

### Jarak tidak akurat

- Lakukan kalibrasi focal length
- Sesuaikan nilai `knownRealHeight` dengan objek yang dideteksi

## Teknologi yang Digunakan

- **Kotlin**: Bahasa pemrograman
- **Jetpack Compose**: UI framework
- **CameraX**: Untuk akses kamera
- **TensorFlow Lite**: Inference model ML
- **Navigation Compose**: Navigasi antar layar
- **Coil**: Image loading
- **Accompanist Permissions**: Permission handling

## Build dan Run

1. Buka proyek di Android Studio
2. Sync Gradle
3. Tambahkan model `best_float16.tflite` ke `app/src/main/assets/`
4. Build dan jalankan di device atau emulator
5. Berikan izin kamera saat diminta

## Minimum Requirements

- **Android SDK**: 24 (Android 7.0 Nougat)
- **Target SDK**: 36
- **Compile SDK**: 36
- **JDK**: 11
