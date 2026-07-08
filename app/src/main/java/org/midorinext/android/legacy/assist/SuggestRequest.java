package org.midorinext.android.legacy.assist;

import android.util.JsonReader;
import android.util.Log;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Locale;

class SuggestRequest {
    private final static String LOGTAG = "MIDORI_BROWSER_ASSIST";
    private final static String BASE_URL = "https://api.qwant.com/api/suggest/?client=opensearch&q=";

    private static InputStream getHttpStream(URL url) throws Exception {
        try {
            HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
            urlConnection.connect();
            if (urlConnection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                return urlConnection.getInputStream();
            }
        } catch (Exception e) {
            throw new Exception("Request connection for suggest failed: " + e);
        }
        return null;
    }

    static ArrayList<String> getSuggestions(String filter_string) {
        try {
            ArrayList<String> result = new ArrayList<>();
            InputStream inputStream = getHttpStream(new URL(BASE_URL + filter_string + "&lang=" + Locale.getDefault()));
            if (inputStream != null) {
                try (JsonReader reader = new JsonReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                    reader.beginArray();
                    reader.skipValue();
                    reader.beginArray();
                    while (reader.hasNext()) {
                        result.add(reader.nextString());
                    }
                    reader.endArray();
                    reader.endArray();
                } catch (Exception e) {
                    Log.e(LOGTAG, "error reading suggest result: " + e.getMessage());
                }
            }
            return result;
        } catch (Exception e) {
            Log.e(LOGTAG, "Impossible de rapatrier les données de suggest:" + e.getMessage());
        }
        return new ArrayList<>();
    }
}
