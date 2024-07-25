package ua.zefir.zefiroptimizations;

import com.ericsson.otp.erlang.*;
import net.fabricmc.api.ModInitializer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ZefirOptimizations implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("zefiroptimizations");
	private static OtpConnection connection;

	@Override
	public void onInitialize() {
		LOGGER.info("Hello Fabric world!");
//		try {
//			OtpSelf self = new OtpSelf("java_node", "mycookie");
//			OtpPeer peer = new OtpPeer("erlang_node");
//			connection = self.connect(peer);
//		} catch (Exception e) {
//			e.printStackTrace();
//		}
	}

	public static OtpConnection getConnection() {
		return connection;
	}
}