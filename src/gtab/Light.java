package gtab;

import com.nokia.mid.ui.DeviceControl;

final class Light {
	static void on() {
		DeviceControl.setLights(0, 100);
	}
}
