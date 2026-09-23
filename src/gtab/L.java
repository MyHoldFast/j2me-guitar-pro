package gtab;

import javax.microedition.rms.RecordStore;

final class L {
	static final int AUTO = 0, RU = 1, EN = 2;
	static final int mode;
	static final boolean en;

	static {
		String p = pref(3);
		int m = p == null ? AUTO : p.charAt(0) - '0';
		if (m < AUTO || m > EN) m = AUTO;
		mode = m;
		if (m == AUTO) {
			String loc = System.getProperty("microedition.locale");
			en = loc == null || !loc.toLowerCase().startsWith("ru");
		} else en = m == EN;
	}

	static String s(String ru, String eng) {
		return en ? eng : ru;
	}

	static String pref(int id) {
		try {
			RecordStore rs = RecordStore.openRecordStore("gtab", true);
			try {
				if (id < rs.getNextRecordID()) {
					byte[] b = rs.getRecord(id);
					if (b != null && b.length > 0) return new String(b, "UTF-8");
				}
			} finally {
				rs.closeRecordStore();
			}
		} catch (Throwable e) {
		}
		return null;
	}

	static void save(int id, String v) {
		try {
			byte[] b = v.getBytes("UTF-8");
			RecordStore rs = RecordStore.openRecordStore("gtab", true);
			try {
				while (rs.getNextRecordID() <= id) rs.addRecord(null, 0, 0);
				rs.setRecord(id, b, 0, b.length);
			} finally {
				rs.closeRecordStore();
			}
		} catch (Throwable e) {
		}
	}
}
