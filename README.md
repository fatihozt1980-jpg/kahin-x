# Pusre

Hepsi bir arada kişisel uygulama: hava, haber, cüzdan, sağlık, oyunlar, notlar, piyasa ve bot (Android uygulaması).

- Uygulamanın kendisi tek dosyadır: `app/src/main/assets/pusre.html`
- Android kabuğu (`app/src/main/java/app/pusre`) onu çalıştırır; bildirim, mikrofon, kamera, konum ve dosya kaydetme köprüsünü sağlar.
- Her değişiklikte GitHub Actions APK'yı derler ve **Releases → Pusre (son sürüm)** (bu repoda `pusre` dalı) altına koyar.
