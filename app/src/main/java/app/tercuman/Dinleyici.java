package app.tercuman;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;

import java.util.ArrayList;

/** Telefonun ses tanımasını kullanarak konuşmayı yazıya çevirir. Bütün çağrılar ana iş parçacığından yapılmalı. */
final class Dinleyici implements RecognitionListener {
    interface Olay {
        /** Konuşma sürerken gelen ara metin. */
        void ara(String metin);

        /** Cümle bitti, kesin metin. */
        void son(String metin);

        /** Ses tanıma hatası (SpeechRecognizer.ERROR_* kodu). */
        void hata(int kod);
    }

    private final Context ctx;
    private final Olay olay;
    private SpeechRecognizer tanici;
    private boolean aktif;
    private String sonAra = "";

    Dinleyici(Context c, Olay o) {
        ctx = c.getApplicationContext();
        olay = o;
    }

    boolean aktif() {
        return aktif;
    }

    /** Verilen dilde (ör. "en-US") dinlemeye başlar. */
    void baslat(String etiket) {
        try {
            if (tanici == null) {
                tanici = SpeechRecognizer.createSpeechRecognizer(ctx);
                tanici.setRecognitionListener(this);
            }
            Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, etiket);
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, etiket);
            i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
            i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
            i.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, ctx.getPackageName());
            sonAra = "";
            aktif = true;
            tanici.startListening(i);
        } catch (Throwable t) {
            aktif = false;
            kapat();
            olay.hata(SpeechRecognizer.ERROR_CLIENT);
        }
    }

    /** Konuşmayı erken bitirir; o ana kadar duyulan kesin sonuç olarak gelir. */
    void bitir() {
        try {
            if (tanici != null && aktif) tanici.stopListening();
        } catch (Throwable ignored) {
        }
    }

    /** Dinlemeyi sonuç beklemeden keser. */
    void iptal() {
        aktif = false;
        try {
            if (tanici != null) tanici.cancel();
        } catch (Throwable ignored) {
        }
    }

    /** Ses tanımayı tamamen bırakır; bir sonraki başlatmada yeniden kurulur. */
    void kapat() {
        aktif = false;
        try {
            if (tanici != null) tanici.destroy();
        } catch (Throwable ignored) {
        }
        tanici = null;
    }

    private static String ilk(Bundle b) {
        if (b == null) return "";
        ArrayList<String> l = b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (l == null || l.isEmpty() || l.get(0) == null) return "";
        return l.get(0).trim();
    }

    @Override
    public void onPartialResults(Bundle b) {
        if (!aktif) return;
        String m = ilk(b);
        if (m.isEmpty()) return;
        sonAra = m;
        olay.ara(m);
    }

    @Override
    public void onResults(Bundle b) {
        if (!aktif) return;
        aktif = false;
        String m = ilk(b);
        if (m.isEmpty()) m = sonAra;
        if (m.isEmpty()) olay.hata(SpeechRecognizer.ERROR_NO_MATCH);
        else olay.son(m);
    }

    @Override
    public void onError(int kod) {
        if (!aktif) return;
        aktif = false;
        boolean sessizlik = kod == SpeechRecognizer.ERROR_NO_MATCH || kod == SpeechRecognizer.ERROR_SPEECH_TIMEOUT;
        // Bazı telefonlar ara metin verip sonra "anlaşılmadı" der; duyulanı boşa atma.
        if (sessizlik && !sonAra.isEmpty()) {
            olay.son(sonAra);
            return;
        }
        // Sessizlik dışındaki hatalarda tanıyıcıyı yenile; takılı kalmasın.
        if (!sessizlik) kapat();
        olay.hata(kod);
    }

    @Override
    public void onReadyForSpeech(Bundle b) {
    }

    @Override
    public void onBeginningOfSpeech() {
    }

    @Override
    public void onRmsChanged(float v) {
    }

    @Override
    public void onBufferReceived(byte[] b) {
    }

    @Override
    public void onEndOfSpeech() {
    }

    @Override
    public void onEvent(int tur, Bundle b) {
    }
}
