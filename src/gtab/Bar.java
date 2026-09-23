package gtab;

import javax.microedition.lcdui.Font;
import javax.microedition.lcdui.Graphics;

final class Bar {
	static final String MENU = "#m", BACK = "#b", PLAY = "#p", STOP = "#s", PREV = "#<", NEXT = "#>", SEARCH = "#f";

	private static final Font FB = Font.getFont(Font.FACE_PROPORTIONAL, Font.STYLE_BOLD, Font.SIZE_SMALL);
	private static final Font FS = Font.getFont(Font.FACE_PROPORTIONAL, Font.STYLE_PLAIN, Font.SIZE_SMALL);

	static int height() {
		int h = FB.getHeight() + FB.getHeight() / 2 + 10;
		return h < 30 ? 30 : h;
	}

	static int hit(int x, int w, int n) {
		int i = x * n / w;
		return i < 0 ? 0 : i >= n ? n - 1 : i;
	}

	static void draw(Graphics g, int y, int w, int h, String[] items, boolean[] off, int pressed) {
		int n = items.length;
		g.setColor(0xD0D0D0);
		g.fillRect(0, y, w, h);
		for (int i = 0; i < n; i++) {
			int x0 = w * i / n + 2, x1 = w * (i + 1) / n - 2, bw = x1 - x0, by = y + 3, bh = h - 6;
			boolean dis = off != null && off[i];
			g.setColor(i == pressed && !dis ? 0xB8CCEE : 0xF6F6F6);
			g.fillRoundRect(x0, by, bw, bh, 8, 8);
			g.setColor(0x9A9A9A);
			g.drawRoundRect(x0, by, bw, bh, 8, 8);
			g.setColor(dis ? 0xB0B0B0 : 0x202020);
			String it = items[i];
			int cx = (x0 + x1) / 2, cy = by + bh / 2;
			if (it.startsWith("#")) icon(g, it.charAt(1), cx, cy, Math.min(bw, bh) * 2 / 5);
			else label(g, it, cx, cy, bw - 6);
		}
	}

	private static void label(Graphics g, String s, int cx, int cy, int max) {
		Font f = FB.stringWidth(s) <= max ? FB : FS;
		String t = f.stringWidth(s) <= max ? s : View.fit(s, FS, max);
		g.setFont(f);
		g.drawString(t, cx, cy - f.getHeight() / 2, Graphics.TOP | Graphics.HCENTER);
	}

	private static void icon(Graphics g, char c, int cx, int cy, int r) {
		if (r < 5) r = 5;
		int t = r / 4 + 1;
		switch (c) {
			case 'm':
				for (int i = -1; i <= 1; i++) g.fillRect(cx - r, cy + i * (r * 2 / 3) - t / 2, 2 * r, t);
				break;
			case 'b':
				g.fillRect(cx - r / 2, cy - t / 2, r * 3 / 2, t);
				g.fillTriangle(cx - r, cy, cx - r / 3, cy - r * 2 / 3, cx - r / 3, cy + r * 2 / 3);
				break;
			case 'p':
				g.fillTriangle(cx - r * 2 / 3, cy - r, cx - r * 2 / 3, cy + r, cx + r, cy);
				break;
			case 's':
				g.fillRect(cx - r * 3 / 4, cy - r * 3 / 4, r * 3 / 2, r * 3 / 2);
				break;
			case '<':
				g.fillRect(cx - r, cy - r * 3 / 4, t, r * 3 / 2);
				g.fillTriangle(cx - r + t, cy, cx + r * 2 / 3, cy - r * 3 / 4, cx + r * 2 / 3, cy + r * 3 / 4);
				break;
			case '>':
				g.fillRect(cx + r - t, cy - r * 3 / 4, t, r * 3 / 2);
				g.fillTriangle(cx + r - t, cy, cx - r * 2 / 3, cy - r * 3 / 4, cx - r * 2 / 3, cy + r * 3 / 4);
				break;
			case 'f': {
				int rr = r * 2 / 3;
				for (int k = 0; k < t; k++) g.drawArc(cx - rr - r / 4 + k / 2, cy - rr - r / 4 + k / 2, 2 * rr - k, 2 * rr - k, 0, 360);
				for (int k = -t / 2; k <= t / 2; k++) g.drawLine(cx + rr / 2 + k, cy + rr / 2, cx + r + k, cy + r);
				break;
			}
		}
	}
}
