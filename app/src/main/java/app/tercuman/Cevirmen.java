package app.tercuman;

import com.google.mlkit.common.model.DownloadConditions;
import com.google.mlkit.nl.translate.TranslateLanguage;
import com.google.mlkit.nl.translate.Translation;
import com.google.mlkit.nl.translate.Translator;
import com.google.mlkit.nl.translate.TranslatorOptions;

/** İki dil arasında, telefonun içinde çeviri yapar. Dil paketleri ilk kullanımda bir kez iner. */
final class Cevirmen {
    interface Sonuc {
        void tamam(String ceviri);

        void hata(String neden);
    }

    interface Hazir {
        void durum(boolean hazir);
    }

    private Translator karsidanBana, bendenKarsiya;
    private boolean ayni;
    private int kusak;

    /** Dilleri ayarlar ve gereken dil paketlerini indirir; bitince geri haber verir. */
    void ayarla(String ben, String karsi, final Hazir geri) {
        kapat();
        final int k = ++kusak;
        ayni = ben.equals(karsi);
        if (ayni) {
            geri.durum(true);
            return;
        }
        final Translator a = Translation.getClient(new TranslatorOptions.Builder()
                .setSourceLanguage(kod(karsi)).setTargetLanguage(kod(ben)).build());
        final Translator b = Translation.getClient(new TranslatorOptions.Builder()
                .setSourceLanguage(kod(ben)).setTargetLanguage(kod(karsi)).build());
        karsidanBana = a;
        bendenKarsiya = b;
        final DownloadConditions kosul = new DownloadConditions.Builder().build();
        a.downloadModelIfNeeded(kosul)
                .addOnSuccessListener(x -> {
                    if (k != kusak) return;
                    b.downloadModelIfNeeded(kosul)
                            .addOnSuccessListener(y -> {
                                if (k == kusak) geri.durum(true);
                            })
                            .addOnFailureListener(e -> {
                                if (k == kusak) geri.durum(false);
                            });
                })
                .addOnFailureListener(e -> {
                    if (k == kusak) geri.durum(false);
                });
    }

    private static String kod(String dil) {
        String k = TranslateLanguage.fromLanguageTag(dil);
        return k != null ? k : dil;
    }

    /** benden true ise benim dilimden karşı dile, değilse karşı dilden benim dilime çevirir. */
    void cevir(boolean benden, String metin, final Sonuc s) {
        if (metin == null || metin.trim().isEmpty()) {
            s.tamam("");
            return;
        }
        if (ayni) {
            s.tamam(metin);
            return;
        }
        final Translator t = benden ? bendenKarsiya : karsidanBana;
        if (t == null) {
            s.hata("hazır değil");
            return;
        }
        String[] satirlar = metin.split("\n", -1);
        if (satirlar.length == 1) tek(t, metin, s);
        else satirSatir(t, satirlar, 0, new StringBuilder(), s);
    }

    private void tek(Translator t, String metin, final Sonuc s) {
        try {
            t.translate(metin)
                    .addOnSuccessListener(c -> s.tamam(c == null ? "" : c))
                    .addOnFailureListener(e -> s.hata(String.valueOf(e.getMessage())));
        } catch (Throwable e) {
            s.hata(String.valueOf(e.getMessage()));
        }
    }

    /** Çok satırlı yazıda satır düzeni bozulmasın diye satırları sırayla çevirir. */
    private void satirSatir(final Translator t, final String[] satirlar, final int i,
                            final StringBuilder b, final Sonuc s) {
        if (i >= satirlar.length) {
            s.tamam(b.toString());
            return;
        }
        final boolean sonSatir = i == satirlar.length - 1;
        if (satirlar[i].trim().isEmpty()) {
            if (!sonSatir) b.append('\n');
            satirSatir(t, satirlar, i + 1, b, s);
            return;
        }
        tek(t, satirlar[i], new Sonuc() {
            @Override
            public void tamam(String c) {
                b.append(c);
                if (!sonSatir) b.append('\n');
                satirSatir(t, satirlar, i + 1, b, s);
            }

            @Override
            public void hata(String neden) {
                s.hata(neden);
            }
        });
    }

    void kapat() {
        kusak++;
        try {
            if (karsidanBana != null) karsidanBana.close();
        } catch (Throwable ignored) {
        }
        try {
            if (bendenKarsiya != null) bendenKarsiya.close();
        } catch (Throwable ignored) {
        }
        karsidanBana = null;
        bendenKarsiya = null;
    }
}
