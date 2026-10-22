package local.driveboard;

import android.app.Presentation;
import android.content.Context;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Browser UI rendered into a private virtual display and operated through the car surface. */
public final class BrowserPresentation extends Presentation {
    private static final String YOUTUBE_URL = "https://m.youtube.com";
    private static final String FOCUS_BRIDGE = "DriveBoardFocus";
    private static final String PAUSE_MEDIA = "(function(){window.__driveboardMediaLocked=true;"
            + "if(!window.__driveboardPlayGuard){window.__driveboardPlayGuard=true;"
            + "document.addEventListener('play',function(event){"
            + "if(window.__driveboardMediaLocked)event.target.pause()},true)}"
            + "document.querySelectorAll('video,audio').forEach(function(media){media.pause()})})()";
    private static final String ACTIVE_INPUT = "(function(){var e=document.activeElement;"
            + "return !!e && (e.tagName==='INPUT' || e.tagName==='TEXTAREA'"
            + " || e.isContentEditable)})()";

    private final Context appContext;
    private final Runnable onClose;
    private final BoardColours colors;
    private final DisplayMetrics displayMetrics = new DisplayMetrics();
    private final Rect stableArea = new Rect();
    private final FocusBridge focusBridge = new FocusBridge();
    private final List<HorizontalScrollView> keyRowScrolls = new ArrayList<>();

    private FrameLayout root;
    private FrameLayout content;
    private FrameLayout browserHolder;
    private LinearLayout chrome;
    private LinearLayout keyboard;
    private LinearLayout keyboardRows;
    private ScrollView keyboardScroll;
    private FrameLayout lockOverlay;
    private TextView lockReason;
    private EditText address;
    private WebView webView;
    private View fullscreenView;
    private WebChromeClient.CustomViewCallback fullscreenCallback;
    private ParkedState.State parkedState = ParkedState.State.UNKNOWN;
    private boolean permission;
    private boolean numericKeys;
    private boolean pageInputFocused;
    private boolean closed;
    private Boolean previouslyUnlocked;
    private String pendingUrl;
    private String currentPageUrl;
    private boolean clearHistoryAfterRestore;
    private int surfaceWidth;
    private int surfaceHeight;

    public BrowserPresentation(Context context, Display display, Runnable onClose) {
        super(context, display);
        this.appContext = AppLocale.wrap(context);
        this.onClose = onClose;
        this.colors = BoardColours.current(context);
        display.getMetrics(displayMetrics);
        surfaceWidth = displayMetrics.widthPixels;
        surfaceHeight = displayMetrics.heightPixels;
        stableArea.set(0, 0, surfaceWidth, surfaceHeight);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
        buildUi();
        setContentView(root);
        getWindow().getDecorView().setPadding(0, 0, 0, 0);
        getWindow().setLayout(-1, -1);
        applyStableArea();
        updateLock();
    }

    private void buildUi() {
        Context context = getContext();
        root = new FrameLayout(context);
        root.setBackgroundColor(colors.surface);

        content = new FrameLayout(context);
        content.setBackgroundColor(colors.surface);
        root.addView(content);

        chrome = new LinearLayout(context);
        chrome.setOrientation(LinearLayout.VERTICAL);
        content.addView(chrome, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout toolbar = new LinearLayout(context);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setPadding(dp(8), dp(5), dp(8), dp(5));
        toolbar.setBackgroundColor(colors.surfaceContainerLow);
        chrome.addView(toolbar, new LinearLayout.LayoutParams(-1, dp(62)));
        addToolbarButton(toolbar, R.string.browser_back, () -> {
            if (webView.canGoBack()) {
                webView.goBack();
            }
        });
        addToolbarButton(toolbar, R.string.browser_forward, () -> {
            if (webView.canGoForward()) {
                webView.goForward();
            }
        });
        addToolbarButton(toolbar, R.string.browser_reload, webViewReload());
        addToolbarButton(toolbar, R.string.browser_youtube, () -> loadUrl(YOUTUBE_URL));
        addToolbarButton(toolbar, R.string.browser_board, onClose);

        LinearLayout addressRow = new LinearLayout(context);
        addressRow.setGravity(Gravity.CENTER_VERTICAL);
        addressRow.setPadding(dp(10), dp(5), dp(10), dp(7));
        addressRow.setBackgroundColor(colors.surfaceContainerLow);
        chrome.addView(addressRow, new LinearLayout.LayoutParams(-1, dp(62)));

        address = new EditText(context);
        address.setSingleLine(true);
        address.setTextColor(colors.onSurface);
        address.setHintTextColor(colors.onSurfaceVariant);
        address.setHint(appContext.getString(R.string.browser_address_hint));
        address.setTextSize(17);
        address.setTypeface(Fonts.regular(context));
        address.setSelectAllOnFocus(true);
        address.setShowSoftInputOnFocus(false);
        address.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_VARIATION_URI);
        address.setImeOptions(EditorInfo.IME_ACTION_GO | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        address.setBackground(shape(colors.surfaceContainerHigh, 20));
        address.setPadding(dp(18), 0, dp(18), 0);
        address.setOnFocusChangeListener((view, hasFocus) -> {
            if (hasFocus && isUnlocked()) {
                pageInputFocused = false;
                showKeyboard();
            }
        });
        address.setOnClickListener(view -> {
            if (isUnlocked()) {
                showKeyboard();
            }
        });
        addressRow.addView(address, new LinearLayout.LayoutParams(0, dp(50), 1));
        TextView go = control(context, appContext.getString(R.string.browser_go), true);
        go.setOnClickListener(view -> submitAddress());
        LinearLayout.LayoutParams goParams = new LinearLayout.LayoutParams(dp(76), dp(50));
        goParams.leftMargin = dp(8);
        addressRow.addView(go, goParams);

        browserHolder = new FrameLayout(context);
        chrome.addView(browserHolder, new LinearLayout.LayoutParams(-1, 0, 1));
        webView = new NoImeWebView(context);
        webView.setBackgroundColor(0xFFFFFFFF);
        webView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        webView.setFocusableInTouchMode(true);
        webView.setOnTouchListener((view, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                address.clearFocus();
                webView.requestFocus();
                webView.postDelayed(this::checkPageFocus, 80);
            }
            return false;
        });
        browserHolder.addView(webView, new FrameLayout.LayoutParams(-1, -1));
        configureWebView();

        keyboard = new LinearLayout(context);
        keyboard.setOrientation(LinearLayout.VERTICAL);
        keyboard.setPadding(dp(6), dp(6), dp(6), dp(6));
        keyboard.setBackgroundColor(colors.surfaceContainerHigh);
        keyboardScroll = new ScrollView(context);
        keyboardScroll.setFillViewport(false);
        keyboardScroll.setVerticalScrollBarEnabled(false);
        keyboardRows = new LinearLayout(context);
        keyboardRows.setOrientation(LinearLayout.VERTICAL);
        keyboardScroll.addView(keyboardRows, new ScrollView.LayoutParams(-1, -2));
        keyboard.addView(keyboardScroll, new LinearLayout.LayoutParams(-1, -1));
        keyboard.setVisibility(View.GONE);
        chrome.addView(keyboard, new LinearLayout.LayoutParams(-1, -2));
        rebuildKeyboard();

        lockOverlay = new FrameLayout(context);
        lockOverlay.setBackgroundColor(colors.surface);
        lockOverlay.setClickable(true);
        LinearLayout lockCard = new LinearLayout(context);
        lockCard.setOrientation(LinearLayout.VERTICAL);
        lockCard.setGravity(Gravity.CENTER);
        lockCard.setPadding(dp(24), dp(24), dp(24), dp(24));
        FrameLayout.LayoutParams cardParams = new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER);
        cardParams.leftMargin = dp(24);
        cardParams.rightMargin = dp(24);
        lockOverlay.addView(lockCard, cardParams);
        TextView title = label(context, appContext.getString(R.string.browser_locked_title), 26, true);
        title.setGravity(Gravity.CENTER);
        lockCard.addView(title, new LinearLayout.LayoutParams(-1, -2));
        lockReason = label(context, "", 17, false);
        lockReason.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams reasonParams = new LinearLayout.LayoutParams(-1, -2);
        reasonParams.topMargin = dp(12);
        lockCard.addView(lockReason, reasonParams);
        TextView board = control(context, appContext.getString(R.string.browser_board), true);
        LinearLayout.LayoutParams boardParams = new LinearLayout.LayoutParams(-1, dp(64));
        boardParams.topMargin = dp(24);
        lockCard.addView(board, boardParams);
        board.setOnClickListener(view -> onClose.run());
        content.addView(lockOverlay, new FrameLayout.LayoutParams(-1, -1));
    }

    private Runnable webViewReload() {
        return () -> {
            if (isUnlocked()) {
                webView.reload();
            }
        };
    }

    private void configureWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setSupportMultipleWindows(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        webView.addJavascriptInterface(focusBridge, FOCUS_BRIDGE);
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                return request.isForMainFrame() && !isHttpUrl(uri);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                if (closed || "about:blank".equals(url)) {
                    return;
                }
                if (!isUnlocked()) {
                    pauseMedia();
                    return;
                }
                currentPageUrl = url;
                address.setText(url);
                installFocusListener();
                if (clearHistoryAfterRestore) {
                    view.clearHistory();
                    clearHistoryAfterRestore = false;
                }
            }

            @Override
            public void onPageCommitVisible(WebView view, String url) {
                if (!closed && !isUnlocked() && !"about:blank".equals(url)) {
                    pauseMedia();
                    view.stopLoading();
                    view.loadUrl("about:blank");
                } else if (!closed && isUnlocked() && isHttpUrl(Uri.parse(url))) {
                    currentPageUrl = url;
                }
            }
        });
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                if (closed || !isUnlocked()) {
                    callback.onCustomViewHidden();
                    return;
                }
                hideFullscreen();
                fullscreenView = view;
                fullscreenCallback = callback;
                chrome.setVisibility(View.GONE);
                lockOverlay.setVisibility(View.GONE);
                content.addView(view, new FrameLayout.LayoutParams(-1, -1));
            }

            @Override
            public void onHideCustomView() {
                hideFullscreen();
            }
        });
    }

    private void installFocusListener() {
        // The bridge is visible to iframes too. It only asks native code to recheck
        // the top-level document's focused element; it accepts no text or commands.
        String script = "(function(){if(window.__driveboardFocusInstalled)return;"
                + "window.__driveboardFocusInstalled=true;"
                + "function report(){DriveBoardFocus.focusChanged()}"
                + "document.addEventListener('focusin',report,true);"
                + "document.addEventListener('focusout',report,true);"
                + "document.addEventListener('click',report,true)})()";
        webView.evaluateJavascript(script, ignored -> checkPageFocus());
    }

    private void checkPageFocus() {
        if (closed || !isUnlocked() || address.hasFocus()) {
            return;
        }
        webView.evaluateJavascript(ACTIVE_INPUT, result -> {
            if (closed || !isUnlocked() || address.hasFocus()) {
                return;
            }
            pageInputFocused = "true".equals(result);
            if (pageInputFocused) {
                showKeyboard();
            } else {
                keyboard.setVisibility(View.GONE);
            }
        });
    }

    private final class FocusBridge {
        @JavascriptInterface
        public void focusChanged() {
            WebView current = webView;
            if (current != null) {
                current.post(BrowserPresentation.this::checkPageFocus);
            }
        }
    }

    public void setStableArea(Rect area, int width, int height) {
        if (closed) {
            return;
        }
        getDisplay().getMetrics(displayMetrics);
        surfaceWidth = Math.max(1, width);
        surfaceHeight = Math.max(1, height);
        stableArea.set(area == null || area.isEmpty()
                ? new Rect(0, 0, surfaceWidth, surfaceHeight) : area);
        if (content != null) {
            applyStableArea();
        }
    }

    private void applyStableArea() {
        float scaleX = displayMetrics.widthPixels / (float) surfaceWidth;
        float scaleY = displayMetrics.heightPixels / (float) surfaceHeight;
        int left = clamp(Math.round(stableArea.left * scaleX), 0, displayMetrics.widthPixels);
        int top = clamp(Math.round(stableArea.top * scaleY), 0, displayMetrics.heightPixels);
        int right = clamp(Math.round(stableArea.right * scaleX), left, displayMetrics.widthPixels);
        int bottom = clamp(Math.round(stableArea.bottom * scaleY), top, displayMetrics.heightPixels);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(right - left, bottom - top);
        params.leftMargin = left;
        params.topMargin = top;
        content.setLayoutParams(params);
        int keyboardHeight = Math.min(dp(280), Math.max(dp(128), bottom - top - dp(190)));
        keyboard.setLayoutParams(new LinearLayout.LayoutParams(-1, keyboardHeight));
    }

    public void loadUrl(String value) {
        if (closed || webView == null) {
            return;
        }
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isEmpty()) {
            return;
        }
        String candidate;
        if (trimmed.contains(" ") || (!trimmed.contains("://")
                && !trimmed.contains(".") && !trimmed.startsWith("localhost"))) {
            candidate = "https://www.google.com/search?q=" + Uri.encode(trimmed);
        } else {
            candidate = trimmed.contains("://") ? trimmed : "https://" + trimmed;
        }
        Uri uri = Uri.parse(candidate);
        if (!isHttpUrl(uri)) {
            return;
        }
        address.clearFocus();
        pageInputFocused = false;
        keyboard.setVisibility(View.GONE);
        address.setText(candidate);
        if (isUnlocked()) {
            pendingUrl = null;
            currentPageUrl = candidate;
            webView.loadUrl(candidate);
        } else {
            pendingUrl = candidate;
        }
    }

    public String currentUrl() {
        if (pendingUrl != null) {
            return pendingUrl;
        }
        return currentPageUrl;
    }

    private static boolean isHttpUrl(Uri uri) {
        String scheme = uri.getScheme();
        return ("https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme))
                && uri.getHost() != null && !uri.getHost().isEmpty();
    }

    private void submitAddress() {
        if (isUnlocked()) {
            loadUrl(address.getText().toString());
        }
    }

    public void setLockState(ParkedState.State state, boolean hasPermission) {
        if (closed) {
            return;
        }
        parkedState = state == null ? ParkedState.State.UNKNOWN : state;
        permission = hasPermission;
        if (content != null) {
            updateLock();
        }
    }

    private boolean isUnlocked() {
        return permission && parkedState == ParkedState.State.PARKED;
    }

    private void updateLock() {
        boolean unlocked = isUnlocked();
        if (!unlocked) {
            if (previouslyUnlocked == null || previouslyUnlocked) {
                hideFullscreen();
                pageInputFocused = false;
                address.clearFocus();
                keyboard.setVisibility(View.GONE);
                String activeUrl = currentPageUrl == null ? webView.getUrl() : currentPageUrl;
                if (pendingUrl == null && isHttpUrl(Uri.parse(activeUrl == null ? "" : activeUrl))) {
                    pendingUrl = activeUrl;
                }
                pauseMedia();
                webView.stopLoading();
                webView.loadUrl("about:blank");
                webView.onPause();
            }
            int reason = !permission ? R.string.browser_permission_needed
                    : parkedState == ParkedState.State.MOVING
                    ? R.string.browser_moving : R.string.browser_waiting_gps;
            lockReason.setText(appContext.getString(reason));
            lockOverlay.setVisibility(View.VISIBLE);
            lockOverlay.bringToFront();
        } else {
            if (previouslyUnlocked == null || !previouslyUnlocked) {
                webView.onResume();
                webView.evaluateJavascript("window.__driveboardMediaLocked=false", null);
                if (pendingUrl != null) {
                    String restore = pendingUrl;
                    pendingUrl = null;
                    currentPageUrl = restore;
                    clearHistoryAfterRestore = true;
                    webView.loadUrl(restore);
                }
            }
            lockOverlay.setVisibility(View.GONE);
        }
        previouslyUnlocked = unlocked;
    }

    private void pauseMedia() {
        if (webView != null && !closed) {
            webView.evaluateJavascript(PAUSE_MEDIA, null);
        }
    }

    private void hideFullscreen() {
        if (fullscreenView == null) {
            return;
        }
        content.removeView(fullscreenView);
        fullscreenView = null;
        chrome.setVisibility(View.VISIBLE);
        if (fullscreenCallback != null) {
            fullscreenCallback.onCustomViewHidden();
            fullscreenCallback = null;
        }
    }

    private void showKeyboard() {
        if (isUnlocked() && fullscreenView == null) {
            keyboard.setVisibility(View.VISIBLE);
        }
    }

    private void rebuildKeyboard() {
        keyboardRows.removeAllViews();
        keyRowScrolls.clear();
        if (numericKeys) {
            addKeyRow(new String[]{"1", "2", "3", "4", "5", "6", "7", "8", "9", "0"});
            addKeyRow(new String[]{"-", "/", ":", ";", "(", ")", "$", "&", "@", "\""});
            addKeyRow(new String[]{".", ",", "?", "!", "'"});
        } else {
            addKeyRow(new String[]{"q", "w", "e", "r", "t", "y", "u", "i", "o", "p"});
            addKeyRow(new String[]{"a", "s", "d", "f", "g", "h", "j", "k", "l"});
            addKeyRow(new String[]{"z", "x", "c", "v", "b", "n", "m"});
        }
        LinearLayout row = newKeyRow();
        addKey(row, appContext.getString(numericKeys ? R.string.browser_keyboard_letters
                : R.string.browser_keyboard_numbers), 1.3f, () -> {
            numericKeys = !numericKeys;
            rebuildKeyboard();
        });
        addKey(row, ".", .65f, () -> type("."));
        addKey(row, "/", .65f, () -> type("/"));
        addKey(row, appContext.getString(R.string.browser_keyboard_space), 2.2f,
                () -> type(" "));
        addKey(row, "⌫", 1f, this::deleteCharacter);
        addKey(row, appContext.getString(R.string.browser_keyboard_enter), 1.2f,
                this::pressEnter);
        addKey(row, appContext.getString(R.string.browser_keyboard_done), 1.2f,
                this::finishTyping);
    }

    private void addKeyRow(String[] values) {
        LinearLayout row = newKeyRow();
        for (String value : values) {
            addKey(row, value, 1f, () -> type(value));
        }
    }

    private LinearLayout newKeyRow() {
        HorizontalScrollView scroller = new HorizontalScrollView(getContext());
        scroller.setHorizontalScrollBarEnabled(false);
        scroller.setFillViewport(false);
        LinearLayout row = new LinearLayout(getContext());
        scroller.addView(row, new HorizontalScrollView.LayoutParams(-2, dp(70)));
        keyboardRows.addView(scroller, new LinearLayout.LayoutParams(-1, dp(70)));
        keyRowScrolls.add(scroller);
        return row;
    }

    private void addKey(LinearLayout row, String text, float weight, Runnable action) {
        TextView key = control(getContext(), text, false);
        key.setTextSize(19);
        key.setOnClickListener(view -> {
            if (isUnlocked()) {
                action.run();
            }
        });
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                Math.max(dp(64), Math.round(dp(64) * weight)), dp(64));
        params.setMargins(dp(2), dp(3), dp(2), dp(3));
        row.addView(key, params);
    }

    private void type(String text) {
        if (address.hasFocus()) {
            int start = Math.max(0, address.getSelectionStart());
            int end = Math.max(0, address.getSelectionEnd());
            address.getText().replace(Math.min(start, end), Math.max(start, end), text);
        } else if (pageInputFocused) {
            String quoted = JSONObject.quote(text);
            webView.evaluateJavascript("(function(){var e=document.activeElement;"
                    + "if(!e || !(e.tagName==='INPUT'||e.tagName==='TEXTAREA'"
                    + "||e.isContentEditable))return;"
                    + "if(!document.execCommand('insertText',false," + quoted + ")){"
                    + "if(e.setRangeText){e.setRangeText(" + quoted + ",e.selectionStart,"
                    + "e.selectionEnd,'end');e.dispatchEvent(new Event('input',{bubbles:true}))}}})()",
                    null);
        }
    }

    private void deleteCharacter() {
        if (address.hasFocus()) {
            int start = Math.max(0, address.getSelectionStart());
            int end = Math.max(0, address.getSelectionEnd());
            if (start == end && start > 0) {
                start--;
            }
            address.getText().delete(Math.min(start, end), Math.max(start, end));
        } else if (pageInputFocused) {
            webView.evaluateJavascript("(function(){var e=document.activeElement;"
                    + "if(!e || !(e.tagName==='INPUT'||e.tagName==='TEXTAREA'"
                    + "||e.isContentEditable))return;"
                    + "document.execCommand('delete',false,null)})()", null);
        }
    }

    private void pressEnter() {
        if (address.hasFocus()) {
            submitAddress();
        } else if (pageInputFocused) {
            long now = SystemClock.uptimeMillis();
            webView.dispatchKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_DOWN,
                    KeyEvent.KEYCODE_ENTER, 0));
            webView.dispatchKeyEvent(new KeyEvent(now, now + 30, KeyEvent.ACTION_UP,
                    KeyEvent.KEYCODE_ENTER, 0));
        }
    }

    private void finishTyping() {
        keyboard.setVisibility(View.GONE);
    }

    /** Surface coordinates are mapped into the presentation display before dispatch. */
    public void click(float x, float y) {
        if (closed || root == null) {
            return;
        }
        float localX = x * displayMetrics.widthPixels / surfaceWidth;
        float localY = y * displayMetrics.heightPixels / surfaceHeight;
        if (localX < 0 || localY < 0 || localX >= displayMetrics.widthPixels
                || localY >= displayMetrics.heightPixels) {
            return;
        }
        long now = SystemClock.uptimeMillis();
        dispatchTouch(now, now, MotionEvent.ACTION_DOWN, localX, localY);
        dispatchTouch(now, now + 30, MotionEvent.ACTION_UP, localX, localY);
    }

    private void dispatchTouch(long downTime, long eventTime, int action, float x, float y) {
        MotionEvent event = MotionEvent.obtain(downTime, eventTime, action, x, y, 0);
        try {
            event.setSource(InputDevice.SOURCE_TOUCHSCREEN);
            getWindow().getDecorView().dispatchTouchEvent(event);
        } finally {
            event.recycle();
        }
    }

    /** Scroll distances use the same surface-pixel units as click coordinates. */
    public void scroll(float distanceX, float distanceY) {
        if (closed || !isUnlocked() || webView == null) {
            return;
        }
        float scaleX = displayMetrics.widthPixels / (float) surfaceWidth;
        float scaleY = displayMetrics.heightPixels / (float) surfaceHeight;
        if (keyboard.getVisibility() == View.VISIBLE) {
            keyboardScroll.scrollBy(0, Math.round(distanceY * scaleY));
            int horizontal = Math.round(distanceX * scaleX);
            for (HorizontalScrollView row : keyRowScrolls) {
                row.scrollBy(horizontal, 0);
            }
        } else {
            webView.scrollBy(Math.round(distanceX * scaleX), Math.round(distanceY * scaleY));
        }
    }

    public void fling(float velocityX, float velocityY) {
        if (closed || !isUnlocked() || webView == null) {
            return;
        }
        if (keyboard.getVisibility() == View.VISIBLE) {
            keyboardScroll.fling(-Math.round(velocityY * displayMetrics.heightPixels
                    / surfaceHeight));
            int horizontal = -Math.round(velocityX * displayMetrics.widthPixels
                    / surfaceWidth);
            for (HorizontalScrollView row : keyRowScrolls) {
                row.fling(horizontal);
            }
            return;
        }
        webView.flingScroll(-Math.round(velocityX * displayMetrics.widthPixels / surfaceWidth),
                -Math.round(velocityY * displayMetrics.heightPixels / surfaceHeight));
    }

    public void scale(float factor) {
        if (closed || !isUnlocked() || webView == null) {
            return;
        }
        if (!Float.isFinite(factor) || factor <= 0) {
            return;
        }
        float current = webView.getScale();
        float target = Math.max(.5f, Math.min(3f, current * factor));
        webView.zoomBy(Math.max(.5f, Math.min(2f, target / Math.max(.01f, current))));
    }

    @Override
    public void onBackPressed() {
        if (fullscreenView != null) {
            hideFullscreen();
        } else if (keyboard.getVisibility() == View.VISIBLE) {
            keyboard.setVisibility(View.GONE);
        } else if (isUnlocked() && webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            onClose.run();
        }
    }

    public void pause() {
        if (closed || webView == null) {
            return;
        }
        hideFullscreen();
        pauseMedia();
        webView.onPause();
    }

    public void close() {
        if (closed) {
            return;
        }
        try {
            try {
                pause();
            } catch (RuntimeException ignored) {
                // Continue releasing the display even if the renderer is already gone.
            }
            closed = true;
            WebView retiring = webView;
            webView = null;
            if (retiring != null) {
                try {
                    retiring.removeJavascriptInterface(FOCUS_BRIDGE);
                    retiring.stopLoading();
                    retiring.setWebChromeClient(null);
                    retiring.setWebViewClient(null);
                    retiring.loadUrl("about:blank");
                } finally {
                    browserHolder.removeView(retiring);
                    retiring.destroy();
                }
            }
        } finally {
            try {
                CookieManager.getInstance().flush();
            } finally {
                dismiss();
            }
        }
    }

    private void addToolbarButton(LinearLayout toolbar, int label, Runnable action) {
        TextView button = control(getContext(), appContext.getString(label), false);
        button.setTextSize(15);
        button.setOnClickListener(view -> {
            if (isUnlocked()) {
                action.run();
            }
        });
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(50), 1);
        params.setMargins(dp(3), 0, dp(3), 0);
        toolbar.addView(button, params);
    }

    private TextView control(Context context, String text, boolean prominent) {
        TextView view = label(context, text, 16, true);
        view.setGravity(Gravity.CENTER);
        view.setTextColor(prominent ? colors.onPrimaryContainer : colors.onSurface);
        view.setBackground(shape(prominent ? colors.primaryContainer
                : colors.surfaceContainerHigh, 18));
        view.setClickable(true);
        view.setFocusable(false);
        return view;
    }

    private TextView label(Context context, String text, int sizeSp, boolean strong) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextSize(sizeSp);
        view.setTextColor(strong ? colors.onSurface : colors.onSurfaceVariant);
        view.setTypeface(strong ? Fonts.semibold(context) : Fonts.regular(context));
        return view;
    }

    private GradientDrawable shape(int color, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    private int dp(float value) {
        return Math.round(value * getContext().getResources().getDisplayMetrics().density);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static final class NoImeWebView extends WebView {
        NoImeWebView(Context context) {
            super(context);
        }

        @Override
        public boolean onCheckIsTextEditor() {
            return false;
        }

        @Override
        public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
            return null;
        }
    }
}
