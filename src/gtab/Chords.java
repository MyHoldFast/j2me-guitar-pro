package gtab;

import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Font;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.TextBox;
import javax.microedition.lcdui.TextField;

final class Chords extends Canvas implements CommandListener {
	private static final int[] STD = { 64, 59, 55, 50, 45, 40 };

	private final App app;
	private final Font fs = Font.getFont(Font.FACE_PROPORTIONAL, Font.STYLE_PLAIN, Font.SIZE_SMALL);
	private final Font fb = Font.getFont(Font.FACE_PROPORTIONAL, Font.STYLE_BOLD, Font.SIZE_SMALL);
	private final Font fl = Font.getFont(Font.FACE_PROPORTIONAL, Font.STYLE_BOLD, Font.SIZE_LARGE);
	private final TextBox box = new TextBox(L.s("Аккорд: Am7, F#m, C/G", "Chord: Am7, F#m, C/G"), "", 16, TextField.ANY);
	private final Command ok = new Command("OK", Command.OK, 1);
	private final Command cancel = new Command(L.s("Назад", "Back"), Command.BACK, 2);

	private ChordLib lib;
	private int[] trackTune;
	private boolean useTrack;
	private int root, type, bass = -1, var, headY, diagY, diagH;
	private String msg;
	private final int barH, rowH;
	private int pressed = -1, px;

	Chords(App app) {
		this.app = app;
		setFullScreenMode(true);
		barH = hasPointerEvents() ? Bar.height() : fs.getHeight() + 8;
		rowH = hasPointerEvents() ? Bar.height() : 0;
		box.addCommand(ok);
		box.addCommand(cancel);
		box.setCommandListener(this);
	}

	synchronized void open(byte[] tune, String chord) {
		Snd.stop();
		msg = null;
		if (tune != null && tune.length >= 4) {
			trackTune = new int[tune.length];
			for (int i = 0; i < tune.length; i++) trackTune[i] = tune[i];
		} else trackTune = null;
		useTrack = trackTune != null;
		lib = null;
		int[] c = chord == null ? null : ChordLib.parse(chord);
		if (c != null) {
			root = c[0];
			type = c[1];
			bass = c[2];
		}
		rebuild();
	}

	private void rebuild() {
		int[] tn = useTrack ? trackTune : STD;
		if (lib == null || lib.tune != tn) lib = new ChordLib(tn);
		lib.build(root, type, bass);
		var = 0;
	}

	private String tuning() {
		StringBuffer b = new StringBuffer();
		for (int i = lib.n - 1; i >= 0; i--) {
			String nn = ChordLib.NOTE[lib.tune[i] % 12];
			b.append(nn);
		}
		return b.toString();
	}

	protected synchronized void paint(Graphics g) {
		int w = getWidth(), h = getHeight(), fh = fs.getHeight();
		g.setColor(0xFFFFFF);
		g.fillRect(0, 0, w, h);
		int y = 2;
		g.setFont(fs);
		g.setColor(0x606060);
		String tn = tuning() + (trackTune != null ? (useTrack ? L.s(" (трек)", " (track)") : L.s(" (станд.)", " (std)")) : "");
		if (fs.stringWidth(L.s("Аккорды", "Chords")) + fs.stringWidth(tn) + 10 <= w) g.drawString(L.s("Аккорды", "Chords"), 3, y, Graphics.TOP | Graphics.LEFT);
		g.drawString(tn, w - 3, y, Graphics.TOP | Graphics.RIGHT);
		y += fh;
		g.setFont(fl);
		g.setColor(0x000000);
		g.drawString(ChordLib.name(root, type, bass), w / 2, y, Graphics.TOP | Graphics.HCENTER);
		y += fl.getHeight();
		headY = y;
		g.setFont(fs);
		g.setColor(0x0000A0);
		g.drawString(View.fit(ChordLib.notes(root, type, bass), fs, w - 6), w / 2, y, Graphics.TOP | Graphics.HCENTER);
		y += fh + 2;
		String err = msg != null ? msg : Snd.error();
		boolean hints = h >= 200 && !hasPointerEvents();
		int foot = h - barH - rowH - fh * (hints ? 2 : 1) - 4 - (err != null ? fh : 0);
		diagY = y;
		diagH = foot - y;
		if (lib.count == 0) {
			g.setColor(0x000000);
			g.drawString(L.s("Нет аппликатуры в этом строе", "No fingering in this tuning"), w / 2, y + diagH / 2, Graphics.TOP | Graphics.HCENTER);
		} else diagram(g, w, y, foot - y);
		g.setFont(fs);
		int fy = foot + 2;
		if (err != null) {
			g.setColor(0xC00000);
			g.drawString(View.fit(err, fs, w - 6), w / 2, fy, Graphics.TOP | Graphics.HCENTER);
			fy += fh;
		}
		if (lib.count > 0) {
			byte[] fr = lib.fr[var];
			StringBuffer b = new StringBuffer();
			for (int s = lib.n - 1; s >= 0; s--) {
				if (b.length() > 0) b.append(' ');
				b.append(fr[s] < 0 ? "x" : String.valueOf(fr[s]));
			}
			String cnt = (var + 1) + "/" + lib.count, fr0 = b.toString();
			int room = w - 12 - fs.stringWidth(cnt);
			Font ff = fb.stringWidth(fr0) <= room ? fb : fs;
			if (ff.stringWidth(fr0) > room) {
				StringBuffer c = new StringBuffer();
				int prev = 0;
				for (int s = lib.n - 1; s >= 0; s--) {
					if (c.length() > 0 && (prev > 9 || fr[s] > 9)) c.append(' ');
					c.append(fr[s] < 0 ? "x" : String.valueOf(fr[s]));
					prev = fr[s];
				}
				fr0 = c.toString();
			}
			boolean showCnt = ff.stringWidth(fr0) <= room;
			if (!showCnt) {
				ff = fb.stringWidth(fr0) <= w - 6 ? fb : fs;
				room = w - 6;
			}
			g.setColor(0x000000);
			g.setFont(ff);
			g.drawString(View.fit(fr0, ff, room), 3, fy, Graphics.TOP | Graphics.LEFT);
			if (showCnt) {
				g.setFont(fs);
				g.setColor(0x606060);
				g.drawString(cnt, w - 3, fy, Graphics.TOP | Graphics.RIGHT);
			}
		}
		fy += fh;
		g.setColor(0x606060);
		if (hints) g.drawString(View.fit(fs.stringWidth(L.s("4/6 вариант  1/3 тоника  2/8 тип", "4/6 voicing  1/3 root  2/8 type")) <= w - 6 ? L.s("4/6 вариант  1/3 тоника  2/8 тип", "4/6 voicing  1/3 root  2/8 type") : L.s("4/6 вар  1/3 тон  2/8 тип", "4/6 var  1/3 root  2/8 type"), fs, w - 6), w / 2, fy, Graphics.TOP | Graphics.HCENTER);
		if (rowH > 0) {
			int T = ChordLib.TYPE.length;
			Bar.draw(g, h - barH - rowH, w, rowH, new String[] { "< " + ChordLib.ROOT[(root + 11) % 12], ChordLib.ROOT[(root + 1) % 12] + " >",
					"< " + tname((type + T - 1) % T), tname((type + 1) % T) + " >" }, null, pressed >= 10 ? pressed - 10 : -1);
		}
		Bar.draw(g, h - barH, w, barH, new String[] { Bar.SEARCH, Bar.PLAY, Bar.BACK }, null, pressed < 10 ? pressed : -1);
	}

	private static String tname(int t) {
		return t == 0 ? L.s("мажор", "major") : ChordLib.TYPE[t];
	}

	private void diagram(Graphics g, int w, int top, int ah) {
		byte[] fr = lib.fr[var], fg = lib.fg[var];
		int n = lib.n, minF = 99, maxF = 0;
		for (int s = 0; s < n; s++) {
			if (fr[s] > 0 && fr[s] < minF) minF = fr[s];
			if (fr[s] > maxF) maxF = fr[s];
		}
		int start = maxF <= 5 ? 1 : minF, rows = 5;
		String lab = start > 1 ? start + "fr" : "";
		int labW = fs.stringWidth("12fr") + 6;
		int sx = (w - 2 * labW) / (n - 1);
		int fy = ah / (rows + 1);
		if (fy > sx * 3 / 2) fy = sx * 3 / 2;
		if (sx > fy * 3 / 2) sx = fy * 3 / 2;
		int r = (sx < fy ? sx : fy) * 2 / 5;
		if (r < 3) r = 3;
		if ((w - sx * (n - 1)) / 2 < labW + r) {
			sx = (w - 2 * (labW + r)) / (n - 1);
			if (fy > sx * 3 / 2) fy = sx * 3 / 2;
			r = (sx < fy ? sx : fy) * 2 / 5;
			if (r < 3) r = 3;
		}
		int gw = sx * (n - 1), x0 = (w - gw) / 2, y0 = top + fy / 2 + (ah - fy * (rows + 1)) / 2 + fy / 2;
		g.setColor(0x000000);
		for (int i = 0; i <= rows; i++) g.drawLine(x0, y0 + i * fy, x0 + gw, y0 + i * fy);
		for (int c = 0; c < n; c++) g.drawLine(x0 + c * sx, y0, x0 + c * sx, y0 + rows * fy);
		if (start == 1) g.fillRect(x0, y0 - 2, gw + 1, 3);
		g.setFont(fs);
		if (start > 1) g.drawString(lab, x0 - r - 3, y0 + fy / 2 - fs.getHeight() / 2, Graphics.TOP | Graphics.RIGHT);
		int mk = r / 2 + 1, my = y0 - fy / 2;
		if (mk > fy / 4 + 1) mk = fy / 4 + 1;
		for (int s = 0; s < n; s++) {
			int cx = x0 + (n - 1 - s) * sx;
			if (fr[s] < 0) {
				g.drawLine(cx - mk, my - mk, cx + mk, my + mk);
				g.drawLine(cx - mk, my + mk, cx + mk, my - mk);
			} else if (fr[s] == 0) g.drawArc(cx - mk, my - mk, mk * 2, mk * 2, 0, 360);
		}
		int bf = lib.bar[var * 3];
		if (bf > 0) {
			int lo = lib.bar[var * 3 + 1], hi = lib.bar[var * 3 + 2];
			int bx0 = x0 + (n - 1 - lo) * sx, bx1 = x0 + (n - 1 - hi) * sx, by = y0 + (bf - start) * fy + fy / 2;
			g.fillRoundRect(bx0 - r, by - r, bx1 - bx0 + 2 * r, 2 * r, 2 * r, 2 * r);
		}
		g.setFont(fb);
		int dh = fb.getHeight();
		for (int s = 0; s < n; s++) {
			if (fr[s] <= 0) continue;
			int cx = x0 + (n - 1 - s) * sx, cy = y0 + (fr[s] - start) * fy + fy / 2;
			g.setColor(0x000000);
			g.fillArc(cx - r, cy - r, 2 * r, 2 * r, 0, 360);
			if (fg[s] > 0 && r * 2 >= dh - 4) {
				g.setColor(0xFFFFFF);
				g.drawString(String.valueOf(fg[s]), cx, cy - dh / 2, Graphics.TOP | Graphics.HCENTER);
			}
		}
	}

	private void sound(int step) {
		if (lib.count == 0) return;
		byte[] fr = lib.fr[var];
		int[] keys = new int[lib.n];
		int c = 0, low = 127;
		for (int s = lib.n - 1; s >= 0; s--) {
			if (fr[s] < 0) continue;
			keys[c++] = lib.tune[s] + fr[s];
		}
		for (int s = 0; s < lib.n; s++) if (lib.tune[s] < low) low = lib.tune[s];
		Snd.play(Snd.notes(keys, c, step, step > 100 ? 2400 : 3600, low < 36 ? 33 : 25), this);
	}

	protected void keyPressed(int k) {
		key(k, false);
	}

	protected void keyRepeated(int k) {
		key(k, true);
	}

	private int action(int k) {
		try {
			return getGameAction(k);
		} catch (IllegalArgumentException e) {
			return 0;
		}
	}

	private synchronized void key(int k, boolean rep) {
		msg = null;
		if (k == -7 || k == -22) {
			if (!rep) {
				Snd.stop();
				app.nav(App.MENU);
			}
			return;
		}
		if (k == -6 || k == -21 || k == KEY_POUND) {
			if (!rep) search();
			return;
		}
		int a = action(k), cnt = lib.count;
		if (k == KEY_NUM4 || a == LEFT) {
			if (cnt > 0) var = (var + cnt - 1) % cnt;
			if (!rep) sound(50);
		} else if (k == KEY_NUM6 || a == RIGHT) {
			if (cnt > 0) var = (var + 1) % cnt;
			if (!rep) sound(50);
		} else if (k == KEY_NUM2 || a == UP) {
			type = (type + ChordLib.TYPE.length - 1) % ChordLib.TYPE.length;
			rebuild();
		} else if (k == KEY_NUM8 || a == DOWN) {
			type = (type + 1) % ChordLib.TYPE.length;
			rebuild();
		} else if (k == KEY_NUM1) {
			root = (root + 11) % 12;
			rebuild();
		} else if (k == KEY_NUM3) {
			root = (root + 1) % 12;
			rebuild();
		} else if (rep) {
			return;
		} else if (k == KEY_NUM5 || a == FIRE) sound(50);
		else if (k == KEY_NUM0) sound(300);
		else if (k == KEY_STAR && trackTune != null) {
			useTrack = !useTrack;
			rebuild();
		}
		repaint();
	}

	protected synchronized void pointerPressed(int x, int y) {
		int w = getWidth(), h = getHeight();
		px = x;
		pressed = -1;
		if (y < headY) search();
		else if (y >= h - barH) pressed = Bar.hit(x, w, 3);
		else if (rowH > 0 && y >= h - barH - rowH) pressed = 10 + Bar.hit(x, w, 4);
		else if (y >= diagY && y < diagY + diagH) pressed = 20;
		repaint();
	}

	protected synchronized void pointerReleased(int x, int y) {
		int i = pressed, w = getWidth(), h = getHeight();
		pressed = -1;
		repaint();
		if (i == 20) {
			int dx = x - px;
			if (dx > w / 6 || (Math.abs(dx) <= w / 6 && px < w / 4)) key(KEY_NUM4, false);
			else if (dx < -w / 6 || px > w * 3 / 4) key(KEY_NUM6, false);
			else key(KEY_NUM5, false);
		} else if (i >= 10) {
			if (y < h - barH - rowH || y >= h - barH || Bar.hit(x, w, 4) != i - 10) return;
			key(i == 10 ? KEY_NUM1 : i == 11 ? KEY_NUM3 : i == 12 ? KEY_NUM2 : KEY_NUM8, false);
		} else if (i >= 0) {
			if (y < h - barH || Bar.hit(x, w, 3) != i) return;
			if (i == 0) search();
			else if (i == 1) key(KEY_NUM5, false);
			else key(-7, false);
		}
	}

	protected void hideNotify() {
		Snd.stop();
	}

	private void search() {
		box.setString("");
		app.show(box);
	}

	public void commandAction(Command c, Displayable d) {
		if (c == ok) {
			String q = box.getString().trim();
			synchronized (this) {
				int[] p = ChordLib.parse(q);
				if (p == null) msg = q.length() > 0 ? L.s("Не понял: ", "Unknown: ") + q : null;
				else {
					root = p[0];
					type = p[1];
					bass = p[2];
					rebuild();
					if (lib.count > 0) sound(50);
				}
			}
		}
		app.show(this);
	}
}
