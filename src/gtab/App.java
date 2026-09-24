package gtab;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Enumeration;
import java.util.Vector;

import javax.microedition.io.Connector;
import javax.microedition.io.file.FileConnection;
import javax.microedition.io.file.FileSystemRegistry;
import javax.microedition.lcdui.ChoiceGroup;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Form;
import javax.microedition.lcdui.Gauge;
import javax.microedition.lcdui.Item;
import javax.microedition.lcdui.ItemCommandListener;
import javax.microedition.lcdui.StringItem;
import javax.microedition.lcdui.TextBox;
import javax.microedition.lcdui.TextField;
import javax.microedition.lcdui.List;
import javax.microedition.media.Manager;
import javax.microedition.media.Player;
import javax.microedition.media.PlayerListener;
import javax.microedition.midlet.MIDlet;
import javax.microedition.rms.RecordStore;

public final class App extends MIDlet implements CommandListener, ItemCommandListener, Runnable, PlayerListener {
	static final int FILES = 1, TRACKS = 2, PREV = 3, NEXT = 4, MENU = 5, VIEW = 6, EXIT = 7, UP = 8, PLAY = 9, HELP = 10, TUNER = 11, CHORDS = 12, SEARCH = 13, RESULTS = 14, SETTINGS = 15, PICKDIR = 16, SHOWFILES = 17, EXPORT = 18;
	private static final int A_SOLO = 20, A_ROT = 21, A_CP = 23;
	private static final String DEFAULT_SERVER = "94.159.98.141:7000";

	private Display d;
	private View view;
	private List files, tracks, menu;
	private Help help;
	private final Form settings = new Form(L.s("Настройки", "Settings"));
	private final TextField srvField = new TextField(L.s("Сервер (хост:порт)", "Server (host:port)"), "", 64, TextField.ANY);
	private final Command saveCmd = new Command(L.s("Сохранить", "Save"), Command.OK, 1);
	private final Command dirCmd = new Command(L.s("Выбрать...", "Choose..."), Command.ITEM, 1);
	private final Command exitCmd = new Command(L.s("Выход", "Exit"), Command.EXIT, 1);
	private final StringItem dirBtn = new StringItem(null, L.s("Выбрать...", "Choose..."), Item.BUTTON);
	private final Command pickCmd = new Command(L.s("Сохранять сюда", "Save here"), Command.OK, 1);
	private final ChoiceGroup lang = new ChoiceGroup(L.s("Язык", "Language"), ChoiceGroup.POPUP,
			new String[] { L.s("Авто", "Auto"), "Русский", "English" }, null);
	private final StringItem dlItem = new StringItem(L.s("Папка загрузок: ", "Download folder: "), "");
	private boolean picking, menuSong;
	private final java.util.Hashtable dirSel = new java.util.Hashtable();
	private int lastAct = SEARCH, filesSel, resSel;
	private char pending;
	private List expList;
	private boolean[] expOn;
	private final Command expCmd = new Command(L.s("Сохранить", "Save"), Command.OK, 1);
	private volatile int op;
	private int waitBack;
	private final int[] acts = new int[16];
	private boolean listed;
	private final Command findCmd = new Command(L.s("Искать", "Search"), Command.OK, 1);
	private List results;
	private final Vector resLabels = new Vector();
	private final TextBox query = new TextBox(L.s("Поиск: исполнитель, песня", "Search: artist, song"), "", 64, TextField.ANY);
	private final Form wait = new Form("GTab");
	private final StringItem waitText = new StringItem(null, "");
	private final Gauge gauge = new Gauge(null, false, 100, 0);
	private int lastPct = -1;
	private final java.util.Vector resIds = new java.util.Vector();
	private Net net;
	private String q, dl, upPath;
	private int resTotal, fetchId;
	private Tuner tuner;
	private Chords chords;
	private final Command back = new Command(L.s("Назад", "Back"), Command.BACK, 1);
	private final Vector names = new Vector();

	private String dir, path, job, dataPath;
	private byte[] data;
	private int want = -1;
	private volatile Player player;
	private volatile boolean playBusy;
	private boolean solo, nokia;
	private volatile boolean alive;

	protected void startApp() {
		if (d != null) return;
		d = Display.getDisplay(this);
		view = new View(this);
		tuner = new Tuner(this);
		chords = new Chords(this);
		settings.append(srvField);
		settings.append(dlItem);
		dirBtn.setDefaultCommand(dirCmd);
		dirBtn.setItemCommandListener(this);
		settings.append(dirBtn);
		settings.append(lang);
		settings.addCommand(saveCmd);
		settings.addCommand(back);
		settings.setCommandListener(this);
		query.addCommand(findCmd);
		query.addCommand(back);
		query.setCommandListener(this);
		wait.append(waitText);
		wait.addCommand(back);
		wait.setCommandListener(this);
		String sv = L.pref(1);
		if (sv == null) sv = getAppProperty("GTab-Server");
		net = new Net(sv == null || sv.trim().length() == 0 ? DEFAULT_SERVER : sv.trim());
		net.ui = this;
		dl = L.pref(2);
		try {
			Class.forName("com.nokia.mid.ui.DeviceControl");
			nokia = true;
		} catch (Throwable e) {
		}
		alive = true;
		new Thread(new Runnable() {
			public void run() {
				while (alive) {
					Displayable c = d.getCurrent();
					if (c != null && c.isShown()) {
						try {
							if (nokia) Light.on();
							else d.flashBacklight(6000);
						} catch (Throwable e) {
							nokia = false;
						}
					}
					try {
						Thread.sleep(5000);
					} catch (InterruptedException e) {
					}
				}
			}
		}).start();
		nav(MENU);
	}

	private static int langMode() {
		String p = L.pref(3);
		return p == null || p.length() == 0 ? L.AUTO : p.charAt(0) - '0';
	}

	private List list(String title, Command a, Command b) {
		List l = new List(title, List.IMPLICIT);
		if (a != null) l.addCommand(a);
		if (b != null) l.addCommand(b);
		l.setCommandListener(this);
		return l;
	}

	private void buildMenu() {
		boolean song = view.song() != null;
		if (menu == null || song != menuSong) {
			menu = list("GTab", song ? back : exitCmd, null);
			int i = 0;
			if (song) {
				item(i++, VIEW);
				item(i++, PLAY);
				item(i++, TRACKS);
				item(i++, A_SOLO);
			}
			item(i++, SEARCH);
			item(i++, FILES);
			item(i++, CHORDS);
			item(i++, TUNER);
			if (song) {
				item(i++, A_ROT);
				item(i++, A_CP);
				item(i++, EXPORT);
			}
			item(i++, SETTINGS);
			item(i++, HELP);
			item(i++, EXIT);
			if (song) lastAct = VIEW;
			menuSong = song;
		} else {
			for (int i = 0; i < menu.size(); i++) {
				String l = label(acts[i]);
				if (!l.equals(menu.getString(i))) menu.set(i, l, null);
			}
		}
		menu.setTitle(song ? Cp.s(s0(view.song())) : "GTab");
	}

	private void item(int i, int act) {
		acts[i] = act;
		menu.append(label(act), null);
	}

	private String label(int act) {
		switch (act) {
			case VIEW: return L.s("Вернуться к табу", "Back to tab");
			case PLAY: return player != null ? L.s("Стоп", "Stop") : L.s("Играть", "Play");
			case TRACKS: return L.s("Треки", "Tracks");
			case A_SOLO: return solo ? L.s("Соло: вкл", "Solo: on") : L.s("Соло: выкл", "Solo: off");
			case SEARCH: return L.s("Поиск табов", "Search tabs");
			case FILES: return L.s("Файлы", "Files");
			case CHORDS: return L.s("Аккорды", "Chords");
			case TUNER: return L.s("Тюнер", "Tuner");
			case A_ROT: return L.s("Поворот экрана", "Rotate screen");
			case A_CP: return L.s("Кодировка 1251/1252", "Encoding 1251/1252");
			case EXPORT: return L.s("Экспорт MIDI", "Export MIDI");
			case SETTINGS: return L.s("Настройки", "Settings");
			case HELP: return L.s("Справка", "Help");
			default: return L.s("Выход", "Exit");
		}
	}

	private int menuIndex(int act) {
		for (int i = 0; i < menu.size(); i++) if (acts[i] == act) return i;
		return 0;
	}

	private void restore(Displayable x) {
		int i = -1;
		List l = null;
		if (x == menu) {
			l = menu;
			i = menuIndex(lastAct);
		} else if (x == files) {
			l = files;
			i = filesSel;
		} else if (x == results) {
			l = results;
			i = resSel;
		} else if (x == tracks) {
			Song s = view.song();
			l = tracks;
			i = s == null ? 0 : s.trk.index;
		}
		if (l != null && l.size() > 0) l.setSelectedIndex(i >= 0 && i < l.size() ? i : 0, true);
	}

	private static byte[] s0(Song s) {
		return s.title != null && s.title.length > 0 ? s.title : s.artist;
	}

	protected void pauseApp() {
		stop();
	}

	protected void destroyApp(boolean u) {
		alive = false;
		stop();
	}

	void nav(int a) {
		Song s = view.song();
		switch (a) {
			case FILES:
				if (listed) show(files);
				else list(dir);
				break;
			case PICKDIR:
				picking = true;
				list(dir);
				break;
			case SETTINGS:
				srvField.setString(net.host());
				dlItem.setText(dl == null ? L.s("не выбрана", "not set") : dl.substring(8));
				lang.setSelectedIndex(langMode(), true);
				show(settings);
				break;
			case TRACKS:
				if (s == null) return;
				List tl = list(Cp.s(s.title), back, null);
				for (int i = 0; i < s.tCount; i++)
					tl.append((i + 1) + ". " + Cp.s(s.tName[i]) + (s.tDrum[i] ? " (drums)" : ""), null);
				tracks = tl;
				show(tracks);
				break;
			case PREV:
			case NEXT:
				if (s == null || s.tCount < 2) return;
				load(path, (s.trk.index + (a == NEXT ? 1 : s.tCount - 1)) % s.tCount);
				break;
			case MENU:
				buildMenu();
				show(menu);
				break;
			case PLAY:
				if (s == null) return;
				if (player != null) stop();
				else if (!playBusy) {
					playBusy = true;
					start("P");
				}
				break;
			case VIEW:
				if (s == null) nav(MENU);
				else show(view);
				break;
			case CHORDS:
				stop();
				if (s != null && !s.tDrum[s.trk.index]) chords.open(s.tTune[s.trk.index], view.cursorChord());
				else chords.open(null, null);
				show(chords);
				break;
			case TUNER:
				stop();
				if (s != null && !s.tDrum[s.trk.index]) tuner.open(s.tTune[s.trk.index], Cp.s(s.tName[s.trk.index]), s.tCapo[s.trk.index]);
				else tuner.open(null, null, 0);
				show(tuner);
				break;
			case SEARCH:
				query.setString(q == null ? "" : q);
				show(query);
				break;
			case RESULTS:
				if (results == null) nav(SEARCH);
				else show(results);
				break;
			case EXPORT:
				if (s == null) return;
				List el = new List(L.s("Экспорт MIDI", "Export MIDI"), List.MULTIPLE);
				for (int i = 0; i < s.tCount; i++) el.append((i + 1) + ". " + Cp.s(s.tName[i]), null);
				for (int i = 0; i < s.tCount; i++) el.setSelectedIndex(i, true);
				el.addCommand(expCmd);
				el.addCommand(back);
				el.setCommandListener(this);
				expList = el;
				show(expList);
				break;
			case SHOWFILES:
				show(files);
				break;
			case HELP:
				if (help == null) help = new Help(this, getAppProperty("MIDlet-Version"));
				help.top();
				show(help);
				break;
			case UP:
				if (dir == null) return;
				int p = dir.lastIndexOf('/', dir.length() - 2);
				String up = p <= 7 ? null : dir.substring(0, p + 1);
				dirSel.put(up == null ? "/" : up, p <= 7 ? dir.substring(8) : dir.substring(p + 1));
				list(up);
				break;
			case EXIT:
				net.close();
				alive = false;
				stop();
				notifyDestroyed();
				break;
		}
	}

	void show(Displayable x) {
		restore(x);
		d.setCurrent(x);
		restore(x);
	}

	public void commandAction(Command c, Item it) {
		if (c == dirCmd) {
			pending = 0;
			nav(PICKDIR);
		}
	}

	public void commandAction(Command c, Displayable x) {
		if (c == back) goBack(x);
		else if (c == expCmd) {
			boolean[] on = new boolean[expList.size()];
			expList.getSelectedFlags(on);
			int n = 0;
			for (int i = 0; i < on.length; i++) if (on[i]) n++;
			if (n == 0) expList.setTitle(L.s("Отметьте дорожки", "Select tracks"));
			else {
				expOn = on;
				busy(L.s("Экспорт MIDI...", "Exporting MIDI..."), VIEW);
				start("X");
			}
		}
		else if (c == exitCmd) nav(EXIT);
		else if (c == pickCmd) {
			if (dir == null) files.setTitle(L.s("Зайдите в папку", "Open a folder first"));
			else {
				busy(L.s("Проверка папки...", "Checking folder..."), SHOWFILES);
				start("W");
			}
		} else if (c == saveCmd) {
			String v = srvField.getString().trim();
			if (v.length() > 0) {
				if (v.indexOf(':') < 0) v = v + ":7000";
				L.save(1, v);
				net.close();
				net = new Net(v);
				net.ui = this;
			}
			int lm = lang.getSelectedIndex();
			if (lm >= 0 && lm != langMode()) {
				L.save(3, String.valueOf(lm));
				busy("Перезапустите GTab, чтобы сменить язык.\n\nRestart GTab to apply the language.", MENU);
			} else nav(MENU);
		}
		else if (c == findCmd && x == query) {
			q = query.getString().trim();
			if (q.length() > 0) {
				resIds.removeAllElements();
				resLabels.removeAllElements();
				busy(L.s("Поиск...", "Searching..."), SEARCH);
				start("Q");
			}
		} else if (c == findCmd) nav(SEARCH);
		else if (x == results) {
			int i = results.getSelectedIndex();
			if (i < 0) return;
			if (i >= resIds.size()) {
				busy(L.s("Поиск...", "Searching..."), RESULTS);
				start("Q");
			} else {
				resSel = i;
				fetchId = ((Integer) resIds.elementAt(i)).intValue();
				busy(L.s("Скачивание...", "Downloading..."), RESULTS);
				start("D");
			}
		}
		else if (x == files) pick(files.getSelectedIndex());
		else if (x == tracks) load(path, tracks.getSelectedIndex());
		else if (x == menu) {
			int i = menu.getSelectedIndex();
			if (i < 0) return;
			int act = acts[i];
			lastAct = act;
			if (act == A_SOLO) {
				solo = !solo;
				nav(VIEW);
			} else if (act == A_ROT) {
				view.rotate();
				nav(VIEW);
			} else if (act == A_CP) {
				Cp.cyr = !Cp.cyr;
				view.relayout();
				nav(VIEW);
			} else if (act == PLAY) {
				nav(VIEW);
				nav(PLAY);
			} else nav(act);
		}
	}

	private void pick(int i) {
		if (i < 0) return;
		String name = (String) names.elementAt(i);
		filesSel = i;
		if (!name.equals("..")) dirSel.put(dir == null ? "/" : dir, name);
		if (name.equals("..")) nav(UP);
		else if (name.endsWith("/")) list((dir == null ? "file:///" : dir) + name);
		else if (picking) return;
		else if (name.toLowerCase().endsWith(".gpx") || name.toLowerCase().endsWith(".gp")) {
			upPath = dir + name;
			busy(L.s("Конвертация на сервере...", "Converting on server..."), SHOWFILES);
			start("U");
		} else load(dir + name, -1);
	}

	private void busy(String m, int backTo) {
		op++;
		waitBack = backTo;
		waitText.setText(m);
		hideGauge();
		show(wait);
	}

	private void hideGauge() {
		lastPct = -1;
		try {
			if (wait.size() > 1) wait.delete(1);
		} catch (Throwable e) {
		}
	}

	private void waitError(String m) {
		hideGauge();
		waitText.setText(m);
	}

	void progress(int done, int total, boolean up) {
		try {
			int pct = total > 0 ? done * 100 / total : 0;
			if (pct == lastPct) return;
			lastPct = pct;
			if (up && done >= total) {
				hideGauge();
				waitText.setText(L.s("Конвертация на сервере...", "Converting on server..."));
				return;
			}
			if (wait.size() == 1) wait.append(gauge);
			gauge.setValue(pct);
			waitText.setText((up ? L.s("Отправка: ", "Uploading: ") : L.s("Загрузка: ", "Downloading: ")) + (done + 1023) / 1024 + " / " + (total + 1023) / 1024 + L.s(" КБ", " KB"));
		} catch (Throwable e) {
		}
	}

	private void goBack(Displayable x) {
		if (x == wait) {
			op++;
			net.abort();
			nav(waitBack);
		} else if (x == query || x == settings || x == expList) nav(MENU);
		else if (x == results) {
			resSel = Math.max(results.getSelectedIndex(), 0);
			nav(SEARCH);
		}
		else if (x == files) {
			if (picking) {
				char p = pending;
				endPick();
				if (p == 'D') nav(RESULTS);
				else if (p == 'U') list(dir);
				else if (p == 'X') nav(MENU);
				else nav(SETTINGS);
			} else if (dir != null) nav(UP);
			else nav(MENU);
		} else nav(VIEW);
	}

	private void list(String to) {
		dir = to;
		start("L");
	}

	private void load(String url, int track) {
		stop();
		path = url;
		want = track;
		view.busy(L.s("Загрузка...", "Loading..."));
		show(view);
		start("O");
	}

	private synchronized void start(String j) {
		job = j;
		new Thread(this).start();
	}

	public void run() {
		String j;
		synchronized (this) {
			j = job;
		}
		if (j.equals("L")) runList();
		else if (j.equals("P")) runPlay();
		else if (j.equals("Q")) runSearch();
		else if (j.equals("D")) runFetch();
		else if (j.equals("U")) runUpload();
		else if (j.equals("W")) runPick();
		else if (j.equals("X")) runExport();
		else runOpen();
	}

	private void runList() {
		Vector v = new Vector();
		String err = null;
		try {
			if (dir == null) {
				for (Enumeration e = FileSystemRegistry.listRoots(); e.hasMoreElements();) v.addElement(e.nextElement());
			} else {
				v.addElement("..");
				FileConnection fc = (FileConnection) Connector.open(dir, Connector.READ);
				try {
					for (Enumeration e = fc.list(); e.hasMoreElements();) {
						String nm = (String) e.nextElement();
						String lc = nm.toLowerCase();
						if (nm.endsWith("/") || lc.endsWith(".gp3") || lc.endsWith(".gp4") || lc.endsWith(".gp5") || lc.endsWith(".gpx") || lc.endsWith(".gp")) v.addElement(nm);
					}
				} finally {
					fc.close();
				}
			}
		} catch (Throwable e) {
			err = e.toString();
		}
		sort(v, dir == null ? 0 : 1);
		names.removeAllElements();
		List fl = list((picking ? L.s("Куда сохранять: ", "Save to: ") : "") + (dir == null ? "/" : dir.substring(8)), back, picking ? pickCmd : null);
		for (int i = 0; i < v.size(); i++) {
			String nm = (String) v.elementAt(i);
			names.addElement(nm);
			fl.append(nm, null);
		}
		Object want = dirSel.get(dir == null ? "/" : dir);
		filesSel = want == null ? 0 : Math.max(names.indexOf(want), 0);
		if (err != null) {
			names.addElement("..");
			fl.append(err, null);
		}
		files = fl;
		listed = true;
		show(files);
	}

	private static void sort(Vector v, int from) {
		for (int i = from + 1; i < v.size(); i++) {
			String a = (String) v.elementAt(i);
			int j = i - 1;
			while (j >= from && less(a, (String) v.elementAt(j))) {
				v.setElementAt(v.elementAt(j), j + 1);
				j--;
			}
			v.setElementAt(a, j + 1);
		}
	}

	private static boolean less(String a, String b) {
		boolean da = a.endsWith("/"), db = b.endsWith("/");
		if (da != db) return da;
		return a.toLowerCase().compareTo(b.toLowerCase()) < 0;
	}

	private void stop() {
		player = null;
		view.playing(false);
	}

	public void playerUpdate(Player p, String event, Object data) {
		if (p == player && (event.equals(END_OF_MEDIA) || event.equals(ERROR) || event.equals(CLOSED))) stop();
	}

	private void runPlay() {
		Song s = view.song();
		if (s == null || s.order.length == 0) {
			playBusy = false;
			return;
		}
		int m0 = view.cursorMeasure(), from = 0;
		for (int i = 0; i < s.order.length; i++) {
			if (s.order[i] == m0) {
				from = i;
				break;
			}
		}
		Player p = null;
		try {
			for (int i = 0; i < 50 && !view.isShown(); i++) Thread.sleep(40);
			view.status("MIDI...");
			boolean[] on = null;
			if (solo) {
				on = new boolean[s.tCount];
				on[s.trk.index] = true;
			}
			byte[] mid = Midi.build(s, from, on);
			long[] at = Midi.times(s, from);
			p = Manager.createPlayer(new ByteArrayInputStream(mid), "audio/midi");
			mid = null;
			p.addPlayerListener(this);
			p.realize();
			p.prefetch();
			player = p;
			view.status(null);
			view.playing(true);
			p.start();
			long t0 = System.currentTimeMillis();
			int i = 0, n = at.length - 1;
			while (player == p) {
				long us = p.getMediaTime();
				if (us < 0) us = (System.currentTimeMillis() - t0) * 1000;
				while (i + 1 < n && at[i + 1] <= us) i++;
				while (i > 0 && at[i] > us) i--;
				if (i < n) {
					int m = s.order[from + i];
					long tick = (us - at[i]) * s.tempo(m) * Midi.PPQ / 60000000L;
					view.follow(m, (int) tick);
				}
				Thread.sleep(40);
			}
		} catch (OutOfMemoryError e) {
			player = null;
			System.gc();
			view.status(solo ? L.s("Мало памяти", "Out of memory") : L.s("Мало памяти, включи соло", "Out of memory, try Solo"));
		} catch (Throwable e) {
			player = null;
			String m = e.getMessage();
			view.status(m != null && m.toLowerCase().indexOf("not allowed") >= 0 ? L.s("Звук запрещён профилем", "Sound disabled by profile") : "MIDI: " + e.toString());
		} finally {
			if (p != null) {
				try {
					p.close();
				} catch (Throwable e) {
				}
			}
			if (player == null) view.playing(false);
			playBusy = false;
		}
	}

	private void runSearch() {
		int my = op;
		try {
			int off = resIds.size();
			net.search(q, off);
			if (my != op) return;
			resTotal = net.total;
			for (int i = 0; i < net.count; i++) {
				resIds.addElement(new Integer(net.ids[i]));
				resLabels.addElement(net.artist[i] + " - " + net.title[i] + (net.fmt[i].equals("gpx") ? " [gpx]" : ""));
			}
			List rl = list(resTotal == 0 ? L.s("Ничего не найдено", "Nothing found") : q + ": " + resTotal, findCmd, back);
			for (int i = 0; i < resLabels.size(); i++) rl.append((String) resLabels.elementAt(i), null);
			if (resIds.size() < resTotal) rl.append(L.s("Ещё... (", "More... (") + (resTotal - resIds.size()) + ")", null);
			results = rl;
			resSel = off;
			nav(RESULTS);
		} catch (Throwable e) {
			if (my == op) waitError(L.s("Ошибка сети: ", "Network error: ") + e.getMessage());
		}
	}

	private void runFetch() {
		int my = op;
		if (dl == null) {
			askDir('D');
			return;
		}
		try {
			net.fetch(fetchId);
		} catch (Throwable e) {
			if (my == op) waitError(L.s("Ошибка: ", "Error: ") + e.getMessage());
			return;
		}
		if (my != op) {
			net.data = null;
			return;
		}
		try {
			String url = save(dl, net.name, net.data);
			net.data = null;
			load(url, -1);
		} catch (Throwable e) {
			net.data = null;
			lostDir('D');
		}
	}

	private void runUpload() {
		int my = op;
		byte[] res;
		String name;
		try {
			byte[] src = read(upPath);
			net.upload(upPath.substring(upPath.lastIndexOf('/') + 1), src);
			res = net.data;
			name = net.name;
			net.data = null;
		} catch (Throwable e) {
			net.data = null;
			if (my == op) waitError(L.s("Ошибка: ", "Error: ") + e.getMessage());
			return;
		}
		if (my != op) return;
		String url;
		try {
			url = save(upPath.substring(0, upPath.lastIndexOf('/') + 1), name, res);
		} catch (Throwable e) {
			if (dl == null) {
				askDir('U');
				return;
			}
			try {
				url = save(dl, name, res);
			} catch (Throwable e2) {
				lostDir('U');
				return;
			}
		}
		load(url, -1);
	}

	private void runPick() {
		int my = op;
		try {
			probe(dir);
		} catch (Throwable e) {
			if (my != op) return;
			files.setTitle(L.s("Сюда нельзя писать, выберите другую", "Not writable, choose another"));
			show(files);
			return;
		}
		if (my != op) return;
		dl = dir;
		L.save(2, dl);
		char p = pending;
		endPick();
		if (p == 'D') {
			busy(L.s("Скачивание...", "Downloading..."), RESULTS);
			runFetch();
		} else if (p == 'U') {
			busy(L.s("Конвертация на сервере...", "Converting on server..."), SHOWFILES);
			runUpload();
		} else if (p == 'X') {
			busy(L.s("Экспорт MIDI...", "Exporting MIDI..."), VIEW);
			runExport();
		} else nav(SETTINGS);
	}

	private void runExport() {
		int my = op;
		Song s = view.song();
		if (s == null || expOn == null) return;
		byte[] mid;
		try {
			mid = Midi.build(s, 0, expOn);
		} catch (OutOfMemoryError e) {
			if (my == op) waitError(L.s("Мало памяти", "Out of memory"));
			return;
		}
		if (my != op) return;
		String base = path.substring(path.lastIndexOf('/') + 1);
		if (base.lastIndexOf('.') > 0) base = base.substring(0, base.lastIndexOf('.'));
		StringBuffer nm = new StringBuffer(base);
		boolean all = true;
		for (int i = 0; i < expOn.length; i++) all &= expOn[i];
		if (!all) {
			char sep = '-';
			for (int i = 0; i < expOn.length; i++) {
				if (!expOn[i]) continue;
				nm.append(sep).append(i + 1);
				sep = '_';
			}
		}
		String name = nm.append(".mid").toString(), url;
		try {
			url = save(path.substring(0, path.lastIndexOf('/') + 1), name, mid);
		} catch (Throwable e) {
			if (dl == null) {
				askDir('X');
				return;
			}
			try {
				url = save(dl, name, mid);
			} catch (Throwable e2) {
				lostDir('X');
				return;
			}
		}
		if (my == op) waitError(L.s("Сохранено:\n", "Saved:\n") + url.substring(8));
	}

	private void askDir(char p) {
		pending = p;
		nav(PICKDIR);
	}

	private void lostDir(char p) {
		dl = null;
		L.save(2, "");
		askDir(p);
	}

	private void endPick() {
		picking = false;
		pending = 0;
		if (files != null) files.removeCommand(pickCmd);
	}

	private static void probe(String folder) throws java.io.IOException {
		FileConnection fc = (FileConnection) Connector.open(folder + "gtab.tmp", Connector.READ_WRITE);
		try {
			if (fc.exists()) fc.delete();
			fc.create();
			OutputStream os = fc.openOutputStream();
			os.write(0);
			os.close();
			fc.delete();
		} finally {
			fc.close();
		}
	}

	private static String save(String folder, String name, byte[] b) throws java.io.IOException {
		String u = folder + name;
		FileConnection fc = (FileConnection) Connector.open(u, Connector.READ_WRITE);
		try {
			if (fc.exists()) fc.truncate(0);
			else fc.create();
			OutputStream os = fc.openOutputStream();
			try {
				os.write(b);
			} finally {
				os.close();
			}
		} finally {
			fc.close();
		}
		return u;
	}

	private static byte[] read(String u) throws java.io.IOException {
		FileConnection fc = (FileConnection) Connector.open(u, Connector.READ);
		try {
			long size = fc.fileSize();
			if (size <= 0 || size > 4194304) throw new java.io.IOException(L.s("размер файла", "file size"));
			byte[] b = new byte[(int) size];
			DataInputStream ds = fc.openDataInputStream();
			try {
				ds.readFully(b);
			} finally {
				ds.close();
			}
			return b;
		} finally {
			fc.close();
		}
	}

	private void runOpen() {
		Song prev = view.song();
		int keep = prev != null && want >= 0 ? view.cursorMeasure() : -1, tick = keep < 0 ? 0 : view.cursorPos();
		boolean fresh = prev == null || want < 0;
		prev = null;
		view.set(null);
		view.busy(L.s("Загрузка...", "Loading..."));
		System.gc();
		FileConnection fc = null;
		InputStream is = null;
		try {
			if (!path.equals(dataPath)) {
				data = null;
				dataPath = null;
				System.gc();
				fc = (FileConnection) Connector.open(path, Connector.READ);
				long size = fc.fileSize();
				if (size > 0 && size <= 262144) {
					DataInputStream ds = fc.openDataInputStream();
					byte[] b = new byte[(int) size];
					try {
						ds.readFully(b);
					} finally {
						ds.close();
					}
					data = b;
					dataPath = path;
				} else is = fc.openInputStream();
			}
			if (is == null) is = new ByteArrayInputStream(data);
			Song s = Gp.load(is, want);
			if (fresh) Cp.cyr = s.detectCyr();
			view.set(s);
			if (keep >= 0) {
				view.follow(keep, tick);
				view.announce();
			}
		} catch (OutOfMemoryError e) {
			view.set(null);
			System.gc();
			view.fail(L.s("Мало памяти", "Out of memory"));
		} catch (Throwable e) {
			view.set(null);
			view.fail(L.s("Ошибка: ", "Error: ") + e.toString());
		} finally {
			try {
				if (is != null) is.close();
			} catch (Throwable e) {
			}
			try {
				if (fc != null) fc.close();
			} catch (Throwable e) {
			}
		}
	}
}
