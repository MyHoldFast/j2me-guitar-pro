package gtab;

import java.util.Vector;

import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Font;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;

final class Help extends Canvas implements Fling.Target {
	private static final int BG = 0xFFFFFF, FG = 0x000000, LINE = 0x808080, TXT = 0x0000A0, MARK = 0xA00000;
	private static final int TITLE = 0, HEAD = 1, TEXT = 2, SAMPLE = 3;

	private final App app;
	private final Font f = Font.getFont(Font.FACE_PROPORTIONAL, Font.STYLE_PLAIN, Font.SIZE_SMALL);
	private final Font fb = Font.getFont(Font.FACE_PROPORTIONAL, Font.STYLE_BOLD, Font.SIZE_SMALL);
	private final int fh = f.getHeight();
	private final int sc, gap, stem, bar;
	private final Image[] dig;
	private final Vector kinds = new Vector(), texts = new Vector();
	private String[][] lines;
	private int[] tops;
	private int laidW = -1, total, sy, pressed = -1;
	private final Fling fling = new Fling(this, this);

	Help(App app, String ver) {
		this.app = app;
		setFullScreenMode(true);
		int m = Math.min(getWidth(), getHeight());
		sc = m >= 360 ? 3 : m >= 240 ? 2 : 1;
		gap = 6 * sc + 2;
		stem = 7 * sc;
		bar = hasPointerEvents() ? Bar.height() : 0;
		dig = new Image[View.GLYPH.length];
		for (int i = 0; i < dig.length; i++) {
			Image im = Image.createImage(3 * sc, 5 * sc);
			Graphics g = im.getGraphics();
			g.setColor(BG);
			g.fillRect(0, 0, 3 * sc, 5 * sc);
			g.setColor(FG);
			for (int k = 0; k < 15; k++)
				if ((View.GLYPH[i] & (1 << (14 - k))) != 0) g.fillRect((k % 3) * sc, (k / 3) * sc, sc, sc);
			dig[i] = im;
		}
		add(TITLE, "GTab " + (ver == null ? "" : ver));
		add(TEXT, L.s("Просмотр табулатур Guitar Pro 3/4/5", "Guitar Pro 3/4/5 tab viewer"));
		add(HEAD, L.s("Управление", "Controls"));
		add(TEXT, L.s("4/6, джойстик влево/вправо - курсор по битам\n2/8, джойстик вверх/вниз - строка выше/ниже\n"
				+ "5, джойстик - играть/стоп\n7/9 - страница вверх/вниз\n1/3 - предыдущая/следующая дорожка\n"
				+ "0 - в начало\n* - масштаб\n# - поворот экрана\nсофт-клавиши - меню",
				"4/6, joystick left/right - move cursor by beat\n2/8, joystick up/down - previous/next line\n"
				+ "5, joystick - play/stop\n7/9 - page up/down\n1/3 - previous/next track\n"
				+ "0 - go to start\n* - zoom\n# - rotate screen\nsoft keys - menu"));
		add(TEXT, L.s("Тач: тап ставит курсор, протяжка листает, кнопки внизу экрана.",
				"Touch: tap sets the cursor, drag scrolls, buttons at the bottom."));
		add(HEAD, L.s("Воспроизведение", "Playback"));
		add(TEXT, L.s("Воспроизведение начинается с такта под курсором. \u00abСоло\u00bb - играть только текущую дорожку. "
				+ "Если нет звука, проверьте профиль телефона: в беззвучном режиме Java-приложениям звук запрещён.",
				"Playback starts from the bar under the cursor. \"Solo\" plays only the current track. "
				+ "No sound? Check the phone profile: in silent mode Java apps are not allowed to play sound."));
		add(HEAD, L.s("Тюнер", "Tuner"));
		add(TEXT, L.s("Играет эталонные ноты струн по строю текущей дорожки. 5 - звук вкл/выкл, 2/8 - струна, "
				+ "1/3 - другой строй, 4/6 - подстроить струну на полтона, 0 - сброс, * - на октаву выше, "
				+ "# - гитара или чистый тон. Красная цифра у струны - на сколько полутонов её перестроить относительно "
				+ "строя трека. Низкие ноты чистым тоном динамик почти не воспроизводит: включите октаву выше "
				+ "и сверяйте с флажолетом на 12 ладу.",
				"Plays reference notes for the strings of the current track tuning. 5 - sound on/off, 2/8 - string, "
				+ "1/3 - another tuning, 4/6 - retune the string by a semitone, 0 - reset, * - octave up, "
				+ "# - guitar or pure tone. The red number next to a string shows how many semitones to retune it "
				+ "relative to the track. Phone speakers barely play low pure tones: use octave up and compare "
				+ "with the 12th fret harmonic."));
		add(HEAD, L.s("Аккорды", "Chords"));
		add(TEXT, L.s("Аппликатуры любых аккордов под строй дорожки. Открываются на аккорде, подписанном над курсором. "
				+ "Поиск - левая софт-клавиша или #: Am7, F#m, Cmaj7, Dsus4, Bb, Hm, C/G. 4/6 - варианты по грифу, "
				+ "1/3 - тоника, 2/8 - тип, 5 - бой, 0 - арпеджио, * - строй трека или стандартный.",
				"Fingerings for any chord in the track tuning. Opens on the chord written above the cursor. "
				+ "Search - left soft key or #: Am7, F#m, Cmaj7, Dsus4, Bb, Hm, C/G. 4/6 - voicings along the neck, "
				+ "1/3 - root, 2/8 - type, 5 - strum, 0 - arpeggio, * - track or standard tuning."));
		add(HEAD, L.s("Поиск табов", "Search tabs"));
		add(TEXT, L.s("База gtp-tabs на сервере. Можно писать по-русски или транслитом. Выбранный таб сохраняется "
				+ "в папку загрузок и сразу открывается. Файлы GPX/GP (Guitar Pro 6-8) в списке файлов отправляются "
				+ "на сервер и возвращаются в формате GP5 рядом с оригиналом.",
				"The gtp-tabs database on the server. Type in English, Russian or transliteration. The chosen tab "
				+ "is saved to the download folder and opened. GPX/GP files (Guitar Pro 6-8) in the file list are sent "
				+ "to the server and come back as GP5 next to the original."));
		add(HEAD, L.s("Экспорт MIDI", "Export MIDI"));
		add(TEXT, L.s("Меню, когда открыт таб: отметьте нужные дорожки и нажмите \u00abСохранить\u00bb. "
				+ "Файл .mid сохраняется рядом с табом или в папку загрузок.",
				"Menu, when a tab is open: check the tracks you need and choose \"Save\". "
				+ "The .mid file is saved next to the tab or to the download folder."));
		add(HEAD, L.s("Настройки", "Settings"));
		add(TEXT, L.s("Адрес сервера, папка для скачанных табов и язык. Если папка не выбрана, при первом скачивании "
				+ "откроется её выбор: зайдите в папку и нажмите \u00abСохранять сюда\u00bb. Подсветка не гаснет, "
				+ "пока приложение на экране. Кодировка названий определяется автоматически, переключить можно в меню.",
				"Server address, download folder and language. If no folder is set, the first download asks for it: "
				+ "open a folder and choose \"Save here\". The backlight stays on while the app is on screen. "
				+ "Title encoding is detected automatically and can be switched in the menu."));
		add(TITLE, L.s("Условные обозначения", "Tab notation"));
		int[] ids = { -1, 1, 2, 3, 4, 5, 30, -2, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, -3, 18, 19, 20, 21, 17, 22,
				-4, 23, 24, 25, 26, 27, 28, 29 };
		for (int i = 0; i < ids.length; i++) {
			if (ids[i] < 0) add(HEAD, cap(ids[i]));
			else add(SAMPLE, String.valueOf(ids[i]));
		}
	}

	private void add(int kind, String text) {
		kinds.addElement(new Integer(kind));
		texts.addElement(text);
	}

	private String[] wrap(String s, int max) {
		Vector out = new Vector();
		int sp = f.stringWidth(" "), start = 0;
		while (start <= s.length()) {
			int nl = s.indexOf('\n', start);
			String para = nl < 0 ? s.substring(start) : s.substring(start, nl);
			StringBuffer line = new StringBuffer();
			int lw = 0, p = 0;
			while (p <= para.length()) {
				int q = para.indexOf(' ', p);
				if (q < 0) q = para.length();
				String word = para.substring(p, q);
				int ww = f.stringWidth(word);
				if (line.length() > 0 && lw + sp + ww > max) {
					out.addElement(line.toString());
					line.setLength(0);
					lw = 0;
				}
				if (line.length() > 0) {
					line.append(' ');
					lw += sp;
				}
				line.append(word);
				lw += ww;
				p = q + 1;
			}
			out.addElement(line.toString());
			if (nl < 0) break;
			start = nl + 1;
		}
		String[] r = new String[out.size()];
		out.copyInto(r);
		return r;
	}

	private boolean narrow() {
		return getWidth() < 200;
	}

	private int sampleW() {
		int w = getWidth();
		return narrow() ? w - 4 : Math.max(w * 2 / 5, 20 * sc);
	}

	private int box() {
		return fh + 2 * gap + 3 * sc + stem + 5 * sc + 6;
	}

	private void layout() {
		int w = getWidth(), n = kinds.size(), ai = -1, frac = 0;
		if (tops != null && sy > 0) {
			for (int i = 0; i < n; i++) {
				if (tops[i + 1] > sy) {
					ai = i;
					frac = (sy - tops[i]) * 1000 / Math.max(1, tops[i + 1] - tops[i]);
					break;
				}
			}
		}
		laidW = w;
		lines = new String[n][];
		tops = new int[n + 1];
		int y = 4;
		for (int i = 0; i < n; i++) {
			int k = ((Integer) kinds.elementAt(i)).intValue();
			String t = (String) texts.elementAt(i);
			tops[i] = y;
			if (k == TITLE) y += fb.getHeight() + 10;
			else if (k == HEAD) y += fb.getHeight() + 8;
			else if (k == TEXT) {
				lines[i] = wrap(t, w - 12);
				y += lines[i].length * fh + 6;
			} else {
				lines[i] = wrap(cap(Integer.parseInt(t)), narrow() ? w - 12 : w - sampleW() - 8);
				y += narrow() ? box() + lines[i].length * fh + 6 : Math.max(box(), lines[i].length * fh + 6);
			}
		}
		tops[n] = y;
		total = y + 4;
		if (ai >= 0) {
			sy = tops[ai] + (tops[ai + 1] - tops[ai]) * frac / 1000;
			int max = total - (getHeight() - bar);
			if (sy > max) sy = max;
			if (sy < 0) sy = 0;
		}
	}

	protected synchronized void paint(Graphics g) {
		int w = getWidth(), h = getHeight() - bar;
		if (laidW != w) layout();
		g.setColor(BG);
		g.fillRect(0, 0, w, getHeight());
		g.setClip(0, 0, w, h);
		int n = kinds.size(), sw = sampleW();
		for (int i = 0; i < n; i++) {
			int y = tops[i] - sy, rh = tops[i + 1] - tops[i];
			if (y + rh < 0) continue;
			if (y >= h) break;
			int k = ((Integer) kinds.elementAt(i)).intValue();
			String t = (String) texts.elementAt(i);
			if (k == TITLE) {
				g.setFont(fb);
				g.setColor(FG);
				g.drawString(View.fit(t, fb, w - 8), w / 2, y + 4, Graphics.TOP | Graphics.HCENTER);
			} else if (k == HEAD) {
				g.setColor(0xE8E8E8);
				g.fillRect(0, y + 2, w, rh - 4);
				g.setFont(fb);
				g.setColor(FG);
				g.drawString(t, 6, y + 4, Graphics.TOP | Graphics.LEFT);
			} else if (k == TEXT) {
				g.setFont(f);
				g.setColor(FG);
				for (int j = 0; j < lines[i].length; j++) g.drawString(lines[i][j], 6, y + 2 + j * fh, Graphics.TOP | Graphics.LEFT);
			} else {
				sample(g, Integer.parseInt(t), 4, sw, y);
				g.setFont(f);
				g.setColor(FG);
				String[] ls = lines[i];
				int ty = narrow() ? y + box() : y + (rh - ls.length * fh) / 2, tx = narrow() ? 6 : sw + 6;
				for (int j = 0; j < ls.length; j++) g.drawString(ls[j], tx, ty + j * fh, Graphics.TOP | Graphics.LEFT);
				g.setColor(0xE0E0E0);
				g.drawLine(4, y + rh - 1, w - 4, y + rh - 1);
			}
		}
		g.setClip(0, 0, w, getHeight());
		if (bar > 0) Bar.draw(g, h, w, bar, new String[] { Bar.BACK }, null, pressed);
	}

	protected void sizeChanged(int w, int h) {
		fling.stop();
		laidW = -1;
		repaint();
	}

	synchronized void top() {
		fling.stop();
		sy = 0;
	}

	private static String cap(int id) {
		switch (id) {
			case -1: return L.s("Ноты", "Notes");
			case -2: return L.s("Приёмы", "Techniques");
			case -3: return L.s("Ритм", "Rhythm");
			case -4: return L.s("Такты и разметка", "Bars and marks");
			case 1: return L.s("Номер лада на струне. Верхняя линия - первая (самая тонкая) струна", "Fret number on a string. The top line is the 1st (thinnest) string");
			case 2: return L.s("Приглушённая (мёртвая) нота", "Dead (muted) note");
			case 3: return L.s("Лига - нота продолжает звучать, или призрачная нота", "Tied note (keeps ringing) or ghost note");
			case 4: return L.s("Флажолет: NH - натуральный, AH - искусственный", "Harmonic: NH - natural, AH - artificial");
			case 5: return L.s("Форшлаг: маленькая нота перед основной", "Grace note before the main note");
			case 30: return L.s("Строй: ноты открытых струн в начале таба", "Tuning: open string notes at the start of the tab");
			case 6: return L.s("Хаммер-он / пулл-офф (легато)", "Hammer-on / pull-off (legato)");
			case 7: return L.s("Слайд - скольжение к следующей ноте", "Slide to the next note");
			case 8: return L.s("Бенд (подтяжка): 1/4, 1/2, full - на тон", "Bend: 1/4, 1/2, full = whole tone");
			case 9: return L.s("Вибрато", "Vibrato");
			case 10: return L.s("PM - глушение ладонью (palm mute)", "PM - palm mute");
			case 11: return L.s("LR - дать звучать (let ring)", "LR - let ring");
			case 12: return L.s("tr - трель; T, S, P - тэппинг, слэп, поп", "tr - trill; T, S, P - tapping, slap, pop");
			case 13: return L.s("Акцент, сильный акцент, стаккато", "Accent, heavy accent, staccato");
			case 14: return L.s("Удар медиатором вниз / вверх", "Pick stroke down / up");
			case 15: return L.s("Бой по струнам: стрелка показывает направление", "Strum: the arrow shows the direction");
			case 16: return L.s("w/bar - рычаг тремоло; клин - нарастание громкости", "w/bar - tremolo bar; wedge - fade in");
			case 17: return L.s("Тремоло медиатором (черты на штиле)", "Tremolo picking (slashes on the stem)");
			case 18: return L.s("Целая - без штиля, половинная - короткий штиль, четверть - штиль", "Whole - no stem, half - short stem, quarter - stem");
			case 19: return L.s("Восьмые и шестнадцатые: флажки или вязки", "Eighths and sixteenths: flags or beams");
			case 20: return L.s("Точка - длительность в полтора раза длиннее", "Dot - one and a half times longer");
			case 21: return L.s("Триоль и другие группы: цифра под штилями", "Triplet and other tuplets: number under the stems");
			case 22: return L.s("Паузы: целая, половинная, четверть, восьмая", "Rests: whole, half, quarter, eighth");
			case 23: return L.s("Реприза: играть отрывок повторно, x3 - сколько раз всего", "Repeat: play the section again, x3 - total times");
			case 24: return L.s("Вольта: такт для указанного прохода репризы", "Volta: bar for the given repeat pass");
			case 25: return L.s("Маркер части песни", "Section marker");
			case 26: return L.s("Размер такта и темп (ударов в минуту)", "Time signature and tempo (beats per minute)");
			case 27: return L.s("Номер такта", "Bar number");
			case 28: return L.s("Аккорд или текст над нотой", "Chord or text above the note");
			default: return L.s("Курсор; красный - во время воспроизведения", "Cursor; red during playback");
		}
	}

	private void staff(Graphics g, int x0, int x1, int y0, int n) {
		g.setColor(LINE);
		for (int i = 0; i < n; i++) g.drawLine(x0, y0 + i * gap, x1, y0 + i * gap);
		g.setColor(FG);
	}

	private int numW(int v) {
		return v > 9 ? 7 * sc : 3 * sc;
	}

	private void num(Graphics g, int v, int cx, int cy) {
		int nw = numW(v), x = cx - nw / 2, y = cy - (5 * sc) / 2;
		g.setColor(BG);
		g.fillRect(x - sc, y, nw + 2 * sc, 5 * sc);
		if (v > 9) {
			g.drawImage(dig[(v / 10) % 10], x, y, Graphics.TOP | Graphics.LEFT);
			x += 4 * sc;
		}
		g.drawImage(dig[v % 10], x, y, Graphics.TOP | Graphics.LEFT);
		g.setColor(FG);
	}

	private void glyph(Graphics g, int i, int cx, int cy) {
		int x = cx - (3 * sc) / 2, y = cy - (5 * sc) / 2;
		g.setColor(BG);
		g.fillRect(x - sc, y, 5 * sc, 5 * sc);
		g.drawImage(dig[i], x, y, Graphics.TOP | Graphics.LEFT);
		g.setColor(FG);
	}

	private void label(Graphics g, String s, int cx, int y) {
		g.setFont(f);
		g.setColor(FG);
		g.drawString(s, cx, y, Graphics.TOP | Graphics.HCENTER);
	}

	private void stemAt(Graphics g, int cx, int yEnd, int code, boolean dot) {
		int y1 = yEnd + 3 * sc, y2 = y1 + stem;
		g.setColor(FG);
		if (code == 0) return;
		g.drawLine(cx, code == 1 ? y1 + stem / 2 : y1, cx, y2);
		if (dot) g.fillRect(cx + sc + 1, y2 - sc, sc, sc);
		if (code >= 3 && code != 9)
			for (int i = 0; i < code - 2; i++) g.drawLine(cx, y2 - i * 2 * sc, cx + 2 * sc, y2 - i * 2 * sc - 2 * sc);
	}

	private void beam(Graphics g, int x1, int x2, int yEnd, int k) {
		int y2 = yEnd + 3 * sc + stem;
		for (int i = 0; i < k; i++) g.fillRect(x1, y2 - i * 2 * sc - sc, x2 - x1 + 1, sc);
	}

	private void sample(Graphics g, int id, int x0, int x1, int top) {
		int lab = top + 2, ys = top + fh + 4, mid = ys + gap, a = x0 + (x1 - x0) / 4, b = x0 + (x1 - x0) / 2, c = x0 + (x1 - x0) * 3 / 4;
		int hh = (5 * sc) / 2;
		g.setColor(FG);
		switch (id) {
			case 1:
				staff(g, x0, x1, ys, 3);
				num(g, 3, a, ys);
				num(g, 12, c, mid);
				break;
			case 2:
				staff(g, x0, x1, ys, 3);
				glyph(g, 10, b, mid);
				break;
			case 3: {
				staff(g, x0, x1, ys, 3);
				num(g, 5, b, mid);
				int xl = b - 3 * sc / 2 - sc - 1, xr = b + 3 * sc / 2 + sc;
				g.drawLine(xl, mid - hh + 1, xl, mid + hh - 1);
				g.drawLine(xr, mid - hh + 1, xr, mid + hh - 1);
				break;
			}
			case 4: {
				staff(g, x0, x1, ys, 3);
				num(g, 12, b, mid);
				int xl = b - 7 * sc / 2 - sc - 1, xr = b + 7 * sc / 2 + sc;
				g.drawLine(xl, mid, xl + sc + 1, mid - hh);
				g.drawLine(xl, mid, xl + sc + 1, mid + hh);
				g.drawLine(xr, mid, xr - sc - 1, mid - hh);
				g.drawLine(xr, mid, xr - sc - 1, mid + hh);
				label(g, "NH", b, lab);
				break;
			}
			case 5: {
				staff(g, x0, x1, ys, 3);
				num(g, 7, c, mid);
				int xl = c - 3 * sc / 2 - sc - 1;
				num(g, 5, xl - 2 * sc - 3 * sc / 2, mid - gap / 2);
				break;
			}
			case 30: {
				staff(g, x0 + 5 * sc, x1, ys, 3);
				g.drawLine(x0 + 5 * sc, ys, x0 + 5 * sc, ys + 2 * gap);
				glyph(g, 11 + 'E' - 'A', x0 + 3 * sc / 2, ys);
				glyph(g, 11 + 'B' - 'A', x0 + 3 * sc / 2, ys + gap);
				glyph(g, 11 + 'G' - 'A', x0 + 3 * sc / 2, ys + 2 * gap);
				break;
			}
			case 6:
			case 7: {
				staff(g, x0, x1, ys, 3);
				num(g, 5, a, mid);
				num(g, 7, c, mid);
				int xr = a + 3 * sc / 2 + sc, x2 = c - 3 * sc / 2 - sc;
				if (id == 6) g.drawArc(a, mid - gap / 2 - sc, x2 - a, gap / 2, 0, 180);
				else g.drawLine(xr, mid + sc, x2, mid - sc);
				break;
			}
			case 8: {
				staff(g, x0, x1, ys, 3);
				num(g, 7, a, mid);
				int xr = a + 3 * sc / 2 + sc, ax = xr + 2 * sc, by = lab + fh;
				g.drawLine(xr, mid, ax, by);
				g.drawLine(ax, by, ax - sc - 1, by + sc + 1);
				g.drawLine(ax, by, ax + sc + 1, by + sc + 1);
				g.setFont(f);
				g.drawString("full", ax, lab, Graphics.TOP | Graphics.LEFT);
				break;
			}
			case 9: {
				staff(g, x0, x1, ys, 3);
				num(g, 5, b, mid);
				int vy = lab + fh / 2, xs = b - 3 * sc;
				for (int i = 0; i < 3; i++) {
					g.drawLine(xs + i * 2 * sc, vy, xs + i * 2 * sc + sc, vy - sc);
					g.drawLine(xs + i * 2 * sc + sc, vy - sc, xs + i * 2 * sc + 2 * sc, vy);
				}
				break;
			}
			case 10:
			case 11: {
				staff(g, x0, x1, ys, 3);
				num(g, 0, a, ys + 2 * gap);
				num(g, 0, b, ys + 2 * gap);
				num(g, 0, c, ys + 2 * gap);
				String s = id == 10 ? "PM" : "LR";
				int my = lab + fh / 2, sx = a - 3 * sc, e = c + 3 * sc;
				g.setFont(f);
				g.drawString(s, sx, lab, Graphics.TOP | Graphics.LEFT);
				for (int d = sx + f.stringWidth(s) + sc; d < e; d += 3 * sc) g.drawLine(d, my, d + sc, my);
				g.drawLine(e, my - sc - 1, e, my + sc + 1);
				break;
			}
			case 12:
				staff(g, x0, x1, ys, 3);
				num(g, 5, a, mid);
				num(g, 12, c, ys);
				label(g, "tr", a, lab);
				label(g, "T", c, lab);
				break;
			case 13: {
				staff(g, x0, x1, ys, 3);
				num(g, 3, a, mid);
				num(g, 3, b, mid);
				num(g, 3, c, mid);
				int my = lab + fh / 2, x = a - 2 * sc;
				g.drawLine(x, my - sc - 1, x + 4 * sc, my);
				g.drawLine(x + 4 * sc, my, x, my + sc + 1);
				x = b - 2 * sc;
				g.drawLine(x, my + sc + 1, x + 2 * sc, my - sc - 1);
				g.drawLine(x + 2 * sc, my - sc - 1, x + 4 * sc, my + sc + 1);
				g.fillRect(c - sc, my - sc / 2, sc + 1, sc + 1);
				break;
			}
			case 14: {
				staff(g, x0, x1, ys, 3);
				num(g, 0, a, ys + 2 * gap);
				num(g, 0, c, ys + 2 * gap);
				int my = lab + fh / 2, x = a - 2 * sc;
				g.drawLine(x, my + sc + 1, x, my - sc - 1);
				g.drawLine(x, my - sc - 1, x + 4 * sc, my - sc - 1);
				g.drawLine(x + 4 * sc, my - sc - 1, x + 4 * sc, my + sc + 1);
				x = c - 2 * sc;
				g.drawLine(x, my - sc - 1, x + 2 * sc, my + sc + 1);
				g.drawLine(x + 2 * sc, my + sc + 1, x + 4 * sc, my - sc - 1);
				break;
			}
			case 15: {
				staff(g, x0, x1, ys, 3);
				for (int i = 0; i < 3; i++) {
					num(g, 2, b, ys + i * gap);
					num(g, 2, c + 2 * sc, ys + i * gap);
				}
				int ax = b - 4 * sc, ye = ys + 2 * gap;
				g.drawLine(ax, ys, ax, ye);
				g.drawLine(ax, ys, ax - sc - 1, ys + sc + 1);
				g.drawLine(ax, ys, ax + sc + 1, ys + sc + 1);
				ax = c + 2 * sc - 4 * sc;
				g.drawLine(ax, ys, ax, ye);
				g.drawLine(ax, ye, ax - sc - 1, ye - sc - 1);
				g.drawLine(ax, ye, ax + sc + 1, ye - sc - 1);
				break;
			}
			case 16: {
				staff(g, x0, x1, ys, 3);
				num(g, 5, a, mid);
				num(g, 7, c, mid);
				g.setFont(f);
				g.drawString("w/bar", a - 2 * sc, lab, Graphics.TOP | Graphics.LEFT);
				int my = lab + fh / 2, x = c - 3 * sc;
				g.drawLine(x, my, x + 8 * sc, my - sc - 1);
				g.drawLine(x, my, x + 8 * sc, my + sc + 1);
				break;
			}
			case 17:
				staff(g, x0, x1, ys, 1);
				num(g, 5, b, ys);
				stemAt(g, b, ys, 2, false);
				for (int i = 0; i < 3; i++) {
					int yy = ys + 3 * sc + stem / 2;
					g.drawLine(b - sc - 1, yy + i * 2 - 1, b + sc + 1, yy + i * 2 - 3);
				}
				break;
			case 18:
				staff(g, x0, x1, ys, 1);
				num(g, 3, a, ys);
				num(g, 3, b, ys);
				num(g, 3, c, ys);
				stemAt(g, b, ys, 1, false);
				stemAt(g, c, ys, 2, false);
				break;
			case 19:
				staff(g, x0, x1, ys, 1);
				num(g, 3, x0 + (x1 - x0) / 6, ys);
				num(g, 3, x0 + (x1 - x0) * 2 / 6, ys);
				num(g, 3, x0 + (x1 - x0) * 4 / 6, ys);
				num(g, 3, x0 + (x1 - x0) * 5 / 6, ys);
				stemAt(g, x0 + (x1 - x0) / 6, ys, 3, false);
				stemAt(g, x0 + (x1 - x0) * 2 / 6, ys, 4, false);
				stemAt(g, x0 + (x1 - x0) * 4 / 6, ys, 9, false);
				stemAt(g, x0 + (x1 - x0) * 5 / 6, ys, 9, false);
				beam(g, x0 + (x1 - x0) * 4 / 6, x0 + (x1 - x0) * 5 / 6, ys, 2);
				break;
			case 20:
				staff(g, x0, x1, ys, 1);
				num(g, 3, b, ys);
				stemAt(g, b, ys, 2, true);
				break;
			case 21: {
				staff(g, x0, x1, ys, 1);
				int[] xs = { a, b, c };
				for (int i = 0; i < 3; i++) {
					num(g, 3, xs[i], ys);
					stemAt(g, xs[i], ys, 9, false);
				}
				beam(g, a, c, ys, 1);
				num(g, 3, a, ys + 3 * sc + stem + 4 * sc);
				break;
			}
			case 22: {
				staff(g, x0, x1, ys, 3);
				int ry = mid, xs[] = { x0 + (x1 - x0) / 8, x0 + (x1 - x0) * 3 / 8, x0 + (x1 - x0) * 5 / 8, x0 + (x1 - x0) * 7 / 8 };
				g.fillRect(xs[0] - 2 * sc, ry - gap / 2, 4 * sc, sc + 1);
				g.fillRect(xs[1] - 2 * sc, ry - sc, 4 * sc, sc + 1);
				for (int i = 2; i < 4; i++) {
					int cx = xs[i];
					g.drawLine(cx - sc, ry - 2 * sc, cx + sc, ry);
					g.drawLine(cx + sc, ry, cx - sc, ry + 2 * sc);
					if (i == 3) g.fillRect(cx - sc, ry - 2 * sc - sc, sc + 1, sc);
				}
				break;
			}
			case 23: {
				staff(g, x0, x1, ys, 3);
				int yb = ys + 2 * gap, x = x0 + 2 * sc, ex = x1 - 2 * sc;
				g.fillRect(x + 1, ys, sc + 1, yb - ys + 1);
				g.fillRect(x + 2 * sc + 2, mid - gap / 2 - sc / 2, sc + 1, sc + 1);
				g.fillRect(x + 2 * sc + 2, mid + gap / 2 - sc / 2, sc + 1, sc + 1);
				g.fillRect(ex - sc - 1, ys, sc + 1, yb - ys + 1);
				g.fillRect(ex - 3 * sc - 2, mid - gap / 2 - sc / 2, sc + 1, sc + 1);
				g.fillRect(ex - 3 * sc - 2, mid + gap / 2 - sc / 2, sc + 1, sc + 1);
				num(g, 5, b, mid);
				g.setFont(f);
				g.drawString("x3", ex, lab, Graphics.TOP | Graphics.RIGHT);
				break;
			}
			case 24: {
				staff(g, x0, x1, ys, 3);
				int ay = lab + 1;
				g.drawLine(a, ay, x1, ay);
				g.drawLine(a, ay, a, ay + fh - 2);
				g.setFont(f);
				g.drawString("1.", a + 2, ay, Graphics.TOP | Graphics.LEFT);
				g.drawLine(a, ys, a, ys + 2 * gap);
				num(g, 3, c, mid);
				break;
			}
			case 25:
				staff(g, x0, x1, ys, 3);
				g.setColor(MARK);
				g.setFont(fb);
				g.drawString("Intro", x0 + 2, lab, Graphics.TOP | Graphics.LEFT);
				num(g, 0, c, mid);
				break;
			case 26: {
				staff(g, x0, x1, ys, 3);
				g.setFont(fb);
				g.drawString("4/4", x0 + 2, lab, Graphics.TOP | Graphics.LEFT);
				int tx = x0 + 6 + fb.stringWidth("4/4"), r = fh / 4 + 1;
				g.fillArc(tx, lab + fh - r - 1, r + 1, r, 0, 360);
				g.drawLine(tx + r, lab + 1, tx + r, lab + fh - r / 2 - 1);
				g.setFont(f);
				g.drawString("=120", tx + r + 2, lab, Graphics.TOP | Graphics.LEFT);
				break;
			}
			case 27:
				staff(g, x0, x1, ys, 3);
				g.drawLine(x0 + 2, ys, x0 + 2, ys + 2 * gap);
				g.setColor(LINE);
				g.setFont(f);
				g.drawString("12", x0 + 2, lab, Graphics.TOP | Graphics.LEFT);
				g.setColor(FG);
				num(g, 3, c, mid);
				break;
			case 28:
				staff(g, x0, x1, ys, 3);
				g.setColor(TXT);
				g.setFont(fb);
				g.drawString("Am", a - 2 * sc, lab, Graphics.TOP | Graphics.LEFT);
				num(g, 2, a, mid);
				num(g, 1, a, ys);
				break;
			default: {
				staff(g, x0, x1, ys, 3);
				num(g, 5, a, mid);
				num(g, 7, c, mid);
				int cw = 6 * sc + 4, cy = ys - gap / 2, ch = 3 * gap;
				g.setColor(0x0070E0);
				g.drawRect(a - cw / 2, cy, cw, ch);
				g.drawRect(a - cw / 2 + 1, cy + 1, cw - 2, ch - 2);
				g.setColor(0xE00000);
				g.drawRect(c - cw / 2, cy, cw, ch);
				g.drawRect(c - cw / 2 + 1, cy + 1, cw - 2, ch - 2);
				break;
			}
		}
	}

	public synchronized boolean scrollBy(int d) {
		int max = total - (getHeight() - bar), old = sy;
		sy += d;
		if (sy > max) sy = max;
		if (sy < 0) sy = 0;
		repaint();
		return sy != old;
	}

	private void scroll(int d) {
		scrollBy(d);
	}

	protected void showNotify() {
		fling.stop();
	}

	protected void keyPressed(int k) {
		key(k);
	}

	protected void keyRepeated(int k) {
		key(k);
	}

	private void key(int k) {
		fling.stop();
		int a;
		try {
			a = getGameAction(k);
		} catch (IllegalArgumentException e) {
			a = 0;
		}
		int h = getHeight() - bar;
		if (k == -6 || k == -7 || k == -21 || k == -22 || k == KEY_STAR || k == KEY_POUND) app.nav(App.MENU);
		else if (a == UP || k == KEY_NUM2) scroll(-3 * fh);
		else if (a == DOWN || k == KEY_NUM8) scroll(3 * fh);
		else if (a == LEFT || k == KEY_NUM4 || k == KEY_NUM7) scroll(-h + fh);
		else if (a == RIGHT || k == KEY_NUM6 || k == KEY_NUM9) scroll(h - fh);
		else if (k == KEY_NUM0) scroll(-sy);
	}

	protected void pointerPressed(int x, int y) {
		fling.press(y);
		if (bar > 0 && y >= getHeight() - bar) {
			pressed = 0;
			repaint();
		}
	}

	protected void pointerDragged(int x, int y) {
		if (pressed < 0) fling.drag(y);
	}

	protected void pointerReleased(int x, int y) {
		if (pressed < 0) {
			fling.release(true);
			return;
		}
		pressed = -1;
		repaint();
		if (y >= getHeight() - bar) app.nav(App.MENU);
	}
}
