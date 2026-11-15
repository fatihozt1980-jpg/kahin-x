package app.tercuman;

/** Desteklenen diller: çeviri kodu, Türkçe adı, ses tanıma etiketi ve kameranın okuyabildiği yazı türü. */
final class Diller {
    /** Kameranın okuyabildiği yazı türleri. */
    static final int LATIN = 0, CINCE = 1, JAPONCA = 2, KORECE = 3, DEVANAGARI = 4, OKUNMAZ = -1;

    static final class Dil {
        final String kod;   // çeviri kodu (ML Kit)
        final String ad;    // ekranda görünen ad
        final String ses;   // ses tanıma ve sesli okuma etiketi; null ise bu dilde ses yok
        final int yazi;     // kameranın okuyacağı yazı türü

        Dil(String kod, String ad, String ses, int yazi) {
            this.kod = kod;
            this.ad = ad;
            this.ses = ses;
            this.yazi = yazi;
        }

        String sesEtiketi() {
            return ses != null ? ses : kod;
        }
    }

    /** İlk sekiz dil en çok kullanılanlar; gerisi alfabe sırasıyla. */
    static final Dil[] HEPSI = {
            new Dil("tr", "Türkçe", "tr-TR", LATIN),
            new Dil("en", "İngilizce", "en-US", LATIN),
            new Dil("de", "Almanca", "de-DE", LATIN),
            new Dil("ar", "Arapça", "ar-SA", OKUNMAZ),
            new Dil("ru", "Rusça", "ru-RU", OKUNMAZ),
            new Dil("fr", "Fransızca", "fr-FR", LATIN),
            new Dil("es", "İspanyolca", "es-ES", LATIN),
            new Dil("it", "İtalyanca", "it-IT", LATIN),
            new Dil("af", "Afrikaanca", "af-ZA", LATIN),
            new Dil("sq", "Arnavutça", "sq-AL", LATIN),
            new Dil("be", "Belarusça", null, OKUNMAZ),
            new Dil("bn", "Bengalce", "bn-BD", OKUNMAZ),
            new Dil("bg", "Bulgarca", "bg-BG", OKUNMAZ),
            new Dil("cs", "Çekçe", "cs-CZ", LATIN),
            new Dil("zh", "Çince", "zh-CN", CINCE),
            new Dil("da", "Danca", "da-DK", LATIN),
            new Dil("id", "Endonezce", "id-ID", LATIN),
            new Dil("eo", "Esperanto", null, LATIN),
            new Dil("et", "Estonca", "et-EE", LATIN),
            new Dil("fa", "Farsça", "fa-IR", OKUNMAZ),
            new Dil("nl", "Felemenkçe", "nl-NL", LATIN),
            new Dil("tl", "Filipince", "fil-PH", LATIN),
            new Dil("fi", "Fince", "fi-FI", LATIN),
            new Dil("cy", "Galce", "cy-GB", LATIN),
            new Dil("gl", "Galiçyaca", "gl-ES", LATIN),
            new Dil("gu", "Guceratça", "gu-IN", OKUNMAZ),
            new Dil("ka", "Gürcüce", "ka-GE", OKUNMAZ),
            new Dil("ht", "Haiti Kreolü", null, LATIN),
            new Dil("hi", "Hintçe", "hi-IN", DEVANAGARI),
            new Dil("hr", "Hırvatça", "hr-HR", LATIN),
            new Dil("he", "İbranice", "he-IL", OKUNMAZ),
            new Dil("ga", "İrlandaca", "ga-IE", LATIN),
            new Dil("sv", "İsveççe", "sv-SE", LATIN),
            new Dil("is", "İzlandaca", "is-IS", LATIN),
            new Dil("ja", "Japonca", "ja-JP", JAPONCA),
            new Dil("kn", "Kannada", "kn-IN", OKUNMAZ),
            new Dil("ca", "Katalanca", "ca-ES", LATIN),
            new Dil("ko", "Korece", "ko-KR", KORECE),
            new Dil("pl", "Lehçe", "pl-PL", LATIN),
            new Dil("lv", "Letonca", "lv-LV", LATIN),
            new Dil("lt", "Litvanca", "lt-LT", LATIN),
            new Dil("hu", "Macarca", "hu-HU", LATIN),
            new Dil("mk", "Makedonca", "mk-MK", OKUNMAZ),
            new Dil("ms", "Malayca", "ms-MY", LATIN),
            new Dil("mt", "Maltaca", "mt-MT", LATIN),
            new Dil("mr", "Marathi", "mr-IN", DEVANAGARI),
            new Dil("no", "Norveççe", "nb-NO", LATIN),
            new Dil("pt", "Portekizce", "pt-PT", LATIN),
            new Dil("ro", "Rumence", "ro-RO", LATIN),
            new Dil("sk", "Slovakça", "sk-SK", LATIN),
            new Dil("sl", "Slovence", "sl-SI", LATIN),
            new Dil("sw", "Svahili", "sw-KE", LATIN),
            new Dil("ta", "Tamilce", "ta-IN", OKUNMAZ),
            new Dil("th", "Tayca", "th-TH", OKUNMAZ),
            new Dil("te", "Telugu", "te-IN", OKUNMAZ),
            new Dil("uk", "Ukraynaca", "uk-UA", OKUNMAZ),
            new Dil("ur", "Urduca", "ur-PK", OKUNMAZ),
            new Dil("vi", "Vietnamca", "vi-VN", LATIN),
            new Dil("el", "Yunanca", "el-GR", OKUNMAZ),
    };

    static String[] adlar() {
        String[] a = new String[HEPSI.length];
        for (int i = 0; i < a.length; i++) a[i] = HEPSI[i].ad;
        return a;
    }

    static int sira(String kod) {
        for (int i = 0; i < HEPSI.length; i++) if (HEPSI[i].kod.equals(kod)) return i;
        return 0;
    }

    private Diller() {
    }
}
