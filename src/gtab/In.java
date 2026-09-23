package gtab;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;

final class In {
	private final InputStream s;
	private final byte[] b = new byte[2048];
	private int p, n;

	In(InputStream s) {
		this.s = s;
	}

	private void fill() throws IOException {
		n = s.read(b, 0, b.length);
		p = 0;
		if (n <= 0) {
			n = 0;
			throw new EOFException();
		}
	}

	int u8() throws IOException {
		if (p == n) fill();
		return b[p++] & 0xff;
	}

	int s8() throws IOException {
		return (byte) u8();
	}

	int i32() throws IOException {
		return u8() | (u8() << 8) | (u8() << 16) | (u8() << 24);
	}

	void skip(int k) throws IOException {
		while (k > 0) {
			if (p == n) fill();
			int t = n - p;
			if (t > k) t = k;
			p += t;
			k -= t;
		}
	}

	byte[] bytes(int k) throws IOException {
		if (k < 0 || k > 0xffff) throw new IOException("len");
		byte[] r = new byte[k];
		int o = 0;
		while (o < k) {
			if (p == n) fill();
			int t = n - p;
			if (t > k - o) t = k - o;
			System.arraycopy(b, p, r, o, t);
			p += t;
			o += t;
		}
		return r;
	}
}
