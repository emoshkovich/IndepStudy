package DHT;

import java.io.File;
import java.io.IOException;

public class MLinkToTorrent {
	public final int INFO_HASH_CH_NUM = 40;

	// Torrent file extension
	private String tf_extension = ".torrent";
	private static String info_hash;
	private UDP_Request request;
	
	public MLinkToTorrent(){
		request = new UDP_Request();
	}
	
	

	private void parseMagnetLink(String ml) {
		System.out.println(ml);
		// Magnet link identifier
		String ml_identifier = "magnet:";

		// Bittorrent info hash identifier
		String btih = "?xt=urn:btih:";

		String mlink = ml.substring(ml_identifier.length() + btih.length());
		System.out.println(mlink);
		info_hash = mlink.substring(0, INFO_HASH_CH_NUM);
		System.out.println(info_hash);
	}

	/**
	 * Create a torrent file and put it into the home directory
	 */
	private void createTorrentFile() {
		String fileName = info_hash.toUpperCase();
		File f = new File(fileName + tf_extension);
		try {
			if (f.createNewFile()) {
				System.out.println("File was created!");
			} else {
				System.out.println("File already exists.");
			}

		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static void main(String argv[]) throws Exception {
		// Later input ml as command line argument
		String ml = "magnet:?xt=urn:btih:9d99e5402c95a5967f3cd360a1d7f4e4da1b6a07&dn=Games+Of+Thrones+Season+1&tr=udp%3A%2F%2Ftracker.openbittorrent.com%3A80&tr=udp%3A%2F%2Ftracker.publicbt.com%3A80&tr=udp%3A%2F%2Ftracker.istole.it%3A6969&tr=udp%3A%2F%2Ftracker.ccc.de%3A80&tr=udp%3A%2F%2Fopen.demonii.com%3A1337";
		UDP_Request req = new UDP_Request();
		MLinkToTorrent mlt = new MLinkToTorrent();
		req.sendPing();
		//req.sendFindNode();
		mlt.parseMagnetLink(ml);
		req.getPeers(info_hash);
		//mlt.createTorrentFile();

	}
}
