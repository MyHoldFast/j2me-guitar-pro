package gtab;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import javax.microedition.io.Connector;
import javax.microedition.io.SocketConnection;

final class Net {
	static final int PAGE = 20;

	private final String url;
	private SocketConnection sc;
	private DataInputStream in;
	private DataOutputStream out;

	int total, count;
	int[] ids = new int[PAGE];
	String[] fmt = new String[PAGE], artist = new String[PAGE], title = new String[PAGE];
	String name;
	byte[] data;
	App ui;
	private volatile int gen;
	private volatile boolean active;

	Net(String hostPort) {
		url = "socket://" + hostPort;
	}

	String host() {
		return url.substring(9);
	}

	synchronized void search(String q, int off) throws IOException {
		call('S', q, off, null);
	}

	synchronized void fetch(int id) throws IOException {
		call('F', null, id, null);
	}

	synchronized void upload(String nm, byte[] b) throws IOException {
		call('C', nm, 0, b);
	}

	synchronized void close() {
		try {
			if (in != null) in.close();
		} catch (IOException e) {
		}
		try {
			if (out != null) out.close();
		} catch (IOException e) {
		}
		try {
			if (sc != null) sc.close();
		} catch (IOException e) {
		}
		sc = null;
		in = null;
		out = null;
	}

	void abort() {
		if (!active) return;
		gen++;
		SocketConnection c = sc;
		DataInputStream i = in;
		DataOutputStream o = out;
		try {
			if (i != null) i.close();
		} catch (Throwable e) {
		}
		try {
			if (o != null) o.close();
		} catch (Throwable e) {
		}
		try {
			if (c != null) c.close();
		} catch (Throwable e) {
		}
	}

	private void call(int cmd, String s, int n, byte[] b) throws IOException {
		int g = gen;
		active = true;
		try {
			exchange(cmd, s, n, b, g);
		} finally {
			active = false;
		}
	}

	private void exchange(int cmd, String s, int n, byte[] b, int g) throws IOException {
		for (int attempt = 0;; attempt++) {
			boolean sent = false;
			try {
				if (sc == null) {
					sc = (SocketConnection) Connector.open(url);
					out = sc.openDataOutputStream();
					in = sc.openDataInputStream();
				}
				out.writeByte(cmd);
				if (cmd == 'S') {
					out.writeUTF(s);
					out.writeInt(n);
				} else if (cmd == 'F') {
					out.writeInt(n);
				} else {
					out.writeUTF(s);
					out.writeInt(b.length);
					for (int o = 0; o < b.length;) {
						int k = Math.min(1024, b.length - o);
						if (g != gen) throw new IOException("cancel");
						out.write(b, o, k);
						out.flush();
						o += k;
						if (ui != null) ui.progress(o, b.length, true);
					}
				}
				out.flush();
				sent = true;
				if (in.readUnsignedByte() != 0) throw new ServerError(in.readUTF());
				if (cmd == 'S') {
					total = in.readInt();
					count = in.readShort();
					for (int i = 0; i < count; i++) {
						ids[i] = in.readInt();
						fmt[i] = in.readUTF();
						artist[i] = in.readUTF();
						title[i] = in.readUTF();
					}
				} else {
					name = in.readUTF();
					data = new byte[in.readInt()];
					for (int o = 0; o < data.length;) {
						if (g != gen) throw new IOException("cancel");
						int k = in.read(data, o, Math.min(1024, data.length - o));
						if (k < 0) throw new IOException("EOF");
						o += k;
						if (ui != null) ui.progress(o, data.length, false);
					}
				}
				return;
			} catch (ServerError e) {
				throw e;
			} catch (IOException e) {
				close();
				if (g != gen || attempt > 0 || (sent && cmd == 'C')) throw e;
			}
		}
	}

	static final class ServerError extends IOException {
		ServerError(String m) {
			super(m);
		}
	}
}
