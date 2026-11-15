package app.tercuman;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

import java.io.File;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Çeviriyi sesli okur. Normalde ses nereye gidiyorsa oraya (kulaklık takılıysa kulaklığa) okur.
 * İstenirse kulaklık takılıyken bile telefonun hoparlöründen okuyabilir; karşı taraf duysun diye.
 */
final class Seslendirici {
    interface Bitti {
        void bitti();
    }

    private static final int EN_UZUN = 3500;

    private final Context ctx;
    private final AudioManager sesYon;
    private final Handler el = new Handler(Looper.getMainLooper());
    private final Map<String, Bitti> bekleyen = new HashMap<>();
    private final Map<String, File> dosyalar = new HashMap<>();
    private TextToSpeech tts;
    private MediaPlayer oynatici;
    private boolean hazir;
    private int sayac;

    private final UtteranceProgressListener izleyici = new UtteranceProgressListener() {
        @Override
        public void onStart(String id) {
        }

        @Override
        public void onDone(final String id) {
            el.post(() -> tamamlandi(id));
        }

        @Override
        public void onError(final String id) {
            el.post(() -> vazgec(id));
        }

        @Override
        public void onError(final String id, int kod) {
            el.post(() -> vazgec(id));
        }
    };

    Seslendirici(Context c) {
        ctx = c.getApplicationContext();
        sesYon = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
        tts = new TextToSpeech(ctx, durum -> {
            hazir = durum == TextToSpeech.SUCCESS;
            if (hazir && tts != null) tts.setOnUtteranceProgressListener(izleyici);
        });
    }

    /**
     * Metni verilen dilde okur.
     *
     * @param hoparlorden true ise kulaklık takılı olsa da telefonun hoparlöründen okur
     * @param siraya      true ise süren okumayı kesmez, arkasına ekler
     * @param b           okuma bitince (ya da okunamayınca) çağrılır; null olabilir
     * @return okuma başlatılabildiyse true; bu dil için telefonda ses yoksa false
     */
    boolean soyle(String metin, String etiket, boolean hoparlorden, boolean siraya, Bitti b) {
        if (metin == null || metin.trim().isEmpty() || !hazir || tts == null) {
            if (b != null) el.post(b::bitti);
            return false;
        }
        if (metin.length() > EN_UZUN) metin = metin.substring(0, EN_UZUN);
        int dilSonucu;
        try {
            dilSonucu = tts.setLanguage(Locale.forLanguageTag(etiket));
        } catch (Throwable t) {
            dilSonucu = TextToSpeech.LANG_NOT_SUPPORTED;
        }
        if (dilSonucu == TextToSpeech.LANG_MISSING_DATA || dilSonucu == TextToSpeech.LANG_NOT_SUPPORTED) {
            if (b != null) el.post(b::bitti);
            return false;
        }
        if (!siraya) {
            bekleyen.clear();
            dosyalariSil();
            oynaticiyiKapat();
        }
        final String id = "s" + (++sayac);
        if (b != null) bekleyen.put(id, b);
        int sonuc;
        try {
            if (hoparlorden && Build.VERSION.SDK_INT >= 28 && hoparlor() != null) {
                // Hoparlöre yönlendirebilmek için önce dosyaya okutup sonra o dosyayı hoparlörden çalarız.
                if (!siraya) tts.stop();
                File f = new File(ctx.getCacheDir(), id + ".wav");
                dosyalar.put(id, f);
                sonuc = tts.synthesizeToFile(metin, new Bundle(), f, id);
            } else {
                sonuc = tts.speak(metin, siraya ? TextToSpeech.QUEUE_ADD : TextToSpeech.QUEUE_FLUSH, new Bundle(), id);
            }
        } catch (Throwable t) {
            sonuc = TextToSpeech.ERROR;
        }
        if (sonuc != TextToSpeech.SUCCESS) {
            dosyalar.remove(id);
            Bitti x = bekleyen.remove(id);
            if (x != null) el.post(x::bitti);
            return false;
        }
        return true;
    }

    /** Süren ve sırada bekleyen bütün okumaları keser; bekleyen geri çağrılar çağrılmaz. */
    void sus() {
        bekleyen.clear();
        dosyalariSil();
        oynaticiyiKapat();
        try {
            if (tts != null) tts.stop();
        } catch (Throwable ignored) {
        }
    }

    void kapat() {
        sus();
        try {
            if (tts != null) tts.shutdown();
        } catch (Throwable ignored) {
        }
        tts = null;
        hazir = false;
    }

    /** Kablolu ya da Bluetooth kulaklık takılı mı? */
    boolean kulaklikVar() {
        try {
            for (AudioDeviceInfo d : sesYon.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
                int t = d.getType();
                if (t == AudioDeviceInfo.TYPE_WIRED_HEADSET
                        || t == AudioDeviceInfo.TYPE_WIRED_HEADPHONES
                        || t == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
                        || t == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
                        || t == AudioDeviceInfo.TYPE_USB_HEADSET
                        || t == 23 /* işitme cihazı */
                        || t == 26 /* Bluetooth LE kulaklık */) {
                    return true;
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private AudioDeviceInfo hoparlor() {
        try {
            for (AudioDeviceInfo d : sesYon.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
                if (d.getType() == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) return d;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private void tamamlandi(String id) {
        File f = dosyalar.remove(id);
        if (f != null) cal(f, id);
        else bitir(id);
    }

    private void vazgec(String id) {
        File f = dosyalar.remove(id);
        if (f != null) f.delete();
        bitir(id);
    }

    private void bitir(String id) {
        Bitti b = bekleyen.remove(id);
        if (b != null) b.bitti();
    }

    /** Dosyaya okutulmuş sesi telefonun hoparlöründen çalar. */
    private void cal(final File f, final String id) {
        oynaticiyiKapat();
        try {
            final MediaPlayer mp = new MediaPlayer();
            oynatici = mp;
            mp.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build());
            mp.setDataSource(f.getAbsolutePath());
            AudioDeviceInfo h = hoparlor();
            if (h != null && Build.VERSION.SDK_INT >= 28) mp.setPreferredDevice(h);
            mp.setOnCompletionListener(x -> {
                if (oynatici == mp) oynaticiyiKapat();
                f.delete();
                bitir(id);
            });
            mp.setOnErrorListener((x, ne, ek) -> {
                if (oynatici == mp) oynaticiyiKapat();
                f.delete();
                bitir(id);
                return true;
            });
            mp.prepare();
            mp.start();
        } catch (Throwable t) {
            oynaticiyiKapat();
            f.delete();
            bitir(id);
        }
    }

    private void oynaticiyiKapat() {
        MediaPlayer mp = oynatici;
        oynatici = null;
        if (mp == null) return;
        try {
            mp.release();
        } catch (Throwable ignored) {
        }
    }

    private void dosyalariSil() {
        for (File f : dosyalar.values()) f.delete();
        dosyalar.clear();
    }
}
