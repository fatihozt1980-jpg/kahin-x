# Tercüman

Konuşmayı, yazıyı ve kameradaki yazıyı anında çeviren Android uygulaması. 59 dil.

## Ne yapar

- **Konuşma:** DİNLE'ye basınca karşı tarafı sürekli dinler, söyleneni senin diline çevirir, yazar ve sesli okur.
  CEVAP VER'e basıp konuşunca söylediğini karşı tarafın diline çevirip sesli okur, sonra dinlemeye döner.
- **Kulaklık:** Kulaklık takılıysa çeviri kulağına gelir. Kulaklık modu açıkken senin cevabın telefonun
  hoparlöründen çıkar; karşı taraf duyar.
- **Yazı:** Yazdığını ya da yapıştırdığını yazarken çevirir; sesli okur, kopyalar.
- **Kamera:** Yazıyı kadraja alıp ÇEVİR'e basınca çeviriyi resmin üstüne, özgün yazının yerine yazar.

## Nasıl çalışır

- Çeviri telefonun içinde yapılır (ML Kit Translate). Dil paketi dil başına bir kez iner, sonra internetsiz çalışır.
- Konuşmayı yazıya telefonun kendi ses tanıması çevirir (Google). Çoğu dilde internet ister.
- Sesli okumayı telefonun kendi sesi yapar (Google sesli okuma).
- Kamera yazıyı telefonun içinde okur (ML Kit Text Recognition): Latin, Çince, Japonca, Korece, Devanagari.
  Arap, Kiril, Yunan gibi alfabeleri kamera okuyamaz; o dillerde konuşma ve yazı çevirisi çalışır.

## Dosyalar

- `MainActivity.java`: ekran, üç sekme ve konuşma sırası (kim dinleniyor, ne zaman okunuyor).
- `Dinleyici.java`: ses tanıma.
- `Seslendirici.java`: sesli okuma ve kulaklık / hoparlör yönlendirmesi.
- `Cevirmen.java`: çeviri ve dil paketleri.
- `KameraKatmani.java`: çeviri kutularının resmin üstüne çizimi.
- `Diller.java`: dil listesi.

Her değişiklikte GitHub Actions APK'yı derler ve **Releases → Tercüman (son sürüm)** altına koyar.
Derlemenin sonucu `tercuman-derleme-kaydi` dalına yazılır.
