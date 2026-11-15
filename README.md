# Anında Çeviri

Oyunlardaki ve uygulamalardaki yabancı yazıyı ekranda Türkçeye çeviren Android uygulaması.

## Nasıl çalışır

1. Ekranı yakalar (kullanıcı her başlatmada izin verir).
2. Yazıyı telefonun içinde okur (ML Kit Text Recognition, model APK'da).
3. Telefonun içinde Türkçeye çevirir (ML Kit Translate; dil paketi ilk açılışta bir kez iner).
4. Çeviriyi özgün yazının üstüne kutu olarak çizer. Dokunmalar alttaki oyuna geçer.

Ekran görüntüsü ve yazılar telefondan dışarı çıkmaz.

## Dosyalar

- `app/src/main/java/app/anindaceviri/Motor.java`: neyin ne zaman okunup çevrileceğine, hangi kutunun eskidiğine karar veren mantık.
- `CeviriServisi.java`: ekran yakalama, yazı okuma, çeviri, baloncuk ve kutuların çizimi.
- `MainActivity.java`: ana ekran, dil seçimi ve izinler.
- `deneme/`: motoru telefonsuz, sahte oyun ekranlarıyla deneyen düzenek (`deneme/calistir.sh`).

Her değişiklikte GitHub Actions APK'yı derler ve **Releases → Anında Çeviri (son sürüm)** altına koyar.
