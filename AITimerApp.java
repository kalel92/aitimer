import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.time.*;
import java.time.format.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;

/**
 * AI Session Monitor – ventana flotante siempre visible.
 * Muestra uso de tokens de Claude Code y estado del daemon de Antigravity (Gemini).
 */
public class AITimerApp extends JFrame {

    // ─── Rutas ──────────────────────────────────────────────────────────────
    private static final String HOME          = System.getProperty("user.home");
    private static final String CLAUDE_DIR    = HOME + "/.claude/projects";
    private static final String AG_DAEMON_DIR = HOME + "/.gemini/antigravity/daemon";
    private static final String AG_CONV_DIR   = HOME + "/.gemini/antigravity/conversations";

    // ─── Config ─────────────────────────────────────────────────────────────
    private static final int REFRESH_SECS     = 30;
    private static final int CLAUDE_HOURS     = 5;      // ventana de rate-limit de Claude Code
    private static final int CLAUDE_LIMIT     = 40_000; // estimado plan Pro (~tokens/5h)
    private static final int WINDOW_W         = 310;

    // ─── Paleta ─────────────────────────────────────────────────────────────
    private static final Color BG          = new Color(14, 14, 20, 235);
    private static final Color C_CLAUDE    = new Color(160, 95, 245);
    private static final Color C_GEMINI    = new Color(66, 165, 245);
    private static final Color C_TEXT      = new Color(235, 235, 235);
    private static final Color C_DIM       = new Color(130, 130, 155);
    private static final Color C_OK        = new Color(72, 199, 96);
    private static final Color C_WARN      = new Color(255, 183, 50);
    private static final Color C_BAD       = new Color(255, 75, 75);
    private static final Color BAR_TRACK_C = new Color(50, 40, 65);
    private static final Color BAR_TRACK_G = new Color(28, 50, 72);

    // ─── Estado ─────────────────────────────────────────────────────────────
    private volatile ClaudeData cd = new ClaudeData();
    private volatile GeminiData gd = new GeminiData();

    // ─── UI ─────────────────────────────────────────────────────────────────
    private JLabel lblClaudeTokens, lblClaudeReset, lblClaudeLast;
    private ColorBar barClaude;
    private JLabel lblGeminiDot, lblGeminiQuota, lblGeminiConv, lblGeminiLast;
    private ColorBar barGemini;
    private JLabel lblTime;

    // ─── Drag ───────────────────────────────────────────────────────────────
    private Point dragOrigin;

    // ─── Scheduler ──────────────────────────────────────────────────────────
    private ScheduledExecutorService sched;

    // ════════════════════════════════════════════════════════════════════════
    public static void main(String[] args) {
        SwingUtilities.invokeLater(AITimerApp::new);
    }

    AITimerApp() {
        super("AI Timer");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setAlwaysOnTop(true);
        setUndecorated(true);
        try { setBackground(new Color(0, 0, 0, 0)); } catch (Exception ignored) {}

        buildUI();
        pack();
        positionWindow();
        hookDrag();
        setVisible(true);

        sched = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "ai-timer");
            t.setDaemon(true);
            return t;
        });
        sched.scheduleAtFixedRate(this::refreshData, 0, REFRESH_SECS, TimeUnit.SECONDS);
    }

    // ─── Posicionamiento ────────────────────────────────────────────────────
    private void positionWindow() {
        Dimension scr = Toolkit.getDefaultToolkit().getScreenSize();
        setLocation(scr.width - getWidth() - 18, 40);
    }

    // ─── Drag ───────────────────────────────────────────────────────────────
    private void hookDrag() {
        MouseAdapter ma = new MouseAdapter() {
            public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) showMenu(e.getComponent(), e.getX(), e.getY());
                else dragOrigin = e.getPoint();
            }
            public void mouseDragged(MouseEvent e) {
                if (dragOrigin != null) {
                    Point loc = getLocation();
                    setLocation(loc.x + e.getX() - dragOrigin.x, loc.y + e.getY() - dragOrigin.y);
                }
            }
        };
        addMouseListener(ma);
        addMouseMotionListener(ma);
    }

    // ─── Context menu ───────────────────────────────────────────────────────
    private void showMenu(Component src, int x, int y) {
        JPopupMenu m = new JPopupMenu();
        JMenuItem refresh = new JMenuItem("Actualizar ahora");
        refresh.addActionListener(e -> sched.execute(this::refreshData));
        JMenuItem exit = new JMenuItem("Cerrar");
        exit.addActionListener(e -> System.exit(0));
        m.add(refresh);
        m.addSeparator();
        m.add(exit);
        m.show(src, x, y);
    }

    // ════════════════════════════════════════════════════════════════════════
    //  UI Construction
    // ════════════════════════════════════════════════════════════════════════
    private void buildUI() {
        RoundPanel root = new RoundPanel(BG, 14);
        root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));
        root.setBorder(BorderFactory.createEmptyBorder(10, 13, 10, 13));
        root.setPreferredSize(new Dimension(WINDOW_W, 215));

        // Barra de título
        JPanel titleRow = hPanel();
        JLabel title = lbl("⚡ AI Sessions", 12, Font.BOLD, C_TEXT);
        lblTime = lbl("", 10, Font.PLAIN, C_DIM);
        titleRow.add(title);
        titleRow.add(Box.createHorizontalGlue());
        titleRow.add(lblTime);

        // Sección Claude
        JPanel secClaude = buildClaudeSection();

        // Separador
        JSeparator sep = new JSeparator(SwingConstants.HORIZONTAL);
        sep.setForeground(new Color(50, 50, 65));
        sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));

        // Sección Gemini / Antigravity
        JPanel secGemini = buildGeminiSection();

        root.add(titleRow);
        root.add(vGap(7));
        root.add(secClaude);
        root.add(vGap(7));
        root.add(sep);
        root.add(vGap(7));
        root.add(secGemini);

        setContentPane(root);
    }

    private JPanel buildClaudeSection() {
        JPanel p = vPanel();

        // Fila cabecera
        JPanel r1 = hPanel();
        r1.add(lbl("◉ CLAUDE CODE", 11, Font.BOLD, C_CLAUDE));
        r1.add(Box.createHorizontalGlue());
        lblClaudeLast = lbl("", 10, Font.PLAIN, C_DIM);
        r1.add(lblClaudeLast);

        // Barra de progreso
        barClaude = new ColorBar(C_CLAUDE, BAR_TRACK_C);

        // Fila tokens
        JPanel r2 = hPanel();
        r2.add(lbl("Tokens 5h:", 10, Font.PLAIN, C_DIM));
        r2.add(hGap(4));
        lblClaudeTokens = lbl("—", 10, Font.BOLD, C_TEXT);
        r2.add(lblClaudeTokens);
        r2.add(Box.createHorizontalGlue());

        // Fila reinicio
        JPanel r3 = hPanel();
        r3.add(lbl("Reinicia en:", 10, Font.PLAIN, C_DIM));
        r3.add(hGap(4));
        lblClaudeReset = lbl("—", 10, Font.BOLD, C_TEXT);
        r3.add(lblClaudeReset);
        r3.add(Box.createHorizontalGlue());

        p.add(r1);
        p.add(vGap(4));
        p.add(barClaude);
        p.add(vGap(4));
        p.add(r2);
        p.add(vGap(1));
        p.add(r3);
        return p;
    }

    private JPanel buildGeminiSection() {
        JPanel p = vPanel();

        // Fila cabecera
        JPanel r1 = hPanel();
        lblGeminiDot = lbl("●", 12, Font.BOLD, C_DIM);
        r1.add(lblGeminiDot);
        r1.add(hGap(3));
        r1.add(lbl("ANTIGRAVITY", 11, Font.BOLD, C_GEMINI));
        r1.add(Box.createHorizontalGlue());
        lblGeminiLast = lbl("", 10, Font.PLAIN, C_DIM);
        r1.add(lblGeminiLast);

        // Barra de progreso
        barGemini = new ColorBar(C_GEMINI, BAR_TRACK_G);

        // Fila cuota
        JPanel r2 = hPanel();
        r2.add(lbl("Cuota:", 10, Font.PLAIN, C_DIM));
        r2.add(hGap(4));
        lblGeminiQuota = lbl("—", 10, Font.BOLD, C_TEXT);
        r2.add(lblGeminiQuota);
        r2.add(Box.createHorizontalGlue());

        // Fila conversaciones
        JPanel r3 = hPanel();
        r3.add(lbl("Sesiones:", 10, Font.PLAIN, C_DIM));
        r3.add(hGap(4));
        lblGeminiConv = lbl("—", 10, Font.BOLD, C_TEXT);
        r3.add(lblGeminiConv);
        r3.add(Box.createHorizontalGlue());

        p.add(r1);
        p.add(vGap(4));
        p.add(barGemini);
        p.add(vGap(4));
        p.add(r2);
        p.add(vGap(1));
        p.add(r3);
        return p;
    }

    // ════════════════════════════════════════════════════════════════════════
    //  Data Reading
    // ════════════════════════════════════════════════════════════════════════
    private void refreshData() {
        cd = readClaude();
        gd = readGemini();
        SwingUtilities.invokeLater(this::updateUI);
    }

    // ─── Claude Code ────────────────────────────────────────────────────────
    private ClaudeData readClaude() {
        ClaudeData d = new ClaudeData();
        File base = new File(CLAUDE_DIR);
        if (!base.isDirectory()) return d;

        long cutoff  = System.currentTimeMillis() - CLAUDE_HOURS * 3_600_000L;
        long earliest = Long.MAX_VALUE;

        File[] projects = base.listFiles();
        if (projects == null) return d;

        for (File proj : projects) {
            File[] sessions = proj.listFiles((dir, name) -> name.endsWith(".jsonl"));
            if (sessions == null) continue;
            for (File sess : sessions) {
                try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(new FileInputStream(sess), "UTF-8"))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        long ts = parseTimestamp(line);
                        if (ts <= 0 || ts < cutoff) continue;

                        if (ts > d.lastMs)   d.lastMs  = ts;
                        if (ts < earliest)   earliest  = ts;

                        d.inputTokens       += extractInt(line, "\"input_tokens\":(\\d+)");
                        d.outputTokens      += extractInt(line, "\"output_tokens\":(\\d+)");
                        d.cacheCreateTokens += extractInt(line, "\"cache_creation_input_tokens\":(\\d+)");
                    }
                } catch (IOException ignored) {}
            }
        }
        d.windowStart = (earliest == Long.MAX_VALUE) ? 0 : earliest;
        return d;
    }

    // ─── Antigravity / Gemini ───────────────────────────────────────────────
    private GeminiData readGemini() {
        GeminiData d = new GeminiData();

        // Leer archivo de descubrimiento del daemon
        File daemonDir = new File(AG_DAEMON_DIR);
        if (!daemonDir.isDirectory()) return d;

        File[] jsons = daemonDir.listFiles((dir, name) -> name.endsWith(".json"));
        if (jsons == null || jsons.length == 0) return d;

        File daemonFile = Arrays.stream(jsons)
                .max(Comparator.comparingLong(File::lastModified))
                .orElse(null);
        if (daemonFile == null) return d;

        d.daemonFileAge = System.currentTimeMillis() - daemonFile.lastModified();

        try {
            String json = new String(Files.readAllBytes(daemonFile.toPath()), "UTF-8");
            d.pid        = extractInt(json, "\"pid\":(\\d+)");
            d.httpPort   = extractInt(json, "\"httpPort\":(\\d+)");
            d.csrfToken  = extractStr(json, "\"csrfToken\":\"([^\"]+)\"");

            if (d.pid > 0 && d.httpPort > 0) {
                d.daemonAlive = pingDaemon(d.httpPort, d.csrfToken);
            }
            if (d.daemonAlive) {
                queryDaemonAPI(d);
            }
        } catch (Exception e) {
            d.error = e.getMessage();
        }

        // Contar conversaciones
        File convDir = new File(AG_CONV_DIR);
        if (convDir.isDirectory()) {
            File[] convs = convDir.listFiles((dir, name) -> name.endsWith(".pb"));
            if (convs != null) {
                d.convCount = convs.length;
                for (File f : convs) {
                    if (f.lastModified() > d.lastConvMs) d.lastConvMs = f.lastModified();
                }
            }
        }
        return d;
    }

    private boolean pingDaemon(int port, String csrf) {
        try {
            HttpURLConnection c = openConn("http://127.0.0.1:" + port + "/", csrf, 600);
            int code = c.getResponseCode();
            c.disconnect();
            return code < 500;
        } catch (Exception e) {
            return false;
        }
    }

    private void queryDaemonAPI(GeminiData d) {
        // Probar endpoints comunes en orden de prioridad
        String[] endpoints = {
            "/api/v2/quota", "/api/v2/usage", "/api/v2/user",
            "/api/quota", "/api/usage", "/quota", "/v2/quota"
        };
        for (String ep : endpoints) {
            String resp = httpGet("http://127.0.0.1:" + d.httpPort + ep, d.csrfToken);
            if (resp == null) continue;
            int used      = extractInt(resp, "\"used\":(\\d+)");
            int limit     = extractInt(resp, "\"limit\":(\\d+)");
            int remaining = extractInt(resp, "\"remaining\":(\\d+)");
            if (used > 0 || limit > 0 || remaining > 0) {
                d.quotaUsed      = used;
                d.quotaLimit     = limit;
                d.quotaRemaining = remaining;
                d.rawResponse    = resp;
                break;
            }
        }
    }

    private String httpGet(String url, String csrf) {
        try {
            HttpURLConnection c = openConn(url, csrf, 1200);
            if (c.getResponseCode() != 200) { c.disconnect(); return null; }
            StringBuilder sb = new StringBuilder();
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(c.getInputStream(), "UTF-8"))) {
                String ln;
                while ((ln = br.readLine()) != null) sb.append(ln);
            }
            c.disconnect();
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    private HttpURLConnection openConn(String url, String csrf, int timeout) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(timeout);
        c.setReadTimeout(timeout);
        c.setRequestProperty("X-CSRF-Token", csrf);
        c.setRequestProperty("Accept", "application/json");
        return c;
    }

    // ════════════════════════════════════════════════════════════════════════
    //  UI Update  (EDT)
    // ════════════════════════════════════════════════════════════════════════
    private void updateUI() {
        // ── Claude ──────────────────────────────────────────────────────────
        int total = cd.inputTokens + cd.outputTokens + cd.cacheCreateTokens;
        int pct   = (int) Math.min(100, total * 100L / CLAUDE_LIMIT);

        lblClaudeTokens.setText(fmtK(total) + " / ~" + fmtK(CLAUDE_LIMIT));

        Color barColor;
        if      (pct >= 90) barColor = C_BAD;
        else if (pct >= 65) barColor = C_WARN;
        else                barColor = C_CLAUDE;
        barClaude.set(pct, barColor);

        if (cd.windowStart > 0) {
            long resetAt  = cd.windowStart + CLAUDE_HOURS * 3_600_000L;
            long remaining = resetAt - System.currentTimeMillis();
            if (remaining > 0) {
                lblClaudeReset.setText(fmtDuration(remaining));
                lblClaudeReset.setForeground(remaining < 1_800_000 ? C_WARN : C_OK);
            } else {
                lblClaudeReset.setText("ya disponible");
                lblClaudeReset.setForeground(C_OK);
            }
        } else {
            lblClaudeReset.setText("sin actividad reciente");
            lblClaudeReset.setForeground(C_DIM);
        }

        lblClaudeLast.setText(cd.lastMs > 0 ? "hace " + fmtAgo(System.currentTimeMillis() - cd.lastMs) : "—");

        // ── Gemini / Antigravity ─────────────────────────────────────────────
        if (gd.daemonAlive) {
            lblGeminiDot.setForeground(C_OK);
            if (gd.quotaLimit > 0) {
                int gPct = (int) Math.min(100, gd.quotaUsed * 100L / gd.quotaLimit);
                Color gc  = gPct >= 90 ? C_BAD : gPct >= 65 ? C_WARN : C_GEMINI;
                barGemini.set(gPct, gc);
                lblGeminiQuota.setText(fmtK(gd.quotaUsed) + " / " + fmtK(gd.quotaLimit));
                lblGeminiQuota.setForeground(C_TEXT);
            } else {
                barGemini.set(0, C_GEMINI);
                lblGeminiQuota.setText("daemon activo");
                lblGeminiQuota.setForeground(C_OK);
            }
        } else {
            lblGeminiDot.setForeground(C_BAD);
            barGemini.set(0, C_GEMINI);
            boolean hoy = gd.daemonFileAge > 0 && gd.daemonFileAge < 86_400_000L;
            lblGeminiQuota.setText(hoy ? "offline (visto hoy)" : "offline");
            lblGeminiQuota.setForeground(C_DIM);
        }

        lblGeminiConv.setText(gd.convCount + " conversacion" + (gd.convCount == 1 ? "" : "es"));
        lblGeminiLast.setText(gd.lastConvMs > 0 ? "hace " + fmtAgo(System.currentTimeMillis() - gd.lastConvMs) : "—");

        lblTime.setText(LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")));
    }

    // ════════════════════════════════════════════════════════════════════════
    //  Parsing helpers
    // ════════════════════════════════════════════════════════════════════════
    private long parseTimestamp(String line) {
        // ISO-8601: "timestamp":"2026-03-05T03:45:23.500Z"
        Matcher m1 = Pattern.compile("\"timestamp\":\"(\\d{4}-\\d{2}-\\d{2}T[\\d:.]+Z?)\"").matcher(line);
        if (m1.find()) {
            try { return Instant.parse(m1.group(1)).toEpochMilli(); }
            catch (Exception ignored) {}
        }
        // Epoch ms: "timestamp":1772682323485
        Matcher m2 = Pattern.compile("\"timestamp\":(\\d{13})").matcher(line);
        if (m2.find()) {
            try { return Long.parseLong(m2.group(1)); }
            catch (Exception ignored) {}
        }
        return 0;
    }

    private int extractInt(String text, String regex) {
        Matcher m = Pattern.compile(regex).matcher(text);
        if (m.find()) { try { return Integer.parseInt(m.group(1)); } catch (Exception ignored) {} }
        return 0;
    }

    private String extractStr(String text, String regex) {
        Matcher m = Pattern.compile(regex).matcher(text);
        return m.find() ? m.group(1) : "";
    }

    // ════════════════════════════════════════════════════════════════════════
    //  Format helpers
    // ════════════════════════════════════════════════════════════════════════
    private String fmtK(int n) {
        return n >= 1000 ? String.format("%.1fk", n / 1000.0) : String.valueOf(n);
    }

    private String fmtDuration(long ms) {
        long s = ms / 1000, h = s / 3600, m = (s % 3600) / 60;
        return h > 0 ? h + "h " + m + "m" : m + "m " + (s % 60) + "s";
    }

    private String fmtAgo(long ms) {
        long s = ms / 1000;
        if (s < 60)  return s + "s";
        long m = s / 60;
        if (m < 60)  return m + "m";
        long h = m / 60;
        return h + "h " + (m % 60) + "m";
    }

    // ════════════════════════════════════════════════════════════════════════
    //  Layout / widget micro-helpers
    // ════════════════════════════════════════════════════════════════════════
    private JLabel lbl(String t, int sz, int style, Color c) {
        JLabel l = new JLabel(t);
        l.setFont(new Font("Segoe UI", style, sz));
        l.setForeground(c);
        return l;
    }

    private JPanel hPanel() {
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setLayout(new BoxLayout(p, BoxLayout.X_AXIS));
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 18));
        return p;
    }

    private JPanel vPanel() {
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        return p;
    }

    private Component vGap(int h) { return Box.createVerticalStrut(h); }
    private Component hGap(int w) { return Box.createHorizontalStrut(w); }

    // ════════════════════════════════════════════════════════════════════════
    //  Custom components
    // ════════════════════════════════════════════════════════════════════════

    /** Panel con fondo redondeado y semitransparente. */
    static class RoundPanel extends JPanel {
        private final Color bg;
        private final int   arc;
        RoundPanel(Color bg, int arc) {
            this.bg = bg; this.arc = arc;
            setOpaque(false);
        }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(bg);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), arc, arc);
            g2.dispose();
        }
    }

    /** Barra de progreso pintada a mano (delgada, redondeada, coloreada). */
    static class ColorBar extends JComponent {
        private int   pct  = 0;
        private Color fill;
        private final Color track;

        ColorBar(Color fill, Color track) {
            this.fill = fill; this.track = track;
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 6));
            setPreferredSize(new Dimension(100, 6));
        }

        void set(int pct, Color fill) {
            this.pct  = pct;
            this.fill = fill;
            repaint();
        }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int h = getHeight(), w = getWidth();
            // Track
            g2.setColor(track);
            g2.fillRoundRect(0, 0, w, h, h, h);
            // Fill
            int fw = (int)(w * pct / 100.0);
            if (fw > 0) {
                g2.setColor(fill);
                g2.fillRoundRect(0, 0, fw, h, h, h);
            }
            g2.dispose();
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    //  Data containers
    // ════════════════════════════════════════════════════════════════════════
    static class ClaudeData {
        int  inputTokens, outputTokens, cacheCreateTokens;
        long lastMs, windowStart;
    }

    static class GeminiData {
        boolean daemonAlive;
        int     pid, httpPort;
        String  csrfToken = "", rawResponse = "", error = "";
        int     quotaUsed, quotaLimit, quotaRemaining;
        int     convCount;
        long    lastConvMs, daemonFileAge;
    }
}
