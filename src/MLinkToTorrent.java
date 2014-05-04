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

/*
 * Class that converts magnet link to a torrent file. 
 * Retrieves info hash from the magnet link, and uses 
 * it to send messages to nodes and peers to retrieve 
 * the information that will be put into the torrent file
 *
 * To run the program, use terminal to go to the folder where the 
 * compiled program is stored, and type java MLinkToTorrent 'magnet_link' '/path/'
 */
public class MLinkToTorrent {
	private static String id = "abcdefghij0123456789";

	public final int INFO_HASH_CH_NUM = 40;
	private int blockSize = 16384;
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
		// System.out.println(ml);
		// Magnet link identifier
		String ml_identifier = "magnet:";

		// Bittorrent info hash identifier
		String btih = "?xt=urn:btih:";

		// Retrieve the info hash from the magnet link
		String mlink = ml.substring(ml_identifier.length() + btih.length());
		info_hash_hex = mlink.substring(0, INFO_HASH_CH_NUM);
		info_hash = hexToString(info_hash_hex);
	}

	// Converts hexadecimal to String
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

	// Gets the metadata of the file, to be put into the torrent file. Does so
	// by calling requestPiece and receivePiece, for each piece.
	public String getMetadata(Socket socket, InetAddress ip, int port) {

		String metadata = "";
		int metadataSize = request.getMetadataSize();
		int ut = request.getUtMetadataCode();
		// System.out.println("metadataSize and ut: " + metadataSize + " " +
		// ut);
		if (metadataSize > 0 && ut > 0) {
			// Calculate the number of pieces
			int numPieces = (int) Math.ceil((double) metadataSize / blockSize);
			int prefixLen = 0;
			// System.out.println("numPieces: " + numPieces);

			// Get data of each piece
			for (int i = 0; i < numPieces; i++) {
				request.requestPiece(socket, ut, i);
				String piece = request.receivePiece(socket);
				if (piece == null) {
					break;
				}
				if (i == 0 && numPieces > 1) {
					// System.out.println("reqPieceResponseMessageSize: " +
					// request.reqPieceResponseMessageSize);
					prefixLen = request.reqPieceResponseMessageSize - blockSize;
				} else if (i == 0) {
					prefixLen = piece.indexOf("ee") + 2;
				}
				// System.out.println("Piece number index: " + i +
				// " prefixLen: " + prefixLen);
				metadata += piece.substring(prefixLen);
			}
		}
		// System.out.println("METADATA: " + metadata);
		return metadata;
	}

	// Add compact information of the nodes to the array list. This info is
	// taken from the array list to send getPeers messages until a peer that
	// contains the needed torrent file (based on the info hash) is found
	private void addCompactInfo(byte[] nodes) {
		if (nodes == null) {
			return;
		}

		// The nodes message should contain compact info of several nodes. Each
		// nodes compact info should be 26-bytes long. If it is not the case,
		// return
		if ((nodes.length % 26) != 0) {
			// System.out.println("nodes compact info has wrong length: "
			// + nodes.length);
			return;
		}
		for (int i = 20; i < nodes.length;) {
			int j = i + 26;
			getPeersCompactInfoList.add(Arrays.copyOfRange(nodes, i, j));
			i = j;
		}
	}

	// If response to the getPeers message contains "values" key, send ping to
	// ip/port of each peer, to make sure they are online. Otherwise, add nodes
	// compact info to the arraylist
	public byte[] decodePeer(Map decoded_reply) throws Exception {
		byte[] nodes = null;
		Map r = (LinkedHashMap) decoded_reply.get("r");
		if (r == null) {
			return null;
		}
		Vector values = (Vector) r.get("values");
		if (values != null) {
			byte[] peer = contactPeers(values, info_hash, id);
			// System.out.println("values not null: " + values.size());
		} else {
			nodes = (byte[]) r.get("nodes");
			addCompactInfo(nodes);
		}
		return nodes;
	}

	// Creates a torrent file and puts it into the home directory
	public void createTorrentFile(String metadata, String path) {
		String fileName = info_hash_hex.toUpperCase();
		File f = new File(path + fileName + tf_extension);
		try {
			f.createNewFile();
		} catch (IOException e) {
			e.printStackTrace();
		}
		PrintWriter writer = null;
		try {
			// System.out.println(fileName);
			writer = new PrintWriter(path + fileName + tf_extension);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
		writer.print("d4:info");
		writer.print(metadata);
		writer.print("e");
		writer.close();
	}

	// sends ping to ip/port of each peer store in "values", to make sure they
	// are online.
	private byte[] contactPeers(Vector<byte[]> values, String info_hash,
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

	// Calculates sha1 of a string (in this case of the metadata)
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

	// Converts bytes to hexadecimal
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
		if (argv.length < 2) {
			System.err.println("Please enter the magnet link and the path");
			return;
		}
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
			mlt.parseMagnetLink(argv[0]);
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}
		String metadata = "";
		// request a peer from "router.bittorrent.com"
		Map decoded_reply = req.getPeers(info_hash, bootstrap_ip,
				bootstrap_port, id);
		if (decoded_reply != null && !decoded_reply.isEmpty()) {
			mlt.decodePeer(decoded_reply);
		}

		String sha1 = "";
		// Repeat until get the correct torrent file
		while (!sha1.equals(info_hash_hex)) {
			Vector<byte[]> values = null;
			// As long as there are more nodes compact info, send getPeers
			// message
			while (peerCounter < getPeersCompactInfoList.size()) {
				byte[] node_info = (byte[]) getPeersCompactInfoList
						.get(peerCounter);
				// System.out.println("peerCounter: " + peerCounter
				// + " ArrayList size: " + getPeersCompactInfoList.size());
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

					// Send ping messages to the peers
					values = (Vector) r.get("values");
					if (values != null) {
						peer = mlt.contactPeers(values, info_hash, id);
						// System.out.println("values not null: " +
						// values.size());
						if (peer != null) {// Peer responded to the ping
							InetAddress peer_ip = req.getIp(peer);
							int peer_port = req.getPort(peer);

							Socket socket = null;
							try {
								// Send handshake to the peer and if it
								// responds, request metadata
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
								// System.out.println("Socket connection not established");
							}
						}
					}
				}
			}
			// If there are no more nodes, send the message to bootstrap
			// address/port
			if (peerCounter == getPeersCompactInfoList.size()) {
				decoded_reply = req.getPeers(info_hash, bootstrap_ip,
						bootstrap_port, id);
			}
			if (metadata.equals("")) {
				continue;
			}
			// Create torrent file with the metadata
			mlt.createTorrentFile(metadata, argv[1]);
			// Check if sha1 matches the info hash
			sha1 = sha1(metadata);
		}
		//System.out.println("The sha1 of torrent file: " + sha1);
	}
}
