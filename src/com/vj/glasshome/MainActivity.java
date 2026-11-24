package com.vj.glasshome;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.EditText;
import android.speech.RecognizerIntent;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Glass Home: a light launcher for Android TV / Google TV.
 * One screen, no content rows, no ads. All layout is built in code and sized
 * against a 1920x1080 design, so it looks the same on 1080p and 4K panels.
 */
public class MainActivity extends Activity {

    // ---- Things you may want to change -------------------------------------------------
    static final double WEATHER_LAT = 52.52, WEATHER_LON = 13.405;   // Berlin
    static final String WEATHER_PLACE = "Berlin";
    static final String WEATHER_TZ = "Europe/Berlin";
    static final String[] GOLD_URLS = {
            "https://www.goodreturns.in/gold-rates/chennai.html",
            "https://groww.in/gold-rates/gold-rate-today-in-chennai"
    };
    static final String[] GOLD_SOURCES = {"Goodreturns", "Groww"};
    static final long REFRESH_MS = 15 * 60 * 1000L;
    /** Nifty 50 members: Yahoo Finance symbol (without .NS), display name. */
    static final String[][] STOCKS = {
            {"ADANIENT", "Adani Enterprises"}, {"ADANIPORTS", "Adani Ports"}, {"APOLLOHOSP", "Apollo Hospitals"},
            {"ASIANPAINT", "Asian Paints"}, {"AXISBANK", "Axis Bank"}, {"BAJAJ-AUTO", "Bajaj Auto"},
            {"BAJFINANCE", "Bajaj Finance"}, {"BAJAJFINSV", "Bajaj Finserv"}, {"BEL", "Bharat Electronics"},
            {"BHARTIARTL", "Bharti Airtel"}, {"CIPLA", "Cipla"}, {"COALINDIA", "Coal India"},
            {"DRREDDY", "Dr Reddy's"}, {"EICHERMOT", "Eicher Motors"}, {"ETERNAL", "Eternal"},
            {"GRASIM", "Grasim Industries"}, {"HCLTECH", "HCL Tech"}, {"HDFCBANK", "HDFC Bank"},
            {"HDFCLIFE", "HDFC Life"}, {"HINDALCO", "Hindalco Industries"}, {"HINDUNILVR", "Hindustan Unilever"},
            {"ICICIBANK", "ICICI Bank"}, {"INDIGO", "InterGlobe Aviation"}, {"INFY", "Infosys"},
            {"ITC", "ITC"}, {"JIOFIN", "Jio Financial"}, {"JSWSTEEL", "JSW Steel"},
            {"KOTAKBANK", "Kotak Mahindra Bank"}, {"LT", "Larsen & Toubro"}, {"M&M", "Mahindra & Mahindra"},
            {"MARUTI", "Maruti Suzuki"}, {"MAXHEALTH", "Max Healthcare"}, {"NESTLEIND", "Nestle India"},
            {"NTPC", "NTPC"}, {"ONGC", "ONGC"}, {"POWERGRID", "Power Grid"},
            {"RELIANCE", "Reliance Industries"}, {"SBILIFE", "SBI Life"}, {"SBIN", "State Bank of India"},
            {"SHRIRAMFIN", "Shriram Finance"}, {"SUNPHARMA", "Sun Pharma"}, {"TATACONSUM", "Tata Consumer"},
            {"TMPV", "Tata Motors PV"}, {"TATASTEEL", "Tata Steel"}, {"TCS", "TCS"},
            {"TECHM", "Tech Mahindra"}, {"TITAN", "Titan"}, {"TRENT", "Trent"},
            {"ULTRACEMCO", "UltraTech Cement"}, {"WIPRO", "Wipro"}
    };
    // -----------------------------------------------------------------------------------

    static final int WHITE = 0xFFFFFFFF, SOFT = 0xFFE6E9F0, DIM = 0xFFC9CEDB, LOSS = 0xFFFFA59A;
    static final int GOLD = 0xFFF0C56A, SKY = 0xFF9FD0FF;
    static final String UA = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36";

    static class AppItem {
        String pkg, label;
        Drawable banner, icon;
        Intent launch;
    }

    static class Stock {
        String name;
        double price, change, pct;
    }

    float u = 1f;
    SharedPreferences prefs;
    final Handler ui = new Handler(Looper.getMainLooper());
    final ExecutorService io = Executors.newSingleThreadExecutor();
    final ExecutorService appIo = Executors.newSingleThreadExecutor();
    final ExecutorService stockLead = Executors.newSingleThreadExecutor();
    final ExecutorService stockIo = Executors.newFixedThreadPool(4);
    String goldErr = "page layout changed";
    final Map<String, AppItem> apps = new LinkedHashMap<String, AppItem>();
    final List<Stock> losers = new ArrayList<Stock>();
    String stockAsOf = "";
    long lastFetch = 0;
    boolean resumed = false;
    String appSignature = "";

    LinearLayout favRow, allRow, stockRows;
    HorizontalScrollView favScroll;
    LinearLayout calRows;
    TextView favHint, goldPrice, goldTen, goldEight, goldFoot, stockDate, stockFoot;
    TextView wHi, wLo, wCond, wSub, tHi, tLo, tCond, tSub, tTitle;
    LinearLayout hourRow, hourRow2;

    final Runnable ticker = new Runnable() {
        @Override public void run() {
            if (!resumed) return;
            refreshData();
            ui.postDelayed(this, REFRESH_MS);
        }
    };

    // ---- lifecycle --------------------------------------------------------------------

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("glasshome", MODE_PRIVATE);
        int w = getResources().getDisplayMetrics().widthPixels;
        int h = getResources().getDisplayMetrics().heightPixels;
        u = Math.min(w / 1920f, h / 1080f);
        if (u <= 0) u = 1f;
        buildUi();
        showCached();
        takeCalendarLink(getIntent());
    }

    @Override protected void onResume() {
        super.onResume();
        resumed = true;
        applyPendingScreensaver();
        loadApps();
        ui.removeCallbacks(ticker);
        long wait = lastFetch == 0 ? 0 : Math.max(0, REFRESH_MS - (System.currentTimeMillis() - lastFetch));
        ui.postDelayed(ticker, wait);
    }

    @Override protected void onPause() {
        super.onPause();
        resumed = false;
        ui.removeCallbacks(ticker);
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        io.shutdownNow();
        appIo.shutdownNow();
        stockLead.shutdownNow();
        stockIo.shutdownNow();
    }

    /** Home screen: Back does nothing. */
    @Override public void onBackPressed() { }

    // ---- small helpers ----------------------------------------------------------------

    int px(float v) { return Math.round(v * u); }

    TextView text(String s, float size, int color, String family) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, size * u);
        t.setTextColor(color);
        t.setTypeface(Typeface.create(family, Typeface.NORMAL));
        t.setIncludeFontPadding(false);
        return t;
    }

    TextView caps(String s, float size, int color) {
        TextView t = text(s, size, color, "sans-serif-medium");
        t.setAllCaps(true);
        t.setLetterSpacing(0.08f);
        return t;
    }

    GradientDrawable glass(float radius, int fill, int stroke, float strokeW) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(radius * u);
        if (strokeW > 0) g.setStroke(Math.max(1, px(strokeW)), stroke);
        return g;
    }

    void round(View v, final float radius) {
        v.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View view, Outline o) {
                o.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius * u);
            }
        });
        v.setClipToOutline(true);
    }

    LinearLayout.LayoutParams lp(int w, int h) { return new LinearLayout.LayoutParams(w, h); }

    static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT, WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;

    void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_LONG).show(); }

    // ---- wallpaper --------------------------------------------------------------------

    /** Drawn once at low resolution and stretched: soft by nature, no live blur needed. */
    Bitmap makeWallpaper() {
        int w = 480, h = 270;
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        c.drawColor(0xFF05050B);
        blob(c, w, h, 0.22f, 0.30f, 0.38f, 0.34f, 0xD9C43ACE);
        blob(c, w, h, 0.50f, 0.22f, 0.34f, 0.38f, 0xE63460EC);
        blob(c, w, h, 0.78f, 0.34f, 0.40f, 0.36f, 0xD99638DC);
        blob(c, w, h, 0.62f, 0.52f, 0.30f, 0.26f, 0x8C1E96E6);
        blob(c, w, h, 0.08f, 0.56f, 0.26f, 0.24f, 0x995A32C8);
        return bmp;
    }

    void blob(Canvas c, int w, int h, float cx, float cy, float rx, float ry, int color) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setDither(true);
        p.setShader(new RadialGradient(0, 0, 1f, color, color & 0x00FFFFFF, Shader.TileMode.CLAMP));
        c.save();
        c.translate(cx * w, cy * h);
        c.scale(rx * w, ry * h);
        c.drawCircle(0, 0, 1f, p);
        c.restore();
    }

    // ---- layout -----------------------------------------------------------------------

    void buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF05050B);

        ImageView wall = new ImageView(this);
        wall.setScaleType(ImageView.ScaleType.FIT_XY);
        wall.setImageBitmap(makeWallpaper());
        root.addView(wall, new FrameLayout.LayoutParams(MATCH, MATCH));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(px(80), px(44), px(80), px(30));
        col.setClipChildren(false);
        col.setClipToPadding(false);
        root.addView(col, new FrameLayout.LayoutParams(MATCH, MATCH));

        col.addView(buildTop(), lp(MATCH, px(300)));

        // Favourites shelf
        LinearLayout shelf = new LinearLayout(this);
        shelf.setOrientation(LinearLayout.VERTICAL);
        shelf.setBackground(glass(36, 0x17FFFFFF, 0x33FFFFFF, 1));
        shelf.setPadding(px(12), px(26), px(12), px(12));
        shelf.setClipChildren(false);
        shelf.setClipToPadding(false);
        TextView favTitle = text("Favourites", 22, SOFT, "sans-serif-medium");
        favTitle.setPadding(px(16), 0, 0, px(6));
        shelf.addView(favTitle, lp(WRAP, WRAP));
        FrameLayout favHolder = new FrameLayout(this);
        favHolder.setClipChildren(false);
        favScroll = new SnapScroll(this, px(289), 6);
        favRow = makeRow(favScroll);
        favHolder.addView(favScroll, new FrameLayout.LayoutParams(MATCH, WRAP));
        favHint = text("Hold OK on any app below to add it here", 22, SOFT, "sans-serif");
        favHint.setGravity(Gravity.CENTER);
        favHolder.addView(favHint, new FrameLayout.LayoutParams(MATCH, px(209)));
        shelf.addView(favHolder, lp(MATCH, WRAP));
        LinearLayout.LayoutParams sp = lp(MATCH, WRAP);
        sp.topMargin = px(28);
        col.addView(shelf, sp);

        // More apps
        LinearLayout more = new LinearLayout(this);
        more.setOrientation(LinearLayout.VERTICAL);
        more.setPadding(px(12), 0, px(12), 0);
        more.setClipChildren(false);
        more.setClipToPadding(false);
        TextView moreTitle = text("More apps", 22, SOFT, "sans-serif-medium");
        moreTitle.setPadding(px(16), 0, 0, px(6));
        more.addView(moreTitle, lp(WRAP, WRAP));
        HorizontalScrollView allScroll = new SnapScroll(this, px(289), 6);
        allRow = makeRow(allScroll);
        more.addView(allScroll, lp(MATCH, WRAP));
        LinearLayout.LayoutParams mp = lp(MATCH, WRAP);
        mp.topMargin = px(28);
        col.addView(more, mp);

        // Bottom bar
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER);
        bar.addView(pill("Add appointment", new Runnable() {
            @Override public void run() {
                if (calUrl().length() == 0) showCalSetup(); else startVoice();
            }
        }));
        bar.addView(pill("TV settings", new Runnable() {
            @Override public void run() { openSettings(Settings.ACTION_SETTINGS); }
        }));
        bar.addView(pill("Screensaver", new Runnable() {
            @Override public void run() { showScreensaverMenu(); }
        }));
        bar.addView(pill("Refresh data", new Runnable() {
            @Override public void run() { toast("Refreshing"); refreshData(); }
        }));
        LinearLayout.LayoutParams bp = lp(MATCH, px(48));
        bp.topMargin = px(14);
        col.addView(bar, bp);

        setContentView(root);
    }

    LinearLayout makeRow(HorizontalScrollView sv) {
        sv.setHorizontalScrollBarEnabled(false);
        sv.setClipToPadding(false);
        sv.setClipChildren(true);
        sv.setPadding(px(16), px(12), px(16), px(12));
        sv.setFocusable(false);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setClipChildren(false);
        row.setClipToPadding(false);
        sv.addView(row, new FrameLayout.LayoutParams(WRAP, WRAP));
        return row;
    }

    LinearLayout card(int padH, int padV, float radius) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(px(padH), px(padV), px(padH), px(padV));
        c.setBackground(glass(radius, 0x800A0A16, 0x33FFFFFF, 1));
        return c;
    }

    View spacer() {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(0, 0, 1f));
        return v;
    }

    LinearLayout pair(String left, TextView right) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        TextView l = text(left, 19, DIM, "sans-serif");
        r.addView(l, new LinearLayout.LayoutParams(0, WRAP, 1f));
        r.addView(right, lp(WRAP, WRAP));
        r.setPadding(0, px(3), 0, px(3));
        return r;
    }

    View buildTop() {
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setClipChildren(false);

        // Clock
        LinearLayout clock = new LinearLayout(this);
        clock.setOrientation(LinearLayout.VERTICAL);
        clock.setPadding(px(28), px(6), 0, 0);
        TextClock time = new TextClock(this);
        time.setFormat24Hour("HH:mm");
        time.setFormat12Hour("HH:mm");
        time.setTextSize(TypedValue.COMPLEX_UNIT_PX, 104 * u);
        time.setTextColor(WHITE);
        time.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        time.setIncludeFontPadding(false);
        time.setLetterSpacing(-0.02f);
        clock.addView(time, lp(WRAP, WRAP));
        TextClock date = new TextClock(this);
        date.setFormat24Hour("EEEE d MMMM");
        date.setFormat12Hour("EEEE d MMMM");
        date.setTextSize(TypedValue.COMPLEX_UNIT_PX, 20 * u);
        date.setTextColor(SOFT);
        date.setAllCaps(true);
        date.setLetterSpacing(0.06f);
        date.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        LinearLayout.LayoutParams dp = lp(WRAP, WRAP);
        dp.topMargin = px(10);
        clock.addView(date, dp);
        top.addView(clock, new LinearLayout.LayoutParams(0, MATCH, 1f));

        // Gold
        LinearLayout gold = card(24, 22, 28);
        gold.addView(caps("22K gold · Chennai", 17, GOLD));
        goldPrice = text("—", 52, WHITE, "sans-serif-medium");
        LinearLayout.LayoutParams gp = lp(WRAP, WRAP);
        gp.topMargin = px(14);
        gold.addView(goldPrice, gp);
        gold.addView(text("per gram", 20, SOFT, "sans-serif"));
        gold.addView(spacer());
        goldEight = text("—", 19, WHITE, "sans-serif-medium");
        goldTen = text("—", 19, WHITE, "sans-serif-medium");
        gold.addView(pair("8 grams", goldEight));
        gold.addView(pair("10 grams", goldTen));
        goldFoot = text("Loading", 15, DIM, "sans-serif");
        LinearLayout.LayoutParams gf = lp(WRAP, WRAP);
        gf.topMargin = px(6);
        gold.addView(goldFoot, gf);
        LinearLayout.LayoutParams gl = lp(px(280), MATCH);
        gl.rightMargin = px(20);
        top.addView(gold, gl);

        // Calendar
        final LinearLayout cal = card(24, 22, 28);
        LinearLayout calHead = new LinearLayout(this);
        calHead.addView(caps("Upcoming", 17, MINT), new LinearLayout.LayoutParams(0, WRAP, 1f));
        calHead.addView(text("Next 30 days", 15, DIM, "sans-serif"));
        cal.addView(calHead, lp(MATCH, WRAP));
        calRows = new LinearLayout(this);
        calRows.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams crp = lp(MATCH, WRAP);
        crp.topMargin = px(14);
        cal.addView(calRows, crp);
        cal.setFocusable(true);
        cal.setClickable(true);
        cal.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View v, boolean f) {
                cal.setBackground(f ? glass(28, 0xB30A0A16, 0xE6FFFFFF, 3) : glass(28, 0x800A0A16, 0x33FFFFFF, 1));
                cal.animate().scaleX(f ? 1.04f : 1f).scaleY(f ? 1.04f : 1f).setDuration(150).start();
            }
        });
        cal.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (calUrl().length() == 0) showCalSetup(); else showCalList();
            }
        });
        LinearLayout.LayoutParams sl = lp(px(400), MATCH);
        sl.rightMargin = px(20);
        top.addView(cal, sl);

        // Weather today
        LinearLayout w1 = card(20, 18, 24);
        w1.addView(caps(WEATHER_PLACE + " · today", 15, SKY));
        LinearLayout r1 = new LinearLayout(this);
        r1.setGravity(Gravity.BOTTOM);
        wHi = text("—", 52, WHITE, "sans-serif-medium");
        wLo = text("", 24, DIM, "sans-serif");
        wLo.setPadding(px(10), 0, 0, px(6));
        r1.addView(wHi);
        r1.addView(wLo);
        LinearLayout.LayoutParams r1p = lp(WRAP, WRAP);
        r1p.topMargin = px(10);
        w1.addView(r1, r1p);
        wCond = text("Loading", 17, SOFT, "sans-serif");
        wCond.setPadding(0, px(8), 0, px(2));
        w1.addView(wCond);
        wSub = text("", 16, DIM, "sans-serif");
        w1.addView(wSub);
        w1.addView(spacer());
        hourRow = new LinearLayout(this);
        hourRow.setOrientation(LinearLayout.HORIZONTAL);
        w1.addView(hourRow, lp(MATCH, WRAP));
        LinearLayout.LayoutParams w1p = lp(px(344), MATCH);
        w1p.rightMargin = px(16);
        top.addView(w1, w1p);

        // Weather tomorrow
        LinearLayout w2 = card(20, 18, 24);
        tTitle = caps("Tomorrow", 15, SKY);
        w2.addView(tTitle);
        LinearLayout r2 = new LinearLayout(this);
        r2.setGravity(Gravity.BOTTOM);
        tHi = text("—", 52, WHITE, "sans-serif-medium");
        tLo = text("", 24, DIM, "sans-serif");
        tLo.setPadding(px(10), 0, 0, px(6));
        r2.addView(tHi);
        r2.addView(tLo);
        LinearLayout.LayoutParams r2p = lp(WRAP, WRAP);
        r2p.topMargin = px(10);
        w2.addView(r2, r2p);
        tCond = text("Loading", 17, SOFT, "sans-serif");
        tCond.setPadding(0, px(8), 0, px(2));
        w2.addView(tCond);
        tSub = text("", 16, DIM, "sans-serif");
        w2.addView(tSub);
        w2.addView(spacer());
        hourRow2 = new LinearLayout(this);
        hourRow2.setOrientation(LinearLayout.HORIZONTAL);
        w2.addView(hourRow2, lp(MATCH, WRAP));
        top.addView(w2, lp(px(344), MATCH));

        return top;
    }

    TextView pill(String label, final Runnable action) {
        final TextView t = text(label, 18, SOFT, "sans-serif-medium");
        t.setGravity(Gravity.CENTER);
        t.setPadding(px(26), 0, px(26), 0);
        t.setBackground(glass(24, 0x1AFFFFFF, 0x33FFFFFF, 1));
        t.setFocusable(true);
        t.setClickable(true);
        t.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View v, boolean f) {
                t.setBackground(f ? glass(24, 0xE6FFFFFF, 0, 0) : glass(24, 0x1AFFFFFF, 0x33FFFFFF, 1));
                t.setTextColor(f ? 0xFF10131C : SOFT);
            }
        });
        t.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { action.run(); }
        });
        LinearLayout.LayoutParams p = lp(WRAP, px(46));
        p.leftMargin = px(6);
        p.rightMargin = px(6);
        t.setLayoutParams(p);
        return t;
    }

    void openSettings(String action) {
        try {
            startActivity(new Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception e) {
            try {
                startActivity(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (Exception e2) {
                toast("Could not open Settings");
            }
        }
    }

    // ---- apps -------------------------------------------------------------------------

    void loadApps() {
        appIo.execute(new Runnable() {
            @Override public void run() {
                try {
                    final Map<String, AppItem> found = queryApps();
                    StringBuilder sig = new StringBuilder();
                    for (String k : found.keySet()) sig.append(k).append(';');
                    final String s = sig.toString();
                    ui.post(new Runnable() {
                        @Override public void run() {
                            if (s.equals(appSignature) && favRow.getChildCount() + allRow.getChildCount() > 0) return;
                            appSignature = s;
                            apps.clear();
                            apps.putAll(found);
                            rebuildRows(null);
                        }
                    });
                } catch (Exception ignored) { }
            }
        });
    }

    Map<String, AppItem> queryApps() {
        PackageManager pm = getPackageManager();
        List<AppItem> list = new ArrayList<AppItem>();
        Map<String, Boolean> seen = new LinkedHashMap<String, Boolean>();
        String[] cats = {Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER};
        for (String cat : cats) {
            Intent q = new Intent(Intent.ACTION_MAIN).addCategory(cat);
            List<ResolveInfo> res = pm.queryIntentActivities(q, 0);
            if (res == null) continue;
            for (ResolveInfo r : res) {
                if (r.activityInfo == null) continue;
                String pkg = r.activityInfo.packageName;
                if (pkg == null || pkg.equals(getPackageName()) || seen.containsKey(pkg)) continue;
                seen.put(pkg, true);
                AppItem a = new AppItem();
                a.pkg = pkg;
                CharSequence l = r.loadLabel(pm);
                a.label = l == null ? pkg : l.toString();
                try { a.banner = r.activityInfo.loadBanner(pm); } catch (Exception ignored) { }
                if (a.banner == null) {
                    try { a.banner = pm.getApplicationBanner(r.activityInfo.applicationInfo); } catch (Exception ignored) { }
                }
                if (a.banner == null) {
                    try { a.icon = r.loadIcon(pm); } catch (Exception ignored) { }
                }
                a.launch = new Intent(Intent.ACTION_MAIN).addCategory(cat)
                        .setComponent(new ComponentName(pkg, r.activityInfo.name))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                list.add(a);
            }
        }
        Collections.sort(list, new Comparator<AppItem>() {
            @Override public int compare(AppItem x, AppItem y) { return x.label.compareToIgnoreCase(y.label); }
        });
        Map<String, AppItem> out = new LinkedHashMap<String, AppItem>();
        for (AppItem a : list) out.put(a.pkg, a);
        return out;
    }

    List<String> getFavs() {
        String s = prefs.getString("favs", "");
        List<String> out = new ArrayList<String>();
        if (s.length() > 0) out.addAll(Arrays.asList(s.split(",")));
        return out;
    }

    void saveFavs(List<String> favs) {
        prefs.edit().putString("favs", TextUtils.join(",", favs)).apply();
    }

    void rebuildRows(String focusPkg) {
        favRow.removeAllViews();
        allRow.removeAllViews();
        List<String> favs = getFavs();
        for (String p : favs) {
            AppItem a = apps.get(p);
            if (a != null) favRow.addView(makeTile(a));
        }
        for (AppItem a : apps.values()) {
            if (!favs.contains(a.pkg)) allRow.addView(makeTile(a));
        }
        // The last tile in the shelf opens the favourites picker, so the shelf is always reachable.
        boolean empty = favRow.getChildCount() == 0;
        favRow.addView(makeEditTile(empty));
        favHint.setVisibility(View.GONE);
        favScroll.setVisibility(View.VISIBLE);
        trimLastGap(favRow);
        trimLastGap(allRow);

        View target = null;
        if (focusPkg != null) {
            target = favRow.findViewWithTag(focusPkg);
            if (target == null) target = allRow.findViewWithTag(focusPkg);
        }
        if (target == null && favRow.getChildCount() > 0) target = favRow.getChildAt(0);
        if (target == null && allRow.getChildCount() > 0) target = allRow.getChildAt(0);
        if (target != null) target.requestFocus();
    }

    int tint(String key) {
        float hue = (key.hashCode() & 0x7fffffff) % 360;
        int c = Color.HSVToColor(new float[]{hue, 0.55f, 0.55f});
        return (c & 0x00FFFFFF) | 0xB0000000;
    }

    View makeTile(final AppItem a) {
        final LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        box.setClipChildren(false);
        box.setTag(a.pkg);
        box.setFocusable(true);
        box.setClickable(true);

        final FrameLayout frame = new FrameLayout(this);
        if (a.banner != null) {
            ImageView iv = new ImageView(this);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            iv.setImageDrawable(a.banner);
            frame.addView(iv, new FrameLayout.LayoutParams(MATCH, MATCH));
        } else {
            frame.setBackgroundColor(tint(a.pkg));
            if (a.icon != null) {
                ImageView iv = new ImageView(this);
                iv.setImageDrawable(a.icon);
                FrameLayout.LayoutParams ip = new FrameLayout.LayoutParams(px(84), px(84));
                ip.gravity = Gravity.CENTER;
                frame.addView(iv, ip);
            }
        }
        round(frame, 24);
        box.addView(frame, lp(px(257), px(145)));

        final TextView label = text(a.label, 19, SOFT, "sans-serif-medium");
        label.setSingleLine(true);
        label.setEllipsize(TextUtils.TruncateAt.END);
        label.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lpL = lp(px(257), px(26));
        lpL.topMargin = px(14);
        box.addView(label, lpL);

        box.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View v, boolean f) {
                frame.animate().scaleX(f ? 1.12f : 1f).scaleY(f ? 1.12f : 1f).setDuration(150).start();
                frame.setForeground(f ? glass(24, 0x00000000, 0xE6FFFFFF, 4) : null);
                frame.setTranslationZ(f ? px(12) : 0);
                label.setTextColor(f ? WHITE : SOFT);
            }
        });
        box.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                try {
                    startActivity(a.launch);
                } catch (Exception e) {
                    toast("Could not open " + a.label);
                }
            }
        });
        box.setOnLongClickListener(new View.OnLongClickListener() {
            @Override public boolean onLongClick(View v) { showAppMenu(a); return true; }
        });
        box.setOnKeyListener(new View.OnKeyListener() {
            @Override public boolean onKey(View v, int code, KeyEvent e) {
                if (code == KeyEvent.KEYCODE_MENU && e.getAction() == KeyEvent.ACTION_UP) {
                    showAppMenu(a);
                    return true;
                }
                return false;
            }
        });

        LinearLayout.LayoutParams p = lp(WRAP, WRAP);
        p.rightMargin = px(32);
        box.setLayoutParams(p);
        return box;
    }

    View makeEditTile(boolean empty) {
        final LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        box.setClipChildren(false);
        box.setFocusable(true);
        box.setClickable(true);

        final FrameLayout frame = new FrameLayout(this);
        frame.setBackground(glass(24, 0x26FFFFFF, 0x66FFFFFF, 1));
        TextView plus = text("+", 64, WHITE, "sans-serif-light");
        plus.setGravity(Gravity.CENTER);
        frame.addView(plus, new FrameLayout.LayoutParams(MATCH, MATCH));
        box.addView(frame, lp(px(257), px(145)));

        final TextView label = text(empty ? "Add favourites" : "Edit favourites", 19, SOFT, "sans-serif-medium");
        label.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lpL = lp(px(257), px(26));
        lpL.topMargin = px(14);
        box.addView(label, lpL);

        box.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View v, boolean f) {
                frame.animate().scaleX(f ? 1.12f : 1f).scaleY(f ? 1.12f : 1f).setDuration(150).start();
                frame.setBackground(f ? glass(24, 0x40FFFFFF, 0xE6FFFFFF, 4) : glass(24, 0x26FFFFFF, 0x66FFFFFF, 1));
                label.setTextColor(f ? WHITE : SOFT);
            }
        });
        box.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showFavPicker(); }
        });

        LinearLayout.LayoutParams p = lp(WRAP, WRAP);
        p.rightMargin = px(32);
        box.setLayoutParams(p);
        return box;
    }

    /** A tick list of every app; ticked apps become favourites, keeping their existing order. */
    void showFavPicker() {
        final List<AppItem> all = new ArrayList<AppItem>(apps.values());
        if (all.isEmpty()) {
            toast("No apps found yet");
            return;
        }
        final List<String> favs = getFavs();
        String[] names = new String[all.size()];
        final boolean[] on = new boolean[all.size()];
        for (int i = 0; i < all.size(); i++) {
            names[i] = all.get(i).label;
            on[i] = favs.contains(all.get(i).pkg);
        }
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Choose favourites")
                .setMultiChoiceItems(names, on, new DialogInterface.OnMultiChoiceClickListener() {
                    @Override public void onClick(DialogInterface d, int which, boolean checked) { on[which] = checked; }
                })
                .setPositiveButton("Done", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        List<String> out = new ArrayList<String>();
                        for (String p : favs) {
                            for (int i = 0; i < all.size(); i++) {
                                if (on[i] && all.get(i).pkg.equals(p)) out.add(p);
                            }
                        }
                        for (int i = 0; i < all.size(); i++) {
                            if (on[i] && !out.contains(all.get(i).pkg)) out.add(all.get(i).pkg);
                        }
                        saveFavs(out);
                        rebuildRows(out.isEmpty() ? null : out.get(0));
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    void showAppMenu(final AppItem a) {
        final List<String> favs = getFavs();
        final int idx = favs.indexOf(a.pkg);
        final List<String> items = new ArrayList<String>();
        if (idx >= 0) {
            items.add("Remove from favourites");
            if (idx > 0) items.add("Move left");
            if (idx < favs.size() - 1) items.add("Move right");
        } else {
            items.add("Add to favourites");
        }
        items.add("App info");
        items.add("Uninstall");
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(a.label)
                .setItems(items.toArray(new String[0]), new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        String pick = items.get(which);
                        if (pick.equals("Add to favourites")) {
                            favs.add(a.pkg);
                        } else if (pick.equals("Remove from favourites")) {
                            favs.remove(a.pkg);
                        } else if (pick.equals("Move left")) {
                            Collections.swap(favs, idx, idx - 1);
                        } else if (pick.equals("Move right")) {
                            Collections.swap(favs, idx, idx + 1);
                        } else if (pick.equals("App info")) {
                            try {
                                startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                        Uri.parse("package:" + a.pkg)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                            } catch (Exception e) { toast("Could not open app info"); }
                            return;
                        } else {
                            try {
                                startActivity(new Intent(Intent.ACTION_DELETE,
                                        Uri.parse("package:" + a.pkg)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                            } catch (Exception e) { toast("Could not start uninstall"); }
                            return;
                        }
                        saveFavs(favs);
                        rebuildRows(a.pkg);
                    }
                })
                .show();
    }

    // ---- data: fetch ------------------------------------------------------------------

    void refreshData() {
        lastFetch = System.currentTimeMillis();
        io.execute(new Runnable() { @Override public void run() { fetchWeather(); } });
        io.execute(new Runnable() { @Override public void run() { fetchGold(); } });
        io.execute(new Runnable() { @Override public void run() { fetchCalendar(); } });
    }

    String http(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        try {
            c.setConnectTimeout(8000);
            c.setReadTimeout(12000);
            c.setRequestProperty("User-Agent", UA);
            c.setRequestProperty("Accept", "text/html,application/json;q=0.9,*/*;q=0.8");
            c.setRequestProperty("Accept-Language", "en-GB,en;q=0.9");
            int code = c.getResponseCode();
            if (code < 200 || code >= 300) throw new Exception("HTTP " + code);
            InputStream in = c.getInputStream();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n, total = 0;
            while ((n = in.read(buf)) > 0 && total < 4000000) {
                out.write(buf, 0, n);
                total += n;
            }
            in.close();
            return out.toString("UTF-8");
        } finally {
            c.disconnect();
        }
    }

    String now() {
        return new SimpleDateFormat("HH:mm", Locale.UK).format(new Date());
    }

    void fetchWeather() {
        try {
            String url = "https://api.open-meteo.com/v1/forecast?latitude=" + WEATHER_LAT + "&longitude=" + WEATHER_LON
                    + "&current=temperature_2m,weather_code"
                    + "&hourly=temperature_2m,precipitation_probability"
                    + "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max"
                    + "&timezone=" + URLEncoder.encode(WEATHER_TZ, "UTF-8") + "&forecast_days=2";
            JSONObject j = new JSONObject(http(url));
            JSONObject cur = j.getJSONObject("current");
            JSONObject d = j.getJSONObject("daily");
            JSONArray hi = d.getJSONArray("temperature_2m_max"), lo = d.getJSONArray("temperature_2m_min");
            JSONArray code = d.getJSONArray("weather_code"), rain = d.getJSONArray("precipitation_probability_max");
            JSONArray days = d.getJSONArray("time");
            // Today and tomorrow in eight three-hour steps each: hour, temperature, chance of rain.
            StringBuilder hours = new StringBuilder(), hours2 = new StringBuilder();
            try {
                JSONObject hr = j.getJSONObject("hourly");
                JSONArray ht = hr.getJSONArray("temperature_2m"), hp = hr.getJSONArray("precipitation_probability");
                for (int i = 0; i < 48 && i < ht.length(); i += 3) {
                    StringBuilder sb = i < 24 ? hours : hours2;
                    int h = i % 24;
                    if (sb.length() > 0) sb.append(';');
                    sb.append(h < 10 ? "0" : "").append(h).append(',').append(deg(ht.getDouble(i))).append(',').append(hp.optInt(i, 0)).append('%');
                }
            } catch (Exception ignored) { }
            String tomorrow = "Tomorrow";
            try {
                Date dt = new SimpleDateFormat("yyyy-MM-dd", Locale.UK).parse(days.getString(1));
                tomorrow = new SimpleDateFormat("EEEE", Locale.UK).format(dt);
            } catch (Exception ignored) { }
            final String s = deg(hi.getDouble(0)) + "|" + deg(lo.getDouble(0)) + "|" + wmo(cur.optInt("weather_code", code.getInt(0)))
                    + "|Now " + deg(cur.getDouble("temperature_2m")) + " · rain " + rain.optInt(0, 0) + "%"
                    + "|" + deg(hi.getDouble(1)) + "|" + deg(lo.getDouble(1)) + "|" + wmo(code.getInt(1))
                    + "|" + tomorrow + " · rain " + rain.optInt(1, 0) + "%" + "|" + hours + "|" + hours2;
            prefs.edit().putString("weather", s).apply();
            ui.post(new Runnable() { @Override public void run() { showWeather(s); } });
        } catch (Exception e) {
            final String reason = why(e);
            ui.post(new Runnable() { @Override public void run() {
                if (prefs.getString("weather", "").length() == 0) { wCond.setText("No data"); wSub.setText(reason); tCond.setText("No data"); }
            } });
        }
    }

    static String deg(double v) {
        long r = Math.round(v);
        return (r == 0 ? 0 : r) + "°";
    }

    static String wmo(int c) {
        if (c == 0) return "Clear";
        if (c == 1) return "Mostly clear";
        if (c == 2) return "Partly cloudy";
        if (c == 3) return "Cloudy";
        if (c == 45 || c == 48) return "Fog";
        if (c >= 51 && c <= 57) return "Drizzle";
        if (c >= 61 && c <= 67) return "Rain";
        if (c >= 71 && c <= 77) return "Snow";
        if (c >= 80 && c <= 82) return "Showers";
        if (c == 85 || c == 86) return "Snow showers";
        if (c >= 95) return "Thunderstorm";
        return "Cloudy";
    }

    void fetchGold() {
        Pattern a = Pattern.compile("([0-9][0-9,]{3,8})(?:\\.[0-9]+)?\\s*per gram for 22\\s*(?:karat|carat|k)", Pattern.CASE_INSENSITIVE);
        Pattern b = Pattern.compile("22\\s*(?:k|karat|carat)\\s*gold\\s*/\\s*g\\D{0,12}([0-9][0-9,]{3,8})", Pattern.CASE_INSENSITIVE);
        for (int i = 0; i < GOLD_URLS.length; i++) {
            try {
                String t = http(GOLD_URLS[i]).replaceAll("<[^>]+>", " ").replace("&nbsp;", " ").replaceAll("\\s+", " ");
                long v = 0;
                Matcher m = a.matcher(t);
                if (m.find()) v = Long.parseLong(m.group(1).replace(",", ""));
                if (v == 0) {
                    m = b.matcher(t);
                    if (m.find()) v = Long.parseLong(m.group(1).replace(",", ""));
                }
                if (v >= 3000 && v <= 60000) {
                    final String s = v + "|" + new SimpleDateFormat("d MMM HH:mm", Locale.UK).format(new Date()) + " · " + GOLD_SOURCES[i];
                    prefs.edit().putString("gold", s).apply();
                    ui.post(new Runnable() { @Override public void run() { showGold(s); } });
                    return;
                }
            } catch (Exception e) {
                goldErr = why(e);
            }
        }
        ui.post(new Runnable() { @Override public void run() {
            if (prefs.getString("gold", "").length() == 0) goldFoot.setText("No data: " + goldErr);
        } });
    }

    /** A short reason for a failed download, shown in the widget so problems can be reported. */
    static String why(Exception e) {
        String m = e.getMessage();
        String s = e.getClass().getSimpleName() + (m == null ? "" : " " + m);
        return s.length() > 44 ? s.substring(0, 44) : s;
    }

    JSONObject stockMeta(String symbol) throws Exception {
        String path = "/v8/finance/chart/" + URLEncoder.encode(symbol + ".NS", "UTF-8") + "?range=1d&interval=1d";
        String body;
        try {
            body = http("https://query1.finance.yahoo.com" + path);
        } catch (Exception first) {
            body = http("https://query2.finance.yahoo.com" + path);
        }
        return new JSONObject(body).getJSONObject("chart").getJSONArray("result").getJSONObject(0).getJSONObject("meta");
    }

    void fetchStocks() {
        final List<Stock> out = new ArrayList<Stock>();
        final long[] latestBox = {0};
        final int[] okBox = {0};
        final String[] errBox = {"no answer"};
        List<java.util.concurrent.Callable<Void>> jobs = new ArrayList<java.util.concurrent.Callable<Void>>();
        for (final String[] s : STOCKS) {
            jobs.add(new java.util.concurrent.Callable<Void>() {
                @Override public Void call() {
                    try {
                        JSONObject meta = stockMeta(s[0]);
                        double price = meta.getDouble("regularMarketPrice");
                        double prev = meta.has("previousClose") ? meta.getDouble("previousClose") : meta.getDouble("chartPreviousClose");
                        if (prev <= 0) return null;
                        synchronized (out) {
                            okBox[0]++;
                            latestBox[0] = Math.max(latestBox[0], meta.optLong("regularMarketTime", 0));
                            if (price < prev) {
                                Stock st = new Stock();
                                st.name = s[1];
                                st.price = price;
                                st.change = price - prev;
                                st.pct = (prev - price) / prev * 100.0;
                                out.add(st);
                            }
                        }
                    } catch (Exception e) {
                        errBox[0] = why(e);
                    }
                    return null;
                }
            });
        }
        try {
            stockIo.invokeAll(jobs);
        } catch (Exception e) {
            return;
        }
        final long latest = latestBox[0];
        final int ok = okBox[0];
        if (ok == 0) {
            final String reason = errBox[0];
            ui.post(new Runnable() { @Override public void run() {
                if (losers.isEmpty()) stockFoot.setText("No data: " + reason);
            } });
            return;
        }
        Collections.sort(out, new Comparator<Stock>() {
            @Override public int compare(Stock x, Stock y) { return Double.compare(y.pct, x.pct); }
        });
        String asOf = "";
        if (latest > 0) {
            SimpleDateFormat f = new SimpleDateFormat("d MMM HH:mm", Locale.UK);
            f.setTimeZone(TimeZone.getTimeZone("Asia/Kolkata"));
            asOf = f.format(new Date(latest * 1000L)) + " IST";
        }
        StringBuilder sb = new StringBuilder(asOf).append('\n').append(ok);
        for (Stock st : out) sb.append('\n').append(st.name).append('|').append(st.price).append('|').append(st.change).append('|').append(st.pct);
        final String packed = sb.toString();
        prefs.edit().putString("stocks", packed).apply();
        ui.post(new Runnable() { @Override public void run() { showStocks(packed); } });
    }

    // ---- data: show -------------------------------------------------------------------

    // ---- calendar ---------------------------------------------------------------------

    static final int REQ_VOICE = 41;
    static final int MINT = 0xFFA8F0C6;

    interface TextCb { void got(String s); }

    String calUrl() { return prefs.getString("cal_url", "").trim(); }

    /** The link can also be set from a computer: adb shell am start -n com.vj.glasshome/.MainActivity --es calendar_url '...' */
    void takeCalendarLink(Intent i) {
        if (i == null) return;
        String u = i.getStringExtra("calendar_url");
        if (u != null && u.trim().startsWith("https://")) {
            prefs.edit().putString("cal_url", u.trim()).apply();
            toast("Calendar link saved");
            io.execute(new Runnable() { @Override public void run() { fetchCalendar(); } });
        }
    }

    @Override protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        takeCalendarLink(i);
    }

    String calCall(String extra) throws Exception {
        String base = calUrl();
        return http(base + (base.contains("?") ? "&" : "?") + extra);
    }

    void fetchCalendar() {
        if (calUrl().length() == 0) {
            ui.post(new Runnable() { @Override public void run() { showCalendar(""); } });
            return;
        }
        try {
            JSONObject j = new JSONObject(calCall("list=1"));
            if (j.has("error")) throw new Exception(j.getString("error"));
            final String packed = j.getJSONArray("events").toString();
            prefs.edit().putString("cal", packed).apply();
            ui.post(new Runnable() { @Override public void run() { showCalendar(packed); } });
        } catch (Exception e) {
            final String reason = why(e);
            ui.post(new Runnable() { @Override public void run() {
                if (prefs.getString("cal", "").length() == 0) {
                    calRows.removeAllViews();
                    calRows.addView(text("No data: " + reason, 15, DIM, "sans-serif"));
                }
            } });
        }
    }

    String calWhen(JSONObject ev) {
        Date d = new Date(ev.optLong("start", 0));
        String day = new SimpleDateFormat("EEE d MMM", Locale.UK).format(d);
        if (ev.optBoolean("allDay", false)) return day + " · all day";
        return day + " · " + new SimpleDateFormat("HH:mm", Locale.UK).format(d);
    }

    void showCalendar(String packed) {
        calRows.removeAllViews();
        if (calUrl().length() == 0) {
            calRows.addView(text("Press OK to connect your Google Calendar", 15, SOFT, "sans-serif"));
            return;
        }
        try {
            JSONArray evs = new JSONArray(packed.length() == 0 ? "[]" : packed);
            if (evs.length() == 0) {
                calRows.addView(text("Nothing in the next 30 days", 15, SOFT, "sans-serif"));
                return;
            }
            for (int i = 0; i < Math.min(4, evs.length()); i++) {
                JSONObject ev = evs.getJSONObject(i);
                TextView when = text(calWhen(ev), 16, MINT, "sans-serif-medium");
                TextView what = text(ev.optString("title", "(no title)"), 20, WHITE, "sans-serif");
                what.setSingleLine(true);
                what.setEllipsize(TextUtils.TruncateAt.END);
                what.setPadding(0, px(3), 0, px(10));
                calRows.addView(when);
                calRows.addView(what, lp(MATCH, WRAP));
            }
        } catch (Exception ignored) { }
    }

    void askText(String title, String hint, String initial, final TextCb cb) {
        final EditText et = new EditText(this);
        et.setSingleLine(true);
        et.setHint(hint);
        et.setText(initial);
        et.setSelection(et.getText().length());
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(title)
                .setView(et)
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        String s = et.getText().toString().trim();
                        if (s.length() > 0) cb.got(s);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
        et.requestFocus();
    }

    void showCalSetup() {
        askText("Calendar link", "Your Apps Script web app link, ending in ?key=...", calUrl(), new TextCb() {
            @Override public void got(String s) {
                if (!s.startsWith("https://")) { toast("The link must start with https://"); return; }
                prefs.edit().putString("cal_url", s).remove("cal").apply();
                showCalendar("");
                io.execute(new Runnable() { @Override public void run() { fetchCalendar(); } });
            }
        });
    }

    void startVoice() {
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(RecognizerIntent.EXTRA_PROMPT, "Say the appointment, for example: Dentist on Friday at 3 pm");
        try {
            startActivityForResult(i, REQ_VOICE);
        } catch (Exception e) {
            confirmAdd("");
        }
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req != REQ_VOICE || res != RESULT_OK || data == null) return;
        ArrayList<String> said = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
        if (said != null && !said.isEmpty()) confirmAdd(said.get(0));
    }

    /** Shows what was heard so it can be corrected, then adds it with Google Calendar's Quick Add. */
    void confirmAdd(String heard) {
        askText("Add appointment", "Dentist on Friday at 3 pm", heard, new TextCb() {
            @Override public void got(final String s) {
                toast("Adding…");
                io.execute(new Runnable() { @Override public void run() { addEvent(s); } });
            }
        });
    }

    void addEvent(String s) {
        String msg;
        try {
            JSONObject j = new JSONObject(calCall("add=" + URLEncoder.encode(s, "UTF-8")));
            if (j.has("error")) throw new Exception(j.getString("error"));
            JSONObject ev = new JSONObject();
            ev.put("start", j.optLong("start", 0));
            ev.put("allDay", j.optBoolean("allDay", false));
            msg = "Added: " + j.optString("added", s) + ", " + calWhen(ev);
        } catch (Exception e) {
            msg = "Could not add it: " + why(e);
        }
        final String m = msg;
        ui.post(new Runnable() { @Override public void run() { toast(m); } });
        fetchCalendar();
    }

    void showCalList() {
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(px(40), px(8), px(40), px(24));
        try {
            JSONArray evs = new JSONArray(prefs.getString("cal", "[]"));
            if (evs.length() == 0) list.addView(text("No upcoming appointments", 21, SOFT, "sans-serif"));
            for (int i = 0; i < evs.length(); i++) {
                JSONObject ev = evs.getJSONObject(i);
                LinearLayout r = new LinearLayout(this);
                r.setGravity(Gravity.CENTER_VERTICAL);
                r.addView(text(calWhen(ev), 21, MINT, "sans-serif-medium"), lp(px(300), WRAP));
                TextView t = text(ev.optString("title", "(no title)"), 22, WHITE, "sans-serif");
                t.setSingleLine(true);
                t.setEllipsize(TextUtils.TruncateAt.END);
                r.addView(t, new LinearLayout.LayoutParams(0, WRAP, 1f));
                list.addView(r, lp(MATCH, px(52)));
            }
        } catch (Exception ignored) { }
        ScrollView sv = new ScrollView(this);
        sv.setFocusable(true);
        sv.addView(list, new FrameLayout.LayoutParams(px(900), WRAP));
        AlertDialog d = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Upcoming appointments")
                .setView(sv)
                .setPositiveButton("Add by voice", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface di, int w) { startVoice(); }
                })
                .setNeutralButton("Change link", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface di, int w) { showCalSetup(); }
                })
                .setNegativeButton("Close", null)
                .create();
        d.show();
        if (d.getWindow() != null) d.getWindow().setLayout(px(980), px(860));
    }

    // ---- screensaver start time -------------------------------------------------------

    static final int[] SS_MIN = {1, 2, 5, 10, 15, 30};

    static String mins(int m) { return m + (m == 1 ? " minute" : " minutes"); }

    void showScreensaverMenu() {
        int cur = -1;
        try { cur = Settings.System.getInt(getContentResolver(), Settings.System.SCREEN_OFF_TIMEOUT) / 60000; } catch (Exception ignored) { }
        final String[] items = new String[SS_MIN.length + 1];
        for (int i = 0; i < SS_MIN.length; i++) {
            items[i] = "Start after " + mins(SS_MIN[i]) + (SS_MIN[i] == cur ? "   ✓ current" : "");
        }
        items[SS_MIN.length] = "Choose photos (Ambient mode settings)";
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Screensaver")
                .setItems(items, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        if (w == SS_MIN.length) openScreensaver(); else setScreensaverDelay(SS_MIN[w]);
                    }
                })
                .show();
    }

    /** The screensaver starts after the system's screen timeout; changing it needs "Modify system settings". */
    void setScreensaverDelay(int minutes) {
        if (Settings.System.canWrite(this)) {
            try {
                Settings.System.putInt(getContentResolver(), Settings.System.SCREEN_OFF_TIMEOUT, minutes * 60000);
                prefs.edit().putInt("ss_min", minutes).remove("ss_pending").apply();
                toast("Screensaver starts after " + mins(minutes));
            } catch (Exception e) {
                toast("Could not change it: " + why(e));
            }
            return;
        }
        prefs.edit().putInt("ss_pending", minutes).apply();
        toast("Turn on \"Allow modifying system settings\" for Glass Home, then press Back");
        try {
            startActivity(new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:" + getPackageName()))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception e) {
            toast("This TV has no page for that permission");
        }
    }

    /** Applies a choice waiting for permission, or the 1-minute default the first time permission exists. */
    void applyPendingScreensaver() {
        int pending = prefs.getInt("ss_pending", 0);
        if (pending == 0 && !prefs.contains("ss_min")) pending = 1;
        if (pending > 0 && Settings.System.canWrite(this)) setScreensaverDelay(pending);
    }

    void openScreensaver() {
        Intent target = findDreamSettings();
        if (target != null) {
            try {
                toast("Choose Google Photos and your albums");
                startActivity(target);
                return;
            } catch (Exception ignored) { }
        }
        toast("Go to System → Ambient mode → Google Photos");
        openSettings(Settings.ACTION_SETTINGS);
    }

    /** Finds the settings page of the TV's screensaver (Ambient mode) from the screensaver's own description. */
    Intent findDreamSettings() {
        PackageManager pm = getPackageManager();
        String current = null;
        try { current = Settings.Secure.getString(getContentResolver(), "screensaver_components"); } catch (Exception ignored) { }
        List<ResolveInfo> dreams;
        try {
            dreams = pm.queryIntentServices(new Intent("android.service.dreams.DreamService"), PackageManager.GET_META_DATA);
        } catch (Exception e) {
            return null;
        }
        if (dreams == null) return null;
        Intent best = null;
        int bestScore = -1;
        for (ResolveInfo r : dreams) {
            android.content.pm.ServiceInfo si = r.serviceInfo;
            if (si == null) continue;
            String act = dreamSettingsActivity(pm, si);
            if (act == null || act.length() == 0 || act.startsWith("@")) continue;
            ComponentName cn;
            if (act.contains("/")) cn = ComponentName.unflattenFromString(act);
            else if (act.startsWith(".")) cn = new ComponentName(si.packageName, si.packageName + act);
            else cn = new ComponentName(si.packageName, act);
            if (cn == null) continue;
            int score = 0;
            if (si.packageName.startsWith("com.google")) score += 1;
            if (current != null && current.contains(si.packageName)) score += 2;
            if (score > bestScore) {
                bestScore = score;
                best = new Intent().setComponent(cn).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            }
        }
        return best;
    }

    String dreamSettingsActivity(PackageManager pm, android.content.pm.ServiceInfo si) {
        android.content.res.XmlResourceParser p = null;
        try {
            p = si.loadXmlMetaData(pm, "android.service.dream");
            if (p == null) return null;
            int t;
            while ((t = p.next()) != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                if (t == org.xmlpull.v1.XmlPullParser.START_TAG) {
                    return p.getAttributeValue("http://schemas.android.com/apk/res/android", "settingsActivity");
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (p != null) p.close();
        }
        return null;
    }

    void showCached() {
        String w = prefs.getString("weather", "");
        if (w.length() > 0) showWeather(w);
        String g = prefs.getString("gold", "");
        if (g.length() > 0) showGold(g);
        String s = prefs.getString("stocks", "");
        showCalendar(prefs.getString("cal", ""));
    }

    void showWeather(String s) {
        try {
            String[] p = s.split("\\|");
            wHi.setText(p[0]); wLo.setText(p[1]); wCond.setText(p[2]); wSub.setText(p[3]);
            tHi.setText(p[4]); tLo.setText(p[5]); tCond.setText(p[6]); tSub.setText(p[7]);
            int nowSlot = java.util.Calendar.getInstance(TimeZone.getTimeZone(WEATHER_TZ)).get(java.util.Calendar.HOUR_OF_DAY) / 3;
            fillHours(hourRow, p.length > 8 ? p[8] : "", nowSlot);
            fillHours(hourRow2, p.length > 9 ? p[9] : "", -1);
        } catch (Exception ignored) { }
    }

    /** One day's strip: eight columns of hour, temperature and chance of rain. */
    void fillHours(LinearLayout row, String data, int nowSlot) {
        row.removeAllViews();
        if (data.length() == 0) return;
        String[] slots = data.split(";");
        for (int i = 0; i < slots.length; i++) {
            String[] f = slots[i].split(",");
            if (f.length < 3) continue;
            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setGravity(Gravity.CENTER_HORIZONTAL);
            col.setPadding(0, px(8), 0, px(8));
            if (i == nowSlot) col.setBackground(glass(14, 0x2EFFFFFF, 0, 0));
            TextView hh = text(f[0], 15, DIM, "sans-serif");
            TextView tt = text(f[1], 19, WHITE, "sans-serif-medium");
            tt.setPadding(0, px(6), 0, px(6));
            TextView rr = text(f[2], 13, SKY, "sans-serif");
            col.addView(hh);
            col.addView(tt);
            col.addView(rr);
            row.addView(col, new LinearLayout.LayoutParams(0, WRAP, 1f));
        }
    }

    /** Drop the gap after a row's last tile, so a full row lines up with the shelf edge. */
    void trimLastGap(LinearLayout row) {
        int n = row.getChildCount();
        if (n == 0) return;
        View last = row.getChildAt(n - 1);
        LinearLayout.LayoutParams p = (LinearLayout.LayoutParams) last.getLayoutParams();
        p.rightMargin = 0;
        last.setLayoutParams(p);
    }

    /** A tile row that scrolls a whole tile at a time, so tiles stay on a fixed grid. */
    static class SnapScroll extends HorizontalScrollView {
        final int step, cols;

        SnapScroll(android.content.Context c, int step, int cols) {
            super(c);
            this.step = step;
            this.cols = cols;
        }

        @Override protected int computeScrollDeltaToGetChildRectOnScreen(android.graphics.Rect r) {
            if (step <= 0 || getChildCount() == 0) return super.computeScrollDeltaToGetChildRectOnScreen(r);
            int idx = Math.max(0, Math.round((r.left - getPaddingLeft()) / (float) step));
            int first = Math.round(getScrollX() / (float) step);
            if (idx < first) first = idx;
            else if (idx > first + cols - 1) first = idx - cols + 1;
            int max = Math.max(0, getChildAt(0).getWidth() + getPaddingLeft() + getPaddingRight() - getWidth());
            return Math.min(first * step, max) - getScrollX();
        }
    }

    static String inr(long v) {
        String s = String.valueOf(Math.abs(v));
        if (s.length() > 3) {
            String last = s.substring(s.length() - 3), rest = s.substring(0, s.length() - 3);
            StringBuilder sb = new StringBuilder();
            while (rest.length() > 2) {
                sb.insert(0, "," + rest.substring(rest.length() - 2));
                rest = rest.substring(0, rest.length() - 2);
            }
            s = rest + sb + "," + last;
        }
        return "₹" + s;
    }

    static String inr2(double v) {
        long total = Math.round(Math.abs(v) * 100.0);
        long paise = total % 100;
        return inr(total / 100) + "." + (paise < 10 ? "0" : "") + paise;
    }

    void showGold(String s) {
        try {
            String[] p = s.split("\\|");
            long v = Long.parseLong(p[0]);
            goldPrice.setText(inr(v));
            goldEight.setText(inr(v * 8));
            goldTen.setText(inr(v * 10));
            goldFoot.setText(p.length > 1 ? p[1] : "");
        } catch (Exception ignored) { }
    }

    void showStocks(String packed) {
        try {
            String[] lines = packed.split("\n");
            stockAsOf = lines[0];
            losers.clear();
            for (int i = 2; i < lines.length; i++) {
                String[] p = lines[i].split("\\|");
                Stock st = new Stock();
                st.name = p[0];
                st.price = Double.parseDouble(p[1]);
                st.change = Double.parseDouble(p[2]);
                st.pct = Double.parseDouble(p[3]);
                losers.add(st);
            }
            stockDate.setText(stockAsOf);
            stockRows.removeAllViews();
            // Biggest percentage loss first; the widget shows the top five.
            Collections.sort(losers, new Comparator<Stock>() {
                @Override public int compare(Stock x, Stock y) { return Double.compare(y.pct, x.pct); }
            });
            for (int i = 0; i < Math.min(5, losers.size()); i++) stockRows.addView(stockRow(losers.get(i), 20, 34, false));
            if (losers.isEmpty()) {
                stockFoot.setText("No Nifty 50 stock is down");
            } else {
                stockFoot.setText(losers.size() + " of " + lines[1] + " down · press OK for the full list");
            }
        } catch (Exception ignored) { }
    }

    View stockRow(Stock st, float size, float height, boolean wide) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        TextView n = text(st.name, size, WHITE, "sans-serif");
        n.setSingleLine(true);
        n.setEllipsize(TextUtils.TruncateAt.END);
        r.addView(n, new LinearLayout.LayoutParams(0, WRAP, 1f));
        TextView p = text(inr2(st.price), size, DIM, "sans-serif");
        p.setGravity(Gravity.END);
        r.addView(p, lp(px(wide ? 170 : 108), WRAP));
        if (wide) {
            TextView c = text("−" + inr2(st.change), size, LOSS, "sans-serif");
            c.setGravity(Gravity.END);
            r.addView(c, lp(px(150), WRAP));
        }
        TextView pc = text("−" + String.format(Locale.UK, "%.2f", st.pct) + "%", size, LOSS, "sans-serif-medium");
        pc.setGravity(Gravity.END);
        r.addView(pc, lp(px(wide ? 130 : 84), WRAP));
        r.setLayoutParams(lp(MATCH, px(height)));
        return r;
    }

    void showStockList() {
        if (losers.isEmpty()) {
            toast("No stock data yet");
            return;
        }
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(px(40), px(8), px(40), px(24));
        TextView sub = text(stockAsOf + " · " + losers.size() + " stocks down · biggest loss first", 19, DIM, "sans-serif");
        sub.setPadding(0, 0, 0, px(14));
        list.addView(sub);
        for (Stock st : losers) list.addView(stockRow(st, 23, 52, true));
        ScrollView sv = new ScrollView(this);
        sv.setFocusable(true);
        sv.addView(list, new FrameLayout.LayoutParams(px(900), WRAP));
        AlertDialog d = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Nifty 50 fallers")
                .setView(sv)
                .setPositiveButton("Close", null)
                .create();
        d.show();
        if (d.getWindow() != null) d.getWindow().setLayout(px(980), px(860));
        sv.requestFocus();
    }
}
