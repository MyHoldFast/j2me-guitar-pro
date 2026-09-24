package gtab;

final class Fling implements Runnable {
	interface Target {
		boolean scrollBy(int dy);
	}

	private final Target target;
	private final Object lock;
	private volatile int id;
	private int py, moved, vel, speed, runId;
	private long lastT;

	Fling(Target target, Object lock) {
		this.target = target;
		this.lock = lock;
	}

	void press(int y) {
		id++;
		py = y;
		moved = 0;
		vel = 0;
		lastT = System.currentTimeMillis();
	}

	void drag(int y) {
		long now = System.currentTimeMillis();
		int dt = (int) (now - lastT);
		if (dt > 0) {
			vel = (vel + (py - y) * 1000 / dt) / 2;
			lastT = now;
		}
		moved += Math.abs(py - y);
		target.scrollBy(py - y);
		py = y;
	}

	boolean dragged() {
		return moved > 6;
	}

	void release(boolean allow) {
		if (!allow || !dragged() || System.currentTimeMillis() - lastT >= 100 || Math.abs(vel) <= 300) return;
		speed = vel > 5000 ? 5000 : vel < -5000 ? -5000 : vel;
		runId = ++id;
		new Thread(this).start();
	}

	void stop() {
		id++;
	}

	public void run() {
		int my = runId, v = speed;
		while (true) {
			try {
				Thread.sleep(25);
			} catch (InterruptedException e) {
			}
			synchronized (lock) {
				if (my != id) return;
				int d = v / 40;
				if (d == 0 || !target.scrollBy(d)) return;
				v = v * 9 / 10;
			}
		}
	}
}
