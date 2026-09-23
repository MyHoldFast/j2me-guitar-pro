package gtab;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import javax.microedition.lcdui.Canvas;
import javax.microedition.media.Manager;
import javax.microedition.media.Player;

final class Snd implements Runnable {
	private static Player p;
	private static int gen;
	private static byte[] data;
	private static Canvas owner;
	private static String err;
	private final int g;

	private Snd(int g) {
		this.g = g;
	}

	static synchronized void play(byte[] midi, Canvas c) {
		gen++;
		close();
		data = midi;
		owner = c;
		err = null;
		new Thread(new Snd(gen)).start();
	}

	static synchronized void stop() {
		gen++;
		close();
	}

	static synchronized String error() {
		return err;
	}

	private static void close() {
		if (p == null) return;
		try {
			p.close();
		} catch (Throwable e) {
		}
		p = null;
	}

	public void run() {
		byte[] d;
		Canvas c;
		synchronized (Snd.class) {
			if (g != gen) return;
			d = data;
			c = owner;
		}
		try {
			Player pl = Manager.createPlayer(new ByteArrayInputStream(d), "audio/midi");
			pl.realize();
			pl.prefetch();
			synchronized (Snd.class) {
				if (g != gen) {
					pl.close();
					return;
				}
				p = pl;
			}
			pl.start();
		} catch (Throwable e) {
			synchronized (Snd.class) {
				if (g != gen) return;
				String m = e.getMessage();
				err = m != null && m.toLowerCase().indexOf("not allowed") >= 0 ? L.s("Звук запрещён профилем", "Sound disabled by profile") : e.toString();
			}
			if (c != null) c.repaint();
		}
	}

	static byte[] notes(int[] keys, int cnt, int step, int hold, int prog) {
		ByteArrayOutputStream t = new ByteArrayOutputStream();
		t.write(0);
		t.write(0xc0);
		t.write(prog);
		for (int i = 0; i < cnt; i++) {
			vlq(t, i == 0 ? 0 : step);
			t.write(0x90);
			t.write(keys[i]);
			t.write(100);
		}
		for (int i = 0; i < cnt; i++) {
			vlq(t, i == 0 ? hold : 0);
			t.write(0x80);
			t.write(keys[i]);
			t.write(0x40);
		}
		vlq(t, 240);
		t.write(0xff);
		t.write(0x2f);
		t.write(0);
		byte[] tr = t.toByteArray();
		ByteArrayOutputStream o = new ByteArrayOutputStream();
		byte[] h = { 'M', 'T', 'h', 'd', 0, 0, 0, 6, 0, 0, 0, 1, 0x03, (byte) 0xC0, 'M', 'T', 'r', 'k' };
		o.write(h, 0, h.length);
		o.write(tr.length >>> 24);
		o.write(tr.length >> 16);
		o.write(tr.length >> 8);
		o.write(tr.length);
		o.write(tr, 0, tr.length);
		return o.toByteArray();
	}

	private static void vlq(ByteArrayOutputStream o, int d) {
		if (d >= 1 << 14) o.write(0x80 | ((d >> 14) & 0x7f));
		if (d >= 1 << 7) o.write(0x80 | ((d >> 7) & 0x7f));
		o.write(d & 0x7f);
	}
}
