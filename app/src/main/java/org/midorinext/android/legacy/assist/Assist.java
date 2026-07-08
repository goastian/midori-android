package org.midorinext.android.legacy.assist;

import static android.content.ClipDescription.MIMETYPE_TEXT_HTML;
import static android.content.ClipDescription.MIMETYPE_TEXT_PLAIN;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.GeolocationPermissions;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.core.app.ActivityCompat;
import androidx.core.app.ComponentActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.textfield.TextInputEditText;
import org.midorinext.android.MidoriApplication;
import org.midorinext.android.R;
import org.midorinext.android.intent.IntentReceiverActivity;
import org.midorinext.android.usecases.MidoriUseCases;

import org.mozilla.geckoview.BuildConfig;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;


// TODO delete this file !

@AndroidEntryPoint
public class Assist extends AppCompatActivity {
    @Inject MidoriUseCases MidoriUseCases;

    public static final int MAX_SUGGEST_TEXT_LENGTH = 30;

    TextInputEditText search_text;
    WebView webview;
    TextView clipboard_text;
    LinearLayout clipboard_layout;
    LinearLayout search_input_layout;
    SuggestAdapter suggest_adapter;
    RecyclerView suggest_recyclerview;
    Intent new_tab_intent;
    CharSequence clipboard_full_text;
    boolean clipboard_is_url = false;

    boolean back_to_webview = false;

    // Geoloc permission
    final int MIDORI_PERMISSIONS_REQUEST_FINE_LOCATION = 0;
    private String permission_request_origin;
    private GeolocationPermissions.Callback permission_request_callback;
    // private MidoriUseCases MidoriUseCases;


    ArrayList<String> suggestItems = new ArrayList<>();

    @SuppressLint({"SetJavaScriptEnabled"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.assist_main);

        // Get reference to midori use cases early so it starts collecting url before we actually need it
        MidoriApplication application = (MidoriApplication) getApplication();
        // MidoriUseCases = application.useCases.get().getMidoriUseCases();
        // MidoriUseCases.warmUp();

        // Intent for opening url in browser. URL is set at just before starting activity.
        new_tab_intent = new Intent(this, IntentReceiverActivity.class);
        new_tab_intent.setPackage(getPackageName());
        new_tab_intent.setAction(Intent.ACTION_VIEW);

        webview = findViewById(R.id.webview);
        webview.getSettings().setBuiltInZoomControls (false);
        webview.getSettings().setDisplayZoomControls(false);
        webview.getSettings().setJavaScriptEnabled(true);

        webview.getSettings().setUserAgentString(BuildConfig.USER_AGENT_GECKOVIEW_MOBILE);

        // Maps settings
        webview.getSettings().setGeolocationEnabled(true);
        webview.getSettings().setBuiltInZoomControls(true);
        // Local storage emulation
        // webview.getSettings().setAppCacheEnabled(true);
        webview.getSettings().setDatabaseEnabled(true);
        webview.getSettings().setDomStorageEnabled(true);

        webview.setWebViewClient(new WebViewClient() {
            // If we get out of astiango.org, it opens in the browser, else stay in webview
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                Uri uri = Uri.parse(url);
                String host = uri.getHost();
                if (host != null && host.contains("astiango.org")) {
                    return false;
                }
                new_tab_intent.setData(uri);
                startActivity(new_tab_intent);
                return true;
            }

            // Show webview after first user request has finished
            @Override
            public void onPageFinished(WebView view, String url) {
                if (webview.getVisibility() == View.INVISIBLE /* && !webview.getUrl().contains("preload") */) {
                    webview.setVisibility(View.VISIBLE);
                    search_text.clearFocus();
                    webview.requestFocus();
                }
                back_to_webview = true;
            }
        });
        webview.setWebChromeClient(new WebChromeClient() {
            // Geoloc permission prompt for maps
            public void onGeolocationPermissionsShowPrompt(final String origin, final GeolocationPermissions.Callback callback) {
                permission_request_origin = null;
                permission_request_callback = null;
                final LocationManager manager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
                if (!manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                    final AlertDialog.Builder builder = new AlertDialog.Builder(Assist.this);
                    builder.setMessage("Your GPS seems to be disabled, do you want to enable it?")
                            .setCancelable(false)
                            .setPositiveButton("Yes", (dialog, id) -> startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)))
                            .setNegativeButton("No", (dialog, id) -> dialog.cancel());
                    final AlertDialog alert = builder.create();
                    alert.show();
                    callback.invoke(origin, false, false);
                } else {
                    if (ContextCompat.checkSelfPermission(Assist.this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                        if (ActivityCompat.shouldShowRequestPermissionRationale(Assist.this, Manifest.permission.READ_CONTACTS)) {
                            new AlertDialog.Builder(Assist.this)
                                    .setMessage("We can not provide location without this permission")
                                    .setNeutralButton("Understood ...", (dialogInterface, i) -> {
                                        permission_request_origin = origin;
                                        permission_request_callback = callback;
                                        ActivityCompat.requestPermissions(Assist.this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, MIDORI_PERMISSIONS_REQUEST_FINE_LOCATION);
                                    }).show();
                        } else {
                            permission_request_origin = origin;
                            permission_request_callback = callback;
                            ActivityCompat.requestPermissions(Assist.this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 0);
                        }
                    } else {
                        callback.invoke(origin, true, true);
                    }
                }
            }
        });
        // Hide webview and preload SERP for speed of next user request (cache)
        webview.setVisibility(View.INVISIBLE);

        AppCompatImageView cancel_cross = findViewById(R.id.widget_search_bar_cross);
        cancel_cross.setVisibility(View.INVISIBLE);
        cancel_cross.setOnClickListener((e) -> reset_searchbar());

        clipboard_layout = findViewById(R.id.clipboard_layout);
        clipboard_text = findViewById(R.id.clipboard_text);
        LinearLayout clipboard_text_layout = findViewById(R.id.clipboard_text_layout);
        clipboard_text_layout.setOnClickListener(v -> {
            if (clipboard_is_url) {
                new_tab_intent.setData(Uri.parse(clipboard_full_text.toString()));
                startActivity(new_tab_intent);
            } else {
                search_text.setText(clipboard_full_text);
                search_text.setSelection(search_text.getText().length());
                launch_search();
            }
        });

        search_text = findViewById(R.id.search_text);
        suggest_recyclerview = findViewById(R.id.suggest_recyclerview);
        suggest_adapter = new SuggestAdapter(this, suggestItems);
        suggest_recyclerview.setAdapter(suggest_adapter);

        // On keyboard validation (button "enter")
        search_text.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                launch_search();
                return true;
            }
            return false;
        });

        search_text.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void afterTextChanged(Editable s) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                String newSearch = s.toString().trim();
                if (newSearch.length() == 0) {
                    cancel_cross.setVisibility(View.INVISIBLE);
                    webview.setVisibility(View.INVISIBLE);
                    suggest_recyclerview.setVisibility(View.INVISIBLE);
                } else {
                    new RefreshSuggestionsTask(suggestItems, suggest_adapter).execute(newSearch);
                    cancel_cross.setVisibility(View.VISIBLE);
                    webview.setVisibility(View.INVISIBLE);
                    suggest_recyclerview.setVisibility(View.VISIBLE);
                }
            }
        });

        search_input_layout = findViewById(R.id.widget_search_bar_layout);
        search_text.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                if (search_text.getText() != null && search_text.getText().length() == 0) {
                    webview.setVisibility(View.INVISIBLE);
                    suggest_recyclerview.setVisibility(View.INVISIBLE);
                } else {
                    webview.setVisibility(View.INVISIBLE);
                    suggest_recyclerview.setVisibility(View.VISIBLE);
                }
            }
        });

        reset_searchbar();
    }

    public void updateSearchField(String text, boolean launchSearch) {
        search_text.setText(text);
        search_text.setSelection(text.length());
        if (launchSearch) launch_search();
    }

    private static class RefreshSuggestionsTask extends AsyncTask<String, ArrayList<String>, ArrayList<String>> {
        ArrayList<String> data;
        SuggestAdapter adapter;
        String search_terms;

        RefreshSuggestionsTask(ArrayList<String> suggest_data, SuggestAdapter adapter) {
            this.data = suggest_data;
            this.adapter = adapter;
        }

        protected ArrayList<String> doInBackground(String... search_strings) {
            search_terms = (search_strings.length > 0) ? search_strings[0] : "";
            return SuggestRequest.getSuggestions(search_terms);
        }

        protected void onPostExecute(ArrayList<String> result) {
            data.clear();
            data.addAll(result);
            adapter.notifyChange(search_terms);
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            clipboard.addPrimaryClipChangedListener(this::reload_clipboard);
            reload_clipboard();
        }
    }

    // Geoloc permission callback
    @Override public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == MIDORI_PERMISSIONS_REQUEST_FINE_LOCATION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                permission_request_callback.invoke(permission_request_origin, true, true);
            } else {
                permission_request_callback.invoke(permission_request_origin, false, false);
            }
        } else {
            permission_request_callback.invoke(permission_request_origin, false, false);
        }
    }

    // We reset the widget when user comes from a click on the homescreen widget
    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        reset_searchbar();
    }

    @Override public void onBackPressed() {
        if (suggest_recyclerview.getVisibility() == View.VISIBLE) {
            suggest_recyclerview.setVisibility(View.INVISIBLE);
            if (back_to_webview) {
                webview.setVisibility(View.VISIBLE);
            }
        } else if (webview.getVisibility() == View.VISIBLE) {
            webview.setVisibility(View.INVISIBLE);
            back_to_webview = false;
            reset_searchbar();
        } else {
            super.onBackPressed();
        }
    }

    void reset_searchbar() {
        search_text.setText("");
        search_text.requestFocus();
        InputMethodManager imm = (InputMethodManager) getSystemService(Activity.INPUT_METHOD_SERVICE);
        if (imm != null) imm.showSoftInput(search_text, 0);
    }

    void reload_clipboard() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        clipboard_is_url = false;
        if (clipboard.hasPrimaryClip() &&
        (clipboard.getPrimaryClipDescription().hasMimeType(MIMETYPE_TEXT_PLAIN) || clipboard.getPrimaryClipDescription().hasMimeType(MIMETYPE_TEXT_HTML))) {
            ClipData.Item item = clipboard.getPrimaryClip().getItemAt(0);
            String clipboard_text = null;
            Uri clipboard_uri = item.getUri();
            if (clipboard_uri != null) {
                clipboard_is_url = true;
                clipboard_text = clipboard_uri.toString();
            } else if (item.getText() != null) {
                clipboard_text = item.getText().toString().trim();
                try {
                    URL url = new URL(clipboard_text);
                    clipboard_is_url = true;
                } catch (MalformedURLException ignored) {}
            }

            if (clipboard_text != null && clipboard_text.length() > 0) {
                CharSequence display_text = (clipboard_text.length() > Assist.MAX_SUGGEST_TEXT_LENGTH) ?
                        clipboard_text.subSequence(0, Assist.MAX_SUGGEST_TEXT_LENGTH) : clipboard_text;
                this.clipboard_text.setText(display_text);
                this.clipboard_full_text = clipboard_text;
                this.clipboard_layout.setVisibility(View.VISIBLE);
                return ;
            }
        }
        // If we get there, no clipboard value is usable so we hide it
        this.clipboard_layout.setVisibility(View.GONE);
    }

    void launch_search() {
        this.launch_search(search_text.getText().toString());
    }

    public void launch_search(String query) {
        search_text.setText(query);
        if (query.length() > 0) {
            back_to_webview = true;
            webview.setVisibility(View.VISIBLE);

            // Get search url and load
            if (MidoriUseCases != null) {
                webview.loadUrl(MidoriUseCases.getGetMidoriUrl().invoke(null, query, null, true));
            }

            // Force hide keyboard
            InputMethodManager imm = (InputMethodManager) getSystemService(Activity.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(search_text.getWindowToken(), 0);
            search_text.clearFocus();
            webview.requestFocus();
        }
    }
}

