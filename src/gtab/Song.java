package gtab;

final class Song {
	int ver, sub;
	byte[] title, artist, album;
	int tempo;

	int mCount;
	byte[] mNum, mDen, mFlag, mRep, mAlt;
	short[] mTempo;
	byte[][] mMark;

	int tCount;
	byte[][] tName;
	byte[] tStr, tCapo, tProg, tChan;
	byte[][] tTune;
	boolean[] tDrum;
	byte[] tVol, tChan2;

	byte[][] pd;
	int[] pl;
	int[][] po;
	int[] order;

	Track trk;

	static final int M_OPEN = 1, M_DOUBLE = 2;

	void addPlay(int k, int pos, int key, int vel, int dur, int fx, int arg) {
		byte[] b = pd[k];
		int n = pl[k];
		if (n + 8 > b.length) {
			byte[] c = new byte[b.length + (b.length >> 1) + 64];
			System.arraycopy(b, 0, c, 0, n);
			pd[k] = b = c;
		}
		b[n] = (byte) (pos >> 8);
		b[n + 1] = (byte) pos;
		b[n + 2] = (byte) key;
		b[n + 3] = (byte) vel;
		b[n + 4] = (byte) (dur >> 8);
		b[n + 5] = (byte) dur;
		b[n + 6] = (byte) fx;
		b[n + 7] = (byte) arg;
		pl[k] = n + 8;
	}

	void trimPlay() {
		for (int k = 0; k < tCount; k++) {
			if (pd[k].length == pl[k]) continue;
			byte[] c = new byte[pl[k]];
			System.arraycopy(pd[k], 0, c, 0, pl[k]);
			pd[k] = c;
		}
	}

	int tempo(int m) {
		int t = mTempo[m];
		return t < 20 ? 120 : t;
	}

	int mLen(int m) {
		int d = mDen[m];
		if (d <= 0) d = 4;
		return mNum[m] * (3840 / d);
	}

	boolean detectCyr() {
		int c = Cp.score(title) + Cp.score(artist) + Cp.score(album);
		for (int i = 0; i < tCount; i++) c += Cp.score(tName[i]);
		for (int i = 0; i < mCount; i++) c += Cp.score(mMark[i]);
		return c > 0;
	}
}
