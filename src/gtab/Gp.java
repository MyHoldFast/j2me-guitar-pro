package gtab;

import java.io.IOException;
import java.io.InputStream;

final class Gp {
	private final In in;
	private final Song s = new Song();
	private int ver, sub, want, tempo;
	private Track t;
	private final int[][] last = new int[2][8];
	private final int[] chProg = new int[64], chVol = new int[64];
	private int[][][] lastKey, lastEnt;
	private int bendShape, trillFret, trillPer, tpPer;

	private Gp(InputStream is, int want) {
		in = new In(is);
		this.want = want;
	}

	static Song load(InputStream is, int track) throws IOException {
		Gp g = new Gp(is, track);
		g.run();
		return g.s;
	}

	private void run() throws IOException {
		version();
		s.ver = ver;
		s.sub = sub;
		info();
		if (ver < 5) in.skip(1);
		if (ver >= 4) {
			in.skip(4);
			in.skip(4);
			skipStrInt();
			for (int i = 0; i < 4; i++) {
				in.skip(4);
				skipStrInt();
			}
		}
		if (ver == 5) {
			in.skip(sub > 0 ? 49 : 30);
			for (int i = 0; i < 11; i++) {
				in.skip(4);
				in.skip(in.u8());
			}
		}
		tempo = in.i32();
		s.tempo = tempo;
		if (ver == 5 && sub > 0) in.skip(1);
		in.skip(4);
		if (ver >= 4) in.skip(1);
		for (int i = 0; i < 64; i++) {
			int p = in.i32();
			chProg[i] = p < 0 ? 0 : p;
			int v = in.s8() * 8 - 1;
			chVol[i] = v < 0 ? 0 : v > 127 ? 127 : v;
			in.skip(7);
		}
		if (ver == 5) in.skip(42);
		int mc = in.i32(), tc = in.i32();
		if (mc < 0 || mc > 5000 || tc < 1 || tc > 128) throw new IOException("hdr");
		s.mCount = mc;
		s.tCount = tc;
		headers(mc);
		tracks(tc);
		if (ver == 5) in.skip(sub == 0 ? 2 : 1);
		if (want < 0 || want >= tc) {
			want = 0;
			for (int i = 0; i < tc; i++) {
				if (!s.tDrum[i]) {
					want = i;
					break;
				}
			}
		}
		for (int v = 0; v < 2; v++)
			for (int i = 0; i < 8; i++) last[v][i] = -1;
		t = new Track(want, mc);
		s.trk = t;
		lastKey = new int[tc][2][8];
		lastEnt = new int[tc][2][8];
		for (int k = 0; k < tc; k++)
			for (int v = 0; v < 2; v++)
				for (int i = 0; i < 8; i++) lastKey[k][v][i] = lastEnt[k][v][i] = -1;
		s.pd = new byte[tc][];
		s.pl = new int[tc];
		s.po = new int[tc][mc + 1];
		for (int k = 0; k < tc; k++) s.pd[k] = new byte[512];
		measures(mc, tc);
		t.trim();
		s.trimPlay();
		s.order = Midi.order(s);
	}

	private void version() throws IOException {
		int len = in.u8();
		byte[] b = in.bytes(30);
		if (len > 30) len = 30;
		String v = new String(b, 0, len);
		if (v.equals("FICHIER GUITAR PRO v3.00")) { ver = 3; sub = 0; }
		else if (v.equals("FICHIER GUITAR PRO v4.00")) { ver = 4; sub = 0; }
		else if (v.equals("FICHIER GUITAR PRO v4.06")) { ver = 4; sub = 1; }
		else if (v.equals("FICHIER GUITAR PRO L4.06")) { ver = 4; sub = 2; }
		else if (v.equals("FICHIER GUITAR PRO v5.00")) { ver = 5; sub = 0; }
		else if (v.equals("FICHIER GUITAR PRO v5.10")) { ver = 5; sub = 1; }
		else throw new IOException(v);
	}

	private void info() throws IOException {
		s.title = strIbs();
		skipIbs();
		s.artist = strIbs();
		s.album = strIbs();
		skipIbs();
		if (ver == 5) skipIbs();
		skipIbs();
		skipIbs();
		skipIbs();
		int c = in.i32();
		for (int i = 0; i < c; i++) skipIbs();
	}

	private byte[] strByte(int size) throws IOException {
		int len = in.u8();
		byte[] b = in.bytes(size > 0 ? size : len);
		int use = len <= b.length ? len : size;
		if (use == b.length) return b;
		byte[] r = new byte[use];
		System.arraycopy(b, 0, r, 0, use);
		return r;
	}

	private byte[] strIbs() throws IOException {
		return strByte(in.i32() - 1);
	}

	private void skipIbs() throws IOException {
		int size = in.i32() - 1;
		int len = in.u8();
		in.skip(size > 0 ? size : len);
	}

	private void skipStrInt() throws IOException {
		int n = in.i32();
		if (n < 0) throw new IOException("str");
		in.skip(n);
	}

	private void headers(int mc) throws IOException {
		s.mNum = new byte[mc];
		s.mDen = new byte[mc];
		s.mFlag = new byte[mc];
		s.mRep = new byte[mc];
		s.mAlt = new byte[mc];
		s.mTempo = new short[mc];
		s.mMark = new byte[mc][];
		int num = 4, den = 4, acc = 0;
		for (int i = 0; i < mc; i++) {
			if (ver == 5 && i > 0) in.skip(1);
			int f = in.u8();
			if ((f & 1) != 0) num = in.s8();
			if ((f & 2) != 0) den = in.s8();
			s.mNum[i] = (byte) num;
			s.mDen[i] = (byte) den;
			int fl = 0;
			if ((f & 4) != 0) fl |= Song.M_OPEN;
			if ((f & 0x80) != 0) fl |= Song.M_DOUBLE;
			s.mFlag[i] = (byte) fl;
			int alt = 0;
			if (ver == 5) {
				if ((f & 8) != 0) s.mRep[i] = (byte) (in.u8() - 1);
				if ((f & 0x20) != 0) marker(i);
				if ((f & 0x40) != 0) in.skip(2);
				if ((f & 3) != 0) in.skip(4);
				if ((f & 0x10) != 0) alt = in.u8();
				else in.skip(1);
				in.skip(1);
			} else {
				if ((f & 8) != 0) s.mRep[i] = (byte) in.s8();
				if ((f & 0x10) != 0) {
					int v = in.u8();
					for (int k = 0; k < 8; k++)
						if (v > k && (acc & (1 << k)) == 0) alt |= 1 << k;
				}
				if ((f & 0x20) != 0) marker(i);
				if ((f & 0x40) != 0) in.skip(2);
			}
			s.mAlt[i] = (byte) alt;
			if ((fl & Song.M_OPEN) != 0) acc = 0;
			acc |= alt;
		}
	}

	private void marker(int i) throws IOException {
		s.mMark[i] = strIbs();
		in.skip(4);
	}

	private void tracks(int tc) throws IOException {
		s.tName = new byte[tc][];
		s.tStr = new byte[tc];
		s.tCapo = new byte[tc];
		s.tProg = new byte[tc];
		s.tChan = new byte[tc];
		s.tTune = new byte[tc][];
		s.tDrum = new boolean[tc];
		s.tVol = new byte[tc];
		s.tChan2 = new byte[tc];
		for (int k = 0; k < tc; k++) {
			if (ver == 5 && (k == 0 || sub == 0)) in.skip(1);
			int fl = in.u8();
			s.tName[k] = strByte(40);
			int n = in.i32();
			if (n < 1) n = 1;
			if (n > 7) n = 7;
			s.tStr[k] = (byte) n;
			byte[] tune = new byte[n];
			for (int i = 0; i < 7; i++) {
				int v = in.i32();
				if (i < n) tune[i] = (byte) v;
			}
			s.tTune[k] = tune;
			in.skip(4);
			int ch = in.i32() - 1;
			s.tChan2[k] = (byte) (in.i32() - 1);
			in.skip(4);
			s.tCapo[k] = (byte) in.i32();
			in.skip(4);
			s.tChan[k] = (byte) ch;
			s.tProg[k] = (byte) (ch >= 0 && ch < 64 ? chProg[ch] : 0);
			s.tVol[k] = (byte) (ch >= 0 && ch < 64 ? chVol[ch] : 100);
			s.tDrum[k] = (fl & 1) != 0 || ch == 9;
			if (ver == 5) {
				in.skip(9);
				in.skip(sub > 0 ? 40 : 35);
				if (sub > 0) {
					skipIbs();
					skipIbs();
				}
			}
		}
	}

	private void measures(int mc, int tc) throws IOException {
		for (int m = 0; m < mc; m++) {
			for (int k = 0; k < tc; k++) {
				boolean st = k == want;
				if (st) t.mBeat[m] = t.beats;
				s.po[k][m] = s.pl[k];
				int voices = ver == 5 ? 2 : 1;
				int v2 = -1;
				for (int v = 0; v < voices; v++) {
					if (st && v == 1) v2 = t.beats;
					int n = in.i32();
					if (n < 0 || n > 1024) throw new IOException("beats");
					int pos = 0;
					for (int i = 0; i < n; i++) pos += beat(k, pos, v, st);
				}
				if (st) {
					boolean v1 = false;
					for (int b = t.mBeat[m]; b < t.beats && !v1; b++) v1 = (t.bDur[b] & Track.D_V2) == 0;
					if (!v1) t.addBeat(0, Track.D_REST, 0, 0, t.notes, 0);
					if (t.beats - t.mBeat[m] > 1 && (!v1 || (v2 > t.mBeat[m] && v2 < t.beats))) t.sort(t.mBeat[m], t.beats);
				}
				if (ver == 5 && (m + 1 < mc || k + 1 < tc)) in.skip(1);
			}
			s.mTempo[m] = (short) tempo;
		}
		t.mBeat[mc] = t.beats;
		for (int k = 0; k < tc; k++) s.po[k][mc] = s.pl[k];
	}

	private int beat(int k, int pos, int voice, boolean st) throws IOException {
		int f = in.u8();
		boolean empty = false;
		if ((f & 0x40) != 0) {
			int bt = in.u8();
			if (ver == 5) empty = (bt & 2) == 0;
		}
		int code = in.s8() + 2;
		if (code < 0) code = 0;
		if (code > 6) code = 6;
		int dur = code;
		if ((f & 1) != 0) dur |= Track.D_DOT;
		int tup = 0;
		if ((f & 0x20) != 0) {
			tup = in.i32();
			if (tup != 3 && (tup < 5 || tup > 13 || tup == 8 || (ver < 5 && tup == 13))) tup = 0;
		}
		byte[] ch = null, tx = null;
		if ((f & 2) != 0) ch = chord();
		if ((f & 4) != 0) tx = strIbs();
		int fx = 0, carry = 0;
		if ((f & 8) != 0) {
			int r = ver == 3 ? beatFx3() : beatFx();
			fx = r & 0xffff;
			carry = r >>> 16;
		}
		if ((f & 0x10) != 0 && mix()) fx |= Track.B_TEMPO;
		int sf = in.u8();
		int strings = s.tStr[k];
		int first = st ? t.notes : 0, cnt = 0, p0 = s.pl[k];
		int bt = Track.ticks(dur, tup);
		for (int i = 6; i >= 0; i--) {
			if ((sf & (1 << i)) != 0 && (6 - i) < strings) {
				note(k, 6 - i + 1, voice, carry, st, pos, bt);
				cnt++;
			}
		}
		if (ver == 5) {
			in.skip(1);
			if ((in.s8() & 8) != 0) in.skip(1);
		}
		if (empty) {
			if (st) t.notes = first;
			s.pl[k] = p0;
			return 0;
		}
		if (st) {
			if (cnt == 0) dur |= Track.D_REST;
			if (voice == 1) dur |= Track.D_V2;
			int b = t.addBeat(pos, dur, tup, fx, first, cnt);
			if (ch != null && ch.length > 0) t.chord.put(new Integer(b), ch);
			if (tx != null && tx.length > 0) t.text.put(new Integer(b), tx);
		}
		return bt;
	}

	private byte[] chord() throws IOException {
		byte[] name;
		if (ver == 5) {
			in.skip(17);
			name = strByte(21);
			in.skip(4 + 4 + 28 + 32);
			return name;
		}
		int h = in.u8();
		if ((h & 1) == 0) {
			name = strIbs();
			if (in.i32() != 0) in.skip(24);
			return name;
		}
		if (ver == 3) {
			in.skip(25);
			name = strByte(34);
			in.skip(4 + 24 + 36);
		} else {
			in.skip(16);
			name = strByte(21);
			in.skip(4 + 4 + 28 + 32);
		}
		return name;
	}

	private int beatFx3() throws IOException {
		int f = in.u8(), fx = 0, c = 0;
		if ((f & 3) != 0) c |= Track.N_VIB;
		if ((f & 0x10) != 0) fx |= Track.B_FADE;
		if ((f & 0x20) != 0) {
			int type = in.u8();
			fx |= tapFx(type);
			if (type == 0) fx |= Track.B_TREM;
			in.skip(4);
		}
		if ((f & 0x40) != 0) fx |= stroke(in.s8(), in.s8());
		if ((f & 4) != 0) c |= Track.N_NH;
		if ((f & 8) != 0) c |= Track.N_AH;
		return fx | (c << 16);
	}

	private int beatFx() throws IOException {
		int f1 = in.u8(), f2 = in.u8(), fx = 0, c = 0;
		if ((f1 & 2) != 0) c |= Track.N_VIB;
		if ((f1 & 0x10) != 0) fx |= Track.B_FADE;
		if ((f1 & 0x20) != 0) fx |= tapFx(in.u8());
		if ((f2 & 4) != 0) {
			fx |= Track.B_TREM;
			skipPoints();
		}
		if ((f1 & 0x40) != 0) {
			int a = in.s8(), b = in.s8();
			fx |= ver == 5 ? stroke(b, a) : stroke(a, b);
		}
		if ((f2 & 2) != 0) {
			int d = in.s8();
			if ((d & 1) != 0) fx |= Track.B_PUP;
			else if ((d & 2) != 0) fx |= Track.B_PDOWN;
		}
		return fx | (c << 16);
	}

	private static int tapFx(int type) {
		return type == 1 ? Track.B_TAP : type == 2 ? Track.B_SLAP : type == 3 ? Track.B_POP : 0;
	}

	private static int stroke(int down, int up) {
		return down > 0 ? Track.B_DOWN : up > 0 ? Track.B_UP : 0;
	}

	private void skipPoints() throws IOException {
		in.skip(5);
		int n = in.i32();
		if (n < 0 || n > 64) throw new IOException("pts");
		in.skip(n * 9);
	}

	private boolean mix() throws IOException {
		in.skip(1);
		if (ver == 5) in.skip(16);
		int set = 0;
		for (int i = 0; i < 6; i++) if (in.s8() >= 0) set++;
		if (ver == 5) skipIbs();
		int tv = in.i32();
		in.skip(set);
		if (tv >= 0) {
			tempo = tv;
			in.skip(ver == 5 && sub > 0 ? 2 : 1);
		}
		if (ver == 4) in.skip(1);
		if (ver == 5) {
			in.skip(2);
			if (sub > 0) {
				skipIbs();
				skipIbs();
			}
		}
		return tv >= 0;
	}

	private void note(int k, int str, int voice, int fx, boolean st, int pos, int bt) throws IOException {
		int f = in.u8();
		if (ver >= 4 && (f & 0x40) != 0) fx |= Track.N_ACC;
		if (ver == 5 && (f & 2) != 0) fx |= Track.N_HACC;
		if ((f & 4) != 0) fx |= Track.N_GHOST;
		int type = 0, fret = 0, vel = 95;
		if ((f & 0x20) != 0) type = in.u8();
		if (type == 2) fx |= Track.N_TIE;
		if (type == 3) fx |= Track.N_DEAD;
		if (ver == 5) {
			if ((f & 0x10) != 0) vel = in.s8() * 16 - 1;
			if ((f & 0x20) != 0) fret = in.s8();
			if ((f & 0x80) != 0) in.skip(2);
			if ((f & 1) != 0) in.skip(8);
			in.skip(1);
		} else {
			if ((f & 1) != 0) in.skip(2);
			if ((f & 0x10) != 0) vel = in.s8() * 16 - 1;
			if ((f & 0x20) != 0) fret = in.s8();
			if ((f & 0x80) != 0) in.skip(2);
		}
		int arg = 0, grace = -1;
		bendShape = tpPer = trillPer = 0;
		trillFret = -1;
		if ((f & 8) != 0) {
			int r = ver == 3 ? noteFx3() : noteFx();
			fx |= r & 0xffff;
			arg = (r >> 16) & 0xff;
			grace = (byte) (r >> 24);
		}
		play(k, str, voice, fx, fret, vel, pos, bt, arg, grace);
		if (!st) return;
		if ((fx & Track.N_TIE) != 0) {
			int v = last[voice][str];
			if (v < 0) v = last[voice ^ 1][str];
			fret = v;
		}
		if (fret < 0 || fret > 99) fret = 0;
		last[voice][str] = fret;
		t.addNote(str, fret, fx, arg, grace);
	}

	private void play(int k, int str, int voice, int fx, int fret, int vel, int pos, int dur, int arg, int grace) {
		int[] lk = lastKey[k][voice];
		boolean drum = s.tDrum[k];
		boolean tie = (fx & Track.N_TIE) != 0 && !drum;
		int base = s.tTune[k][str - 1] + (drum ? 0 : s.tCapo[k]), key;
		if (tie) {
			key = lk[str];
			if (key < 0) key = lastKey[k][voice ^ 1][str];
			if (key < 0) return;
		} else {
			if (fret < 0 || fret > 99) fret = 0;
			key = base + fret;
		}
		if (key < 0 || key > 127) return;
		lk[str] = key;
		int pf = 0, pa = 0;
		if ((fx & Track.N_BEND) != 0) {
			pf |= 1 | bendShape;
			pa = arg > 4 ? 4 : arg;
		}
		if ((fx & Track.N_VIB) != 0) pf |= 2;
		if ((fx & Track.N_SLIDE) != 0) pf |= 4;
		if ((fx & Track.N_HAM) != 0) pf |= 32;
		int pe = lastEnt[k][voice][str];
		if (pe >= 0 && pe + 8 <= s.pl[k]) {
			byte[] d = s.pd[k];
			int pfx = d[pe + 6];
			if ((pfx & 32) != 0) vel -= 25;
			if ((pfx & 5) == 4) {
				int dl = key - d[pe + 2];
				if (!tie && dl != 0 && dl >= -2 && dl <= 2) d[pe + 7] = (byte) dl;
				else d[pe + 6] = (byte) (pfx & ~4);
			}
		}
		if ((fx & Track.N_DEAD) != 0) {
			if (dur > 120) dur = 120;
			vel = vel * 2 / 3;
		}
		if ((fx & Track.N_GHOST) != 0) vel -= 30;
		if ((fx & Track.N_ACC) != 0) vel += 15;
		if ((fx & Track.N_HACC) != 0) vel += 25;
		if ((fx & Track.N_PM) != 0) {
			dur >>= 1;
			vel -= 10;
		}
		if ((fx & Track.N_STAC) != 0) dur >>= 1;
		if ((fx & Track.N_RING) != 0) dur = dur * 3 > 7680 ? 7680 : dur * 3;
		if (vel < 1) vel = 1;
		if (vel > 127) vel = 127;
		if (grace != -1 && !tie) {
			int gk = base + (grace == -2 ? 0 : grace), gd = grace == -2 ? 30 : 110, gv = vel > 21 ? vel - 20 : 1;
			if (gk >= 0 && gk <= 127) {
				if (pos >= 120) s.addPlay(k, pos - 120, gk, gv, gd, 0, 0);
				else if (dur > 120) {
					s.addPlay(k, pos, gk, gv, 55, 0, 0);
					pos += 60;
					dur -= 60;
				}
			}
		}
		if (tie) {
			s.addPlay(k, pos, key, vel | 0x80, dur, 0, 0);
			return;
		}
		int step = 0, alt = key;
		if (trillFret >= 0 && trillPer > 0) {
			step = 240 >> (trillPer - 1);
			alt = base + trillFret;
			if (alt < 0 || alt > 127) alt = key;
		} else if (tpPer > 0) step = 960 >> tpPer;
		if (step > 0 && dur >= step * 2) {
			int e = pos + dur, c = 0;
			for (int p = pos; p < e && c < 64; p += step, c++) {
				lastEnt[k][voice][str] = s.pl[k];
				s.addPlay(k, p, (c & 1) == 0 ? key : alt, vel, step, pf & 32, 0);
			}
			return;
		}
		lastEnt[k][voice][str] = s.pl[k];
		s.addPlay(k, pos, key, vel, dur, pf, pa);
	}

	private int noteFx3() throws IOException {
		int f = in.u8(), fx = 0, arg = 0, grace = 0xff;
		if ((f & 1) != 0) {
			fx |= Track.N_BEND;
			arg = bend();
		}
		if ((f & 2) != 0) fx |= Track.N_HAM;
		if ((f & 4) != 0) fx |= Track.N_SLIDE;
		if ((f & 8) != 0) fx |= Track.N_RING;
		if ((f & 0x10) != 0) grace = grace(4);
		return fx | (arg << 16) | (grace << 24);
	}

	private int noteFx() throws IOException {
		int f1 = in.u8(), f2 = in.u8(), fx = 0, arg = 0, grace = 0xff;
		if ((f1 & 1) != 0) {
			fx |= Track.N_BEND;
			arg = bend();
		}
		if ((f1 & 0x10) != 0) grace = grace(ver == 5 ? 5 : 4);
		if ((f2 & 4) != 0) {
			tpPer = in.u8();
			if (tpPer > 3) tpPer = 0;
			fx |= Track.N_TREMP;
		}
		if ((f2 & 8) != 0) {
			in.skip(1);
			fx |= Track.N_SLIDE;
		}
		if ((f2 & 0x10) != 0) {
			int type = in.s8();
			if (ver == 5) {
				if (type == 2) in.skip(3);
				else if (type == 3) in.skip(1);
			}
			if (type == 1) fx |= Track.N_NH;
			else if (type > 1) fx |= Track.N_AH;
		}
		if ((f2 & 0x20) != 0) {
			trillFret = in.s8();
			trillPer = in.s8();
			if (trillPer < 1 || trillPer > 3) trillPer = 0;
			fx |= Track.N_TRILL;
		}
		if ((f1 & 2) != 0) fx |= Track.N_HAM;
		if ((f1 & 8) != 0) fx |= Track.N_RING;
		if ((f2 & 0x40) != 0) fx |= Track.N_VIB;
		if ((f2 & 2) != 0) fx |= Track.N_PM;
		if ((f2 & 1) != 0) fx |= Track.N_STAC;
		return fx | (arg << 16) | (grace << 24);
	}

	private int bend() throws IOException {
		in.skip(5);
		int n = in.i32(), mx = 0, first = 0, lastV = 0;
		if (n < 0 || n > 64) throw new IOException("bend");
		for (int i = 0; i < n; i++) {
			in.skip(4);
			int v = in.i32();
			in.skip(1);
			if (v < 0) v = -v;
			if (i == 0) first = v;
			lastV = v;
			if (v > mx) mx = v;
		}
		bendShape = (first >= 12 ? 8 : 0) | (lastV < mx - 12 ? 16 : 0);
		mx = (mx + 12) / 25;
		return mx > 127 ? 127 : mx;
	}

	private int grace(int len) throws IOException {
		int fret = in.u8();
		in.skip(3);
		boolean dead = len == 5 ? (in.u8() & 1) != 0 : fret == 255;
		if (dead) return 0xfe;
		return fret > 99 ? 99 : fret;
	}
}
