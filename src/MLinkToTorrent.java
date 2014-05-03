import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.Socket;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Formatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Vector;

public class MLinkToTorrent {
	private static String id = "abcdefghij0123456789";

	public final int INFO_HASH_CH_NUM = 40;
	private int blockSize = 16384;
	// private int reqPieceResponseMessageSize;

	// Torrent file extension
	private String tf_extension = ".torrent";
	private static String info_hash_hex;
	private static String info_hash;
	private Messages request;
	private ArrayList getPeersCompactInfoList;

	public MLinkToTorrent(ArrayList getPeersCompactInfoList) {
		this.getPeersCompactInfoList = getPeersCompactInfoList;
		request = new Messages();
	}

	private void parseMagnetLink(String ml) throws UnsupportedEncodingException {
		System.out.println(ml);
		// Magnet link identifier
		String ml_identifier = "magnet:";

		// Bittorrent info hash identifier
		String btih = "?xt=urn:btih:";

		String mlink = ml.substring(ml_identifier.length() + btih.length());
		info_hash_hex = mlink.substring(0, INFO_HASH_CH_NUM);
		info_hash = hexToString(info_hash_hex);
	}

	private String hexToString(String hexStr)
			throws UnsupportedEncodingException {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < hexStr.length() - 1; i += 2) {
			String str = hexStr.substring(i, i + 2);
			int dec = Integer.parseInt(str, 16);
			sb.append((char) dec);
		}
		return sb.toString();
	}

	//
	public String getMetadata(Socket socket, InetAddress ip, int port) {

		String metadata = "";
		int metadataSize = request.getMetadataSize();
		int ut = request.getUtMetadataCode();
		// int reqPieceResponseMessageSize =
		// request.getReqPieceResponseMessageSize();
		System.out.println("metadataSize and ut: " + metadataSize + " " + ut);
		if (metadataSize > 0 && ut > 0) {
			int numPieces = (int) Math.ceil((double) metadataSize / blockSize);
			int prefixLen = 0;
			System.out.println("numPieces: " + numPieces);

			for (int i = 0; i < numPieces; i++) {
				request.requestPiece(socket, ut, i);
				String piece = request.receivePiece(socket);
				if (piece == null) {
					break;
				}
				if (i == 0 && numPieces > 1) {
					System.out.println("reqPieceResponseMessageSize: "
							+ request.reqPieceResponseMessageSize);
					prefixLen = request.reqPieceResponseMessageSize - blockSize;
				} else if (i == 0) {
					prefixLen = piece.indexOf("ee") + 2;
				}
				System.out.println("Piece number index: " + i + " prefixLen: "
						+ prefixLen);
				metadata += piece.substring(prefixLen);
			}
		}
		System.out.println("METADATA: " + metadata);
		return metadata;
	}

	private void addCompactInfo(byte[] nodes) {
		if (nodes == null) {
			return;
		}
		if ((nodes.length % 26) != 0) {
			System.out.println("nodes compact info has wrong length: "
					+ nodes.length);
		}
		for (int i = 20; i < nodes.length;) {
			int j = i + 26;
			getPeersCompactInfoList.add(Arrays.copyOfRange(nodes, i, j));
			i = j;
		}
	}

	public byte[] decodePeer(Map decoded_reply) throws Exception {
		byte[] nodes = null;
		Map r = (LinkedHashMap) decoded_reply.get("r");
		if (r == null) {
			return null;
		}
		Vector values = (Vector) r.get("values");
		if (values != null) {
			byte[] peer = contactPeers(values, info_hash, id);
			System.out.println("values not null: " + values.size());
		} else {
			nodes = (byte[]) r.get("nodes");
			addCompactInfo(nodes);
		}
		return nodes;
	}

	/**
	 * Create a torrent file and put it into the home directory
	 */
	private void createTorrentFile(String metadata) {
		String fileName = info_hash_hex.toUpperCase();
		File f = new File(fileName + tf_extension);
		try {
			f.createNewFile();
		} catch (IOException e) {
			e.printStackTrace();
		}
		PrintWriter writer = null;
		try {
			System.out.println(fileName);
			writer = new PrintWriter(fileName + tf_extension);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
		writer.println("d4:info");
		writer.println(metadata);
		writer.close();
	}

	public byte[] contactPeers(Vector<byte[]> values, String info_hash,
			String id) throws Exception {
		for (int i = 0; i < values.size(); i++) {
			byte[] peer = (byte[]) values.elementAt(i);
			InetAddress peer_ip = request.getIp(peer);
			int peer_port = request.getPort(peer);
			LinkedHashMap ping_reply = request.sendPing(peer_ip, peer_port, id);
			if (ping_reply != null && !ping_reply.isEmpty()) {
				return peer;
			}
		}
		return null;
	}

	private static String sha1(String toEncrypt) {
		String sha1 = "";
		try {
			MessageDigest crypt = MessageDigest.getInstance("SHA-1");
			crypt.reset();
			crypt.update(toEncrypt.getBytes());
			sha1 = byteToHex(crypt.digest());
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		}
		return sha1;
	}

	private static String byteToHex(final byte[] hash) {
		Formatter formatter = new Formatter();
		for (byte b : hash) {
			formatter.format("%02x", b);
		}
		String result = formatter.toString();
		formatter.close();
		return result;
	}

	public static void main(String argv[]) throws Exception {
		// Later input ml as command line argument
		//String ml = "magnet:?xt=urn:btih:9d99e5402c95a5967f3cd360a1d7f4e4da1b6a07&dn=Games+Of+Thrones+Season+1&tr=udp%3A%2F%2Ftracker.openbittorrent.com%3A80&tr=udp%3A%2F%2Ftracker.publicbt.com%3A80&tr=udp%3A%2F%2Ftracker.istole.it%3A6969&tr=udp%3A%2F%2Ftracker.ccc.de%3A80&tr=udp%3A%2F%2Fopen.demonii.com%3A1337";
		//String ml = "magnet:?xt=urn:btih:915930f68f841aacd6dac60869bc5ca65c121f54&dn=Electric+Power+Substations+Engineering%2C+Third+Edition&tr=udp%3A%2F%2Ftracker.openbittorrent.com%3A80&tr=udp%3A%2F%2Ftracker.publicbt.com%3A80&tr=udp%3A%2F%2Ftracker.istole.it%3A6969&tr=udp%3A%2F%2Ftracker-ccc.de%3A6969&tr=udp%3A%2F%2Fopen.demonii.com%3A1337";
		 //String ml = "magnet:?xt=urn:btih:f62c9f1a05a9e31d1c7363c52b0bca25104b05af&dn=It+Began+With+Babbage+-+The+Genesis+Of+Computer+Science+%282013%29+B&tr=udp%3A%2F%2Ftracker.openbittorrent.com%3A80&tr=udp%3A%2F%2Ftracker.publicbt.com%3A80&tr=udp%3A%2F%2Ftracker.istole.it%3A6969&tr=udp%3A%2F%2Ftracker-ccc.de%3A6969&tr=udp%3A%2F%2Fopen.demonii.com%3A1337";
		 //String ml = "magnet:?xt=urn:btih:4a990b48a6517e95ca8346481ba8a9ba03fe322e&dn=Computer+Science+-+An+Overview+%2C11th+Edition%7BBBS%7D&tr=udp%3A%2F%2Ftracker.openbittorrent.com%3A80&tr=udp%3A%2F%2Ftracker.publicbt.com%3A80&tr=udp%3A%2F%2Ftracker.istole.it%3A6969&tr=udp%3A%2F%2Ftracker-ccc.de%3A6969&tr=udp%3A%2F%2Fopen.demonii.com%3A1337";
		 //String ml = "magnet:?xt=urn:btih:61a10ba7c0cb33501ffaae9693e2f690429384c7&dn=The+Art+and+Science+of+Java.An+Introduction+To+Computer+Science%5B&tr=udp%3A%2F%2Ftracker.openbittorrent.com%3A80&tr=udp%3A%2F%2Ftracker.publicbt.com%3A80&tr=udp%3A%2F%2Ftracker.istole.it%3A6969&tr=udp%3A%2F%2Ftracker-ccc.de%3A6969&tr=udp%3A%2F%2Fopen.demonii.com%3A1337";
		 String ml = "magnet:?xt=urn:btih:ebee8a025a6d8a2ac802cb21bfdedafd63d48734&dn=MIT+-+Mathematics+For+Computer+Science&tr=udp%3A%2F%2Ftracker.openbittorrent.com%3A80&tr=udp%3A%2F%2Ftracker.publicbt.com%3A80&tr=udp%3A%2F%2Ftracker.istole.it%3A6969&tr=udp%3A%2F%2Ftracker-ccc.de%3A6969&tr=udp%3A%2F%2Fopen.demonii.com%3A1337";
		// String ml =
		// "magnet:?xt=urn:btih:b58a43aa5ebe5475159ffc259b7e2ede815d90ff&dn=Computer+Science+Programming+Basics+in+Ruby&tr=udp%3A%2F%2Ftracker.openbittorrent.com%3A80&tr=udp%3A%2F%2Ftracker.publicbt.com%3A80&tr=udp%3A%2F%2Ftracker.istole.it%3A6969&tr=udp%3A%2F%2Ftracker-ccc.de%3A6969&tr=udp%3A%2F%2Fopen.demonii.com%3A1337";
		// String ml =
		// "magnet:?xt=urn:btih:8a4241c9ee8d14568f0534389cc59c7313912289&dn=Computer+Science+-+An+Overview+%289th+Edition%29&tr=udp%3A%2F%2Ftracker.openbittorrent.com%3A80&tr=udp%3A%2F%2Ftracker.publicbt.com%3A80&tr=udp%3A%2F%2Ftracker.istole.it%3A6969&tr=udp%3A%2F%2Ftracker-ccc.de%3A6969&tr=udp%3A%2F%2Fopen.demonii.com%3A1337";
		// String ml =
		// "magnet:?xt=urn:btih:be3e31357e30c2c6835c70bd5efb5c9189482df7&dn=Tutsplus+-++Intro+To+Computer+Science+Programming+With+Java+201+&tr=udp%3A%2F%2Ftracker.openbittorrent.com%3A80&tr=udp%3A%2F%2Ftracker.publicbt.com%3A80&tr=udp%3A%2F%2Ftracker.istole.it%3A6969&tr=udp%3A%2F%2Ftracker-ccc.de%3A6969&tr=udp%3A%2F%2Fopen.demonii.com%3A1337";

		Messages req = new Messages();
		ArrayList getPeersCompactInfoList = new ArrayList();
		MLinkToTorrent mlt = new MLinkToTorrent(getPeersCompactInfoList);
		String bootstrap_addr_str = "67.215.242.138";// "router.bittorrent.com"
		InetAddress bootstrap_ip = InetAddress.getByName(bootstrap_addr_str);
		int bootstrap_port = 6881;

		int peerCounter = 0;
		InetAddress node_ip = null;
		int node_port = 0;
		byte[] peer = null;
		try {
			mlt.parseMagnetLink(ml);
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}
		String metadata = "";
		mlt.createTorrentFile("wrote to the file");
		// request a peer from "router.bittorrent.com"
		Map decoded_reply = req.getPeers(info_hash, bootstrap_ip,
				bootstrap_port, id);
		if (decoded_reply != null && !decoded_reply.isEmpty()) {
			mlt.decodePeer(decoded_reply);
		}

		String sha1 = "";
		while (!sha1.equals(info_hash_hex)) {
			// while (peer == null) {
			Vector<byte[]> values = null;
			// Should it be an if or a while loop?
			while (peerCounter < getPeersCompactInfoList.size()) {
				byte[] node_info = (byte[]) getPeersCompactInfoList
						.get(peerCounter);
				System.out.println("peerCounter: " + peerCounter
						+ " ArrayList size: " + getPeersCompactInfoList.size());
				node_ip = req.getIp(node_info);
				node_port = req.getPort(node_info);
				peerCounter++;

				decoded_reply = req.getPeers(info_hash, node_ip, node_port, id);
				if (decoded_reply != null && !decoded_reply.isEmpty()) {
					mlt.decodePeer(decoded_reply);
					Map r = (LinkedHashMap) decoded_reply.get("r");
					if (r == null) {
						continue;
					}
					values = (Vector) r.get("values");
					if (values != null) {
						peer = mlt.contactPeers(values, info_hash, id);
						System.out.println("values not null: " + values.size());
						if (peer != null) {
							System.out.println("peer not null!!!");
							InetAddress peer_ip = req.getIp(peer);
							int peer_port = req.getPort(peer);

							Socket socket = null;
							try {
								socket = new Socket(peer_ip, peer_port);
								socket.setSoTimeout(60000);
								if (req.handShake(socket, peer_ip, peer_port,
										info_hash, id) == true) {
									metadata = mlt.getMetadata(socket, peer_ip,
											peer_port);
									if (!metadata.equals(""))
										break;
								}
							} catch (IOException e) {
								System.out
										.println("Socket connection not established");
							}
						}// Somewhere a peer counter is not incremented.
					}
				}
			}
			if (peerCounter == getPeersCompactInfoList.size()) {
				decoded_reply = req.getPeers(info_hash, bootstrap_ip,
						bootstrap_port, id);
			}
			if (metadata.equals("")){
				continue;
			}
			mlt.createTorrentFile(metadata);
			sha1 = sha1(metadata);
			System.out.println("The sha1 of torrent file: " + sha1);
		}
	}
}
