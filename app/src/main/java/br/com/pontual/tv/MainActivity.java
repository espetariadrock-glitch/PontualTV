package br.com.pontual.tv;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class MainActivity extends Activity {

    private WebView webView;
    private ScrollView setupLayout;
    private EditText etUrl;
    private EditText etServidor;
    private LinearLayout tvListLayout;
    private ScrollView tvListScroll;
    private SharedPreferences prefs;
    private final Handler handler = new Handler();

    // Para tratar fullscreen do WebView sem tela preta
    private View mFullscreenView;
    private WebChromeClient.CustomViewCallback mFullscreenCallback;
    private FrameLayout mContainer;

    private static final String PREFS_NAME = "pontual_tv_cfg";
    private static final String KEY_URL    = "tv_url";
    private static final String KEY_SERVER = "tv_server";

    private int backCount = 0;
    private final Runnable resetBack = () -> backCount = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().addFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN |
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON |
            WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD |
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        );

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        setContentView(R.layout.activity_main);

        mContainer    = findViewById(R.id.rootContainer);
        webView       = findViewById(R.id.webView);
        setupLayout   = findViewById(R.id.setupLayout);
        etUrl         = findViewById(R.id.etUrl);
        etServidor    = findViewById(R.id.etServidor);
        tvListLayout  = findViewById(R.id.tvListLayout);
        tvListScroll  = findViewById(R.id.tvListScroll);

        Button btnSalvar    = findViewById(R.id.btnSalvar);
        Button btnRedefinir = findViewById(R.id.btnRedefinir);
        Button btnBuscar    = findViewById(R.id.btnBuscar);

        configurarWebView();

        String servidorSalvo = prefs.getString(KEY_SERVER, "192.168.2.206");
        etServidor.setText(servidorSalvo);

        etServidor.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            public void onTextChanged(CharSequence s, int st, int b, int c) { tvListLayout.removeAllViews(); }
            public void afterTextChanged(Editable s) {}
        });

        btnBuscar.setOnClickListener(v -> buscarTVs());
        btnSalvar.setOnClickListener(v -> salvarEAbrir());
        btnRedefinir.setOnClickListener(v -> {
            prefs.edit().remove(KEY_URL).apply();
            webView.setVisibility(View.GONE);
            setupLayout.setVisibility(View.VISIBLE);
            etUrl.setText("");
            tvListLayout.removeAllViews();
            buscarTVs();
        });

        String urlSalva = prefs.getString(KEY_URL, "");
        if (!urlSalva.isEmpty()) {
            abrirWebView(urlSalva);
        } else {
            mostrarSetup();
            buscarTVs();
        }
    }

    // ── Busca TVs na API ──────────────────────────────────────────────────────
    private void buscarTVs() {
        tvListLayout.removeAllViews();
        TextView loading = new TextView(this);
        loading.setText("Buscando TVs disponíveis...");
        loading.setTextColor(Color.parseColor("#9ca3af"));
        loading.setTextSize(14);
        tvListLayout.addView(loading);

        String servidorInput = etServidor.getText().toString().trim();
        final String servidor = servidorInput.isEmpty() ? "192.168.2.206" : servidorInput;
        final String apiUrl = "http://" + servidor + "/api/tvs-lista";

        new Thread(() -> {
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL(apiUrl).openConnection();
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
                br.close();
                final JSONArray tvs = new JSONArray(sb.toString());
                runOnUiThread(() -> mostrarListaTVs(tvs, servidor));
            } catch (Exception e) {
                runOnUiThread(() -> {
                    tvListLayout.removeAllViews();
                    TextView err = new TextView(this);
                    err.setText("Não foi possível conectar ao servidor.\nDigite a URL manualmente abaixo.");
                    err.setTextColor(Color.parseColor("#ef4444"));
                    err.setTextSize(13);
                    tvListLayout.addView(err);
                });
            }
        }).start();
    }

    private void mostrarListaTVs(JSONArray tvs, String servidor) {
        tvListLayout.removeAllViews();
        if (tvs.length() == 0) {
            TextView vazio = new TextView(this);
            vazio.setText("Nenhuma TV cadastrada no servidor.");
            vazio.setTextColor(Color.parseColor("#9ca3af"));
            tvListLayout.addView(vazio);
            return;
        }

        TextView titulo = new TextView(this);
        titulo.setText("Selecione a TV deste dispositivo:");
        titulo.setTextColor(Color.parseColor("#e5e7eb"));
        titulo.setTextSize(16);
        titulo.setPadding(0, 0, 0, 16);
        tvListLayout.addView(titulo);

        for (int i = 0; i < tvs.length(); i++) {
            try {
                JSONObject tv = tvs.getJSONObject(i);
                String codigo = tv.getString("codigo");
                String nome   = tv.optString("nome", codigo);
                String loja   = tv.optString("loja", "");
                String setor  = tv.optString("setor", "");
                String label  = nome + (loja.isEmpty() ? "" : "  |  " + loja) + (setor.isEmpty() ? "" : " / " + setor);
                final String url = "http://" + servidor + "/tv/" + codigo;

                Button btn = new Button(this);
                btn.setText(label);
                btn.setTextColor(Color.WHITE);
                btn.setBackgroundColor(Color.parseColor("#1f2937"));
                btn.setAllCaps(false);
                btn.setTextSize(15);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                );
                lp.setMargins(0, 0, 0, 10);
                btn.setLayoutParams(lp);
                btn.setOnClickListener(v -> {
                    etUrl.setText(url);
                    Toast.makeText(this, "URL preenchida: " + url, Toast.LENGTH_SHORT).show();
                });
                tvListLayout.addView(btn);
            } catch (Exception ignored) {}
        }
    }

    // ── WebView ───────────────────────────────────────────────────────────────
    private void configurarWebView() {
        WebSettings ws = webView.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setMediaPlaybackRequiresUserGesture(false);
        ws.setCacheMode(WebSettings.LOAD_NO_CACHE);
        ws.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        ws.setBuiltInZoomControls(false);
        ws.setSupportZoom(false);
        // Sem useWideViewPort/loadWithOverviewMode para evitar barras pretas
        webView.setBackgroundColor(Color.BLACK);
        webView.setScrollBarStyle(View.SCROLLBARS_INSIDE_OVERLAY);

        // WebChromeClient trata fullscreen corretamente — evita tela preta
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                if (mFullscreenView != null) {
                    mFullscreenCallback.onCustomViewHidden();
                }
                mFullscreenView = view;
                mFullscreenCallback = callback;
                mContainer.addView(view, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                ));
                webView.setVisibility(View.GONE);
                mFullscreenView.setVisibility(View.VISIBLE);
            }

            @Override
            public void onHideCustomView() {
                if (mFullscreenView != null) {
                    mContainer.removeView(mFullscreenView);
                    mFullscreenView = null;
                }
                webView.setVisibility(View.VISIBLE);
            }
        });

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                // Dispara clique simulado para desbloquear autoplay de vídeo no Fire TV WebView
                view.evaluateJavascript(
                    "document.dispatchEvent(new MouseEvent('click',{bubbles:true,cancelable:true}));" +
                    "document.dispatchEvent(new TouchEvent('touchstart',{bubbles:true,cancelable:true}));",
                    null);
            }
            @Override
            public void onReceivedError(WebView view, WebResourceRequest req, WebResourceError err) {
                if (req.isForMainFrame()) {
                    handler.postDelayed(() -> {
                        String url = prefs.getString(KEY_URL, "");
                        if (!url.isEmpty()) webView.loadUrl(url);
                    }, 10_000);
                }
            }
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                view.loadUrl(req.getUrl().toString());
                return true;
            }
        });
    }

    // ── Setup ─────────────────────────────────────────────────────────────────
    private void mostrarSetup() {
        webView.setVisibility(View.GONE);
        setupLayout.setVisibility(View.VISIBLE);
        String atual = prefs.getString(KEY_URL, "");
        if (!atual.isEmpty()) etUrl.setText(atual);
    }

    private void salvarEAbrir() {
        String servidor = etServidor.getText().toString().trim();
        if (!servidor.isEmpty()) prefs.edit().putString(KEY_SERVER, servidor).apply();

        String url = etUrl.getText().toString().trim();
        if (url.isEmpty()) {
            Toast.makeText(this, "Selecione uma TV ou digite a URL", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) url = "http://" + url;
        prefs.edit().putString(KEY_URL, url).apply();
        abrirWebView(url);
    }

    private void abrirWebView(String url) {
        setupLayout.setVisibility(View.GONE);
        webView.setVisibility(View.VISIBLE);
        webView.loadUrl(url);
    }

    // ── Botão BACK ────────────────────────────────────────────────────────────
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            // Se fullscreen ativo, fecha fullscreen primeiro
            if (mFullscreenView != null) {
                if (mFullscreenCallback != null) mFullscreenCallback.onCustomViewHidden();
                return true;
            }
            if (setupLayout.getVisibility() == View.VISIBLE) {
                moveTaskToBack(true);
                return true;
            }
            backCount++;
            handler.removeCallbacks(resetBack);
            if (backCount >= 5) {
                backCount = 0;
                mostrarSetup();
                buscarTVs();
            } else {
                handler.postDelayed(resetBack, 3000);
            }
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onResume() {
        super.onResume();
        esconderBarraSistema();
        if (webView != null) webView.onResume();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) esconderBarraSistema();
    }

    private void esconderBarraSistema() {
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE     |
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
            View.SYSTEM_UI_FLAG_FULLSCREEN        |
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        );
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (webView != null) webView.onPause();
    }

    @Override
    protected void onDestroy() {
        if (webView != null) { webView.stopLoading(); webView.destroy(); }
        super.onDestroy();
    }
}
