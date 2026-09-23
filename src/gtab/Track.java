package gtab;

import java.util.Enumeration;
import java.util.Hashtable;

final class Track {
	static final int D_DOT = 8, D_V2 = 16, D_REST = 32;

	static final int B_FADE = 1, B_TAP = 2, B_SLAP = 4, B_POP = 8, B_UP = 16, B_DOWN = 32,
			B_PUP = 64, B_PDOWN = 128, B_TREM = 256, B_TEMPO = 512;

	static final int N_TIE = 1, N_DEAD = 2, N_GHOST = 4, N_ACC = 8, N_HACC = 16, N_HAM = 32,
			N_SLIDE = 64, N_RING = 128, N_PM = 256, N_STAC = 512, N_VIB = 1024, N_BEND = 2048,
			N_NH = 4096, N_AH = 8192, N_TRILL = 16384, N_TREMP = 32768;

	int index;
	int[] mBeat;

	int beats;
	char[] bPos;
	byte[] bDur, bTup, bCnt;
	char[] bFx;
	int[] bNote;

	int notes;
	byte[] nStr, nFret, nArg, nGrace;
	char[] nFx;

	Hashtable text = new Hashtable(), chord = new Hashtable();

	Track(int index, int mCount) {
		this.index = index;
		mBeat = new int[mCount + 1];
		int b = mCount * 6 + 16;
		bPos = new char[b];
		bDur = new byte[b];
		bTup = new byte[b];
		bCnt = new byte[b];
		bFx = new char[b];
		bNote = new int[b];
		int n = b * 2;
		nStr = new byte[n];
		nFret = new byte[n];
		nArg = new byte[n];
		nGrace = new byte[n];
		nFx = new char[n];
	}

	int addBeat(int pos, int dur, int tup, int fx, int note, int cnt) {
		if (beats == bPos.length) growBeats(beats + (beats >> 1) + 16);
		int i = beats++;
		bPos[i] = (char) pos;
		bDur[i] = (byte) dur;
		bTup[i] = (byte) tup;
		bFx[i] = (char) fx;
		bNote[i] = note;
		bCnt[i] = (byte) cnt;
		return i;
	}

	void addNote(int str, int fret, int fx, int arg, int grace) {
		if (notes == nStr.length) growNotes(notes + (notes >> 1) + 32);
		int i = notes++;
		nStr[i] = (byte) str;
		nFret[i] = (byte) fret;
		nFx[i] = (char) fx;
		nArg[i] = (byte) arg;
		nGrace[i] = (byte) grace;
	}

	private void growBeats(int c) {
		char[] a = new char[c]; System.arraycopy(bPos, 0, a, 0, beats); bPos = a;
		byte[] d = new byte[c]; System.arraycopy(bDur, 0, d, 0, beats); bDur = d;
		d = new byte[c]; System.arraycopy(bTup, 0, d, 0, beats); bTup = d;
		d = new byte[c]; System.arraycopy(bCnt, 0, d, 0, beats); bCnt = d;
		a = new char[c]; System.arraycopy(bFx, 0, a, 0, beats); bFx = a;
		int[] n = new int[c]; System.arraycopy(bNote, 0, n, 0, beats); bNote = n;
	}

	private void growNotes(int c) {
		byte[] d = new byte[c]; System.arraycopy(nStr, 0, d, 0, notes); nStr = d;
		d = new byte[c]; System.arraycopy(nFret, 0, d, 0, notes); nFret = d;
		d = new byte[c]; System.arraycopy(nArg, 0, d, 0, notes); nArg = d;
		d = new byte[c]; System.arraycopy(nGrace, 0, d, 0, notes); nGrace = d;
		char[] a = new char[c]; System.arraycopy(nFx, 0, a, 0, notes); nFx = a;
	}

	void trim() {
		if (bPos.length != beats) growBeats(beats);
		if (nStr.length != notes) growNotes(notes);
	}

	void sort(int from, int to) {
		int k = to - from;
		int[] ix = new int[k];
		for (int i = 0; i < k; i++) ix[i] = from + i;
		boolean moved = false;
		for (int i = 1; i < k; i++) {
			int v = ix[i], j = i - 1;
			while (j >= 0 && bPos[ix[j]] > bPos[v]) {
				ix[j + 1] = ix[j];
				j--;
				moved = true;
			}
			ix[j + 1] = v;
		}
		if (!moved) return;
		char[] p = new char[k], f = new char[k];
		byte[] d = new byte[k], t = new byte[k], c = new byte[k];
		int[] n = new int[k];
		for (int i = 0; i < k; i++) {
			int o = ix[i];
			p[i] = bPos[o]; f[i] = bFx[o]; d[i] = bDur[o]; t[i] = bTup[o]; c[i] = bCnt[o]; n[i] = bNote[o];
		}
		System.arraycopy(p, 0, bPos, from, k);
		System.arraycopy(f, 0, bFx, from, k);
		System.arraycopy(d, 0, bDur, from, k);
		System.arraycopy(t, 0, bTup, from, k);
		System.arraycopy(c, 0, bCnt, from, k);
		System.arraycopy(n, 0, bNote, from, k);
		remap(text, ix, from, to);
		remap(chord, ix, from, to);
	}

	private static void remap(Hashtable h, int[] ix, int from, int to) {
		if (h.isEmpty()) return;
		Hashtable moved = new Hashtable();
		for (int i = 0; i < ix.length; i++) {
			Object v = h.remove(new Integer(ix[i]));
			if (v != null) moved.put(new Integer(from + i), v);
		}
		for (Enumeration e = moved.keys(); e.hasMoreElements();) {
			Object key = e.nextElement();
			h.put(key, moved.get(key));
		}
	}

	static int ticks(int dur, int tup) {
		int t = 3840 >> (dur & 7);
		if ((dur & D_DOT) != 0) t += t >> 1;
		if (tup > 0) t = t * times(tup) / tup;
		return t;
	}

	static int times(int tup) {
		return tup == 3 ? 2 : tup < 8 ? 4 : 8;
	}
}
