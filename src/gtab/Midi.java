package gtab;

import java.io.ByteArrayOutputStream;

final class Midi {
	static final int PPQ = 960;

	static int[] order(Song s) {
		int mc = s.mCount, lim = mc * 16 + 1, n = 0;
		int[] o = new int[mc + 16];
		int i = 0, last = -1, start = 0, num = 0, alt = 0;
		boolean open = true;
		while (i < mc && n < lim) {
			boolean play = true;
			if (i == 0) {
				start = 0;
				open = true;
			}
			if ((s.mFlag[i] & Song.M_OPEN) != 0) {
				start = i;
				open = true;
				if (i > last) {
					num = 0;
					alt = 0;
				}
			} else {
				if (alt == 0) alt = s.mAlt[i] & 0xff;
				if (open && alt > 0 && (num > 7 || (alt & (1 << num)) == 0)) {
					if (s.mRep[i] > 0) alt = 0;
					play = false;
				}
			}
			if (play) {
				if (i > last) last = i;
				if (n == o.length) {
					int[] c = new int[n + (n >> 1) + 16];
					System.arraycopy(o, 0, c, 0, n);
					o = c;
				}
				o[n++] = i;
				if (open && s.mRep[i] > 0) {
					if (num < s.mRep[i] || alt > 0) {
						i = start - 1;
						num++;
					} else {
						num = 0;
						open = false;
					}
					alt = 0;
				}
			}
			i++;
		}
		int[] r = new int[n];
		System.arraycopy(o, 0, r, 0, n);
		return r;
	}

	static long[] times(Song s, int from) {
		int[] o = s.order;
		long[] at = new long[o.length - from + 1];
		for (int i = from; i < o.length; i++) {
			int m = o[i];
			at[i - from + 1] = at[i - from] + (long) s.mLen(m) * 60000000L / ((long) s.tempo(m) * PPQ);
		}
		return at;
	}

	static byte[] build(Song s, int from, int solo) {
		int nt = 1;
		for (int k = 0; k < s.tCount; k++) if (solo < 0 || solo == k) nt++;
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		out.write('M'); out.write('T'); out.write('h'); out.write('d');
		i32(out, 6);
		i16(out, 1);
		i16(out, nt);
		i16(out, PPQ);
		chunk(out, tempoTrack(s, from));
		for (int k = 0; k < s.tCount; k++)
			if (solo < 0 || solo == k) chunk(out, track(s, k, from));
		return out.toByteArray();
	}

	private static byte[] tempoTrack(Song s, int from) {
		W w = new W();
		int[] o = s.order;
		long abs = 0;
		int last = -1;
		for (int i = from; i < o.length; i++) {
			int m = o[i], t = s.tempo(m);
			if (t != last) {
				int us = 60000000 / t;
				w.at(abs);
				w.b(0xff); w.b(0x51); w.b(3);
				w.b(us >> 16); w.b(us >> 8); w.b(us);
				last = t;
			}
			abs += s.mLen(m);
		}
		w.at(abs);
		w.b(0xff); w.b(0x2f); w.b(0);
		return w.toByteArray();
	}

	private static byte[] track(Song s, int k, int from) {
		W w = new W();
		boolean drum = s.tDrum[k];
		int ch = drum ? 9 : s.tChan[k] & 15, ech = s.tChan2[k];
		if (!drum && ch == 9) ch = 15;
		ech = drum || ech < 0 ? ch : ech & 15;
		if (!drum && ech == 9) ech = 14;
		for (int c = 0; c < 2; c++) {
			int cc = c == 0 ? ch : ech;
			if (c == 1 && ech == ch) break;
			if (!drum) {
				w.at(0); w.b(0xc0 | cc); w.b(s.tProg[k] & 0x7f);
			}
			w.at(0); w.b(0xb0 | cc); w.b(7); w.b(s.tVol[k] & 0x7f);
		}
		byte[] d = s.pd[k];
		int[] po = s.po[k], o = s.order;
		Q q = new Q();
		int[] ix = new int[32];
		long abs = 0;
		for (int i = from; i < o.length; i++) {
			int m = o[i], a = po[m], cnt = (po[m + 1] - a) / 8;
			if (ix.length < cnt) ix = new int[cnt];
			for (int j = 0; j < cnt; j++) {
				int e = a + j * 8, p = pos(d, e), r = j - 1;
				while (r >= 0 && pos(d, ix[r]) > p) {
					ix[r + 1] = ix[r];
					r--;
				}
				ix[r + 1] = e;
			}
			for (int j = 0; j < cnt; j++) {
				int e = ix[j], key = d[e + 2], vel = d[e + 3] & 0xff, fx = d[e + 6], arg = d[e + 7];
				int dur = ((d[e + 4] & 0xff) << 8) | (d[e + 5] & 0xff);
				long t = abs + pos(d, e);
				int hit = q.off(key);
				if ((vel & 0x80) != 0 && hit >= 0) {
					if (q.t[hit] < t + dur) q.retime(hit, t + dur);
					continue;
				}
				q.flush(w, t);
				hit = q.off(key);
				if (hit >= 0) {
					q.emit(w, hit, t);
					q.remove(hit);
				}
				boolean efx = !drum && ((fx & 3) != 0 || ((fx & 4) != 0 && arg != 0));
				int c = efx ? ech : ch;
				w.at(t); w.b(0x90 | c); w.b(key); w.b(vel & 0x7f);
				q.add(t + dur, ((0x80 | c) << 16) | (key << 8) | 0x40);
				if (efx) bends(q, c, t, dur, fx, arg);
			}
			abs += s.mLen(m);
		}
		q.flush(w, Long.MAX_VALUE);
		w.at(w.last);
		w.b(0xff); w.b(0x2f); w.b(0);
		return w.toByteArray();
	}

	private static void bends(Q q, int c, long t, int dur, int fx, int arg) {
		long e = t + dur;
		if ((fx & 1) != 0) {
			int full = arg * 2048, third = dur / 3;
			if ((fx & 8) != 0) {
				pb(q, c, t, full);
				if ((fx & 16) != 0) ramp(q, c, t + third, third, full, 0);
			} else {
				ramp(q, c, t, third, 0, full);
				if ((fx & 16) != 0) ramp(q, c, e - third, third, full, 0);
			}
		} else if ((fx & 4) != 0 && arg != 0) {
			pb(q, c, t, 0);
			ramp(q, c, t + dur / 2, dur / 2 - 1, 0, arg * 4096);
		} else if ((fx & 2) != 0) {
			int k = 0;
			for (long x = t; x < e && k < 64; x += 60, k++) pb(q, c, x, (k & 1) == 0 ? 0 : (k & 2) == 0 ? 900 : -900);
		}
		pb(q, c, e, 0);
	}

	private static void ramp(Q q, int c, long t, int len, int a, int b) {
		for (int i = 0; i <= 4; i++) pb(q, c, t + len * i / 4, a + (b - a) * i / 4);
	}

	private static void pb(Q q, int c, long t, int v) {
		v += 8192;
		if (v < 0) v = 0;
		if (v > 16383) v = 16383;
		q.add(t, ((0xe0 | c) << 16) | ((v & 0x7f) << 8) | (v >> 7));
	}

	private static int pos(byte[] d, int e) {
		return ((d[e] & 0xff) << 8) | (d[e + 1] & 0xff);
	}

	private static final class Q {
		long[] t = new long[32];
		int[] ev = new int[32];
		int n;

		void add(long time, int e) {
			if (n == t.length) {
				long[] nt = new long[n * 2];
				int[] ne = new int[n * 2];
				System.arraycopy(t, 0, nt, 0, n);
				System.arraycopy(ev, 0, ne, 0, n);
				t = nt;
				ev = ne;
			}
			int i = n;
			while (i > 0 && t[i - 1] > time) {
				t[i] = t[i - 1];
				ev[i] = ev[i - 1];
				i--;
			}
			t[i] = time;
			ev[i] = e;
			n++;
		}

		int off(int key) {
			for (int i = 0; i < n; i++)
				if (((ev[i] >> 16) & 0xf0) == 0x80 && ((ev[i] >> 8) & 0x7f) == key) return i;
			return -1;
		}

		void remove(int i) {
			System.arraycopy(t, i + 1, t, i, n - i - 1);
			System.arraycopy(ev, i + 1, ev, i, n - i - 1);
			n--;
		}

		void retime(int i, long time) {
			int e = ev[i];
			remove(i);
			add(time, e);
		}

		void emit(W w, int i, long time) {
			int e = ev[i];
			w.at(time);
			w.b(e >> 16);
			w.b(e >> 8);
			w.b(e);
		}

		void flush(W w, long lim) {
			int i = 0;
			while (i < n && t[i] <= lim) {
				emit(w, i, t[i]);
				i++;
			}
			if (i > 0) {
				System.arraycopy(t, i, t, 0, n - i);
				System.arraycopy(ev, i, ev, 0, n - i);
				n -= i;
			}
		}
	}

	private static void chunk(ByteArrayOutputStream out, byte[] b) {
		out.write('M'); out.write('T'); out.write('r'); out.write('k');
		i32(out, b.length);
		out.write(b, 0, b.length);
	}

	private static void i32(ByteArrayOutputStream o, int v) {
		o.write(v >>> 24); o.write(v >> 16); o.write(v >> 8); o.write(v);
	}

	private static void i16(ByteArrayOutputStream o, int v) {
		o.write(v >> 8); o.write(v);
	}

	private static final class W extends ByteArrayOutputStream {
		long last;

		void b(int v) {
			write(v & 0xff);
		}

		void at(long t) {
			int d = (int) (t - last);
			if (d < 0) d = 0;
			last += d;
			if (d >= 1 << 21) b(0x80 | ((d >> 21) & 0x7f));
			if (d >= 1 << 14) b(0x80 | ((d >> 14) & 0x7f));
			if (d >= 1 << 7) b(0x80 | ((d >> 7) & 0x7f));
			b(d & 0x7f);
		}
	}
}
