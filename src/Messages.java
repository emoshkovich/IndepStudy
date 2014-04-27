/* NOTES:
 * BitTorrent client will normally use ports 6881 to 6889.
 */

import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;

class Udp {
	private Bencoder benc = new Bencoder();
	private byte[] send_packet;
	private InetAddress ip;
	private int port;
	private Map decoded_reply;

	public Udp(byte[] send_packet, InetAddress ip, int port) {
		this.send_packet = send_packet;
		this.ip = ip;
		this.port = port;
	}

	// public synchronized void run() {
	public void run() {
		if (port < 0 || port > 65535) {
			System.out.println("ERROR: wrong port number");
			return;
		}
		// System.out.println(Thread.currentThread().getName());
		// Send the message and receive the response
		DatagramSocket socket = null;
		try {
			socket = new DatagramSocket();
		} catch (SocketException e) {
			e.printStackTrace();
		}
		byte[] response_to_ping_b = new byte[Messages.PACKET_SIZE];
		DatagramPacket dp_send = new DatagramPacket(send_packet,
				send_packet.length, ip, port);
		System.out.println("send message to ip:   " + ip + "     to port: "
				+ port);
		try {
			socket.send(dp_send);
			socket.setSoTimeout(500);
		} catch (Exception e) {
			System.out.println("socket timed out");
		}

		// Receiving the message
		DatagramPacket dp_receive = new DatagramPacket(response_to_ping_b,
				response_to_ping_b.length);
		try {
			socket.receive(dp_receive);
		} catch (SocketTimeoutException e) {
			socket.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		// If the socket was not closed before, close it
		finally {
			if (socket != null && !socket.isClosed()) {
				socket.close();
			}
			// notify();
		}
		if (dp_receive.getData()[0] != 0) {
			decoded_reply = benc.unbencodeDictionary(dp_receive.getData());
		}
	}

	public Map getDecodedReply() {
		return decoded_reply;
	}

}

/**
 * This class
 */
public class Messages {
	public final static int PACKET_SIZE = 4096;
	private int blockSize = 16384;
	private int reqPieceResponseMessageSize;

	// private String bootstrap_addr_str = "67.215.242.138";//
	// "router.bittorrent.com";
	// private int bootstrap_port = 6881;
	// private String id = "abcdefghij0123456789";

	// private InetAddress bootstrap_addr;
	// private DatagramSocket socket;

	// UDP requests parameters data
	private byte[] send_packet;
	private InetAddress ip;
	private int port;
	// private Map decoded_reply;

	private static ArrayList findNodeCompactInfoList = new ArrayList();
	private static ArrayList getPeersCompactInfoList = new ArrayList();
	private int peerCounter = 0;
	private int metadataSize = -1;

	private Bencoder benc = new Bencoder();

	public LinkedHashMap sendPing(InetAddress ip, int port, String id)
			throws Exception {
		Map<byte[], byte[]> args = new LinkedHashMap<byte[], byte[]>();
		args.put(benc.bencodeString("id"), benc.bencodeString(id));

		Map<byte[], byte[]> send_hm = new LinkedHashMap<byte[], byte[]>();
		send_hm.put(benc.bencodeString("t"), benc.bencodeString("aa"));
		send_hm.put(benc.bencodeString("y"), benc.bencodeString("q"));
		send_hm.put(benc.bencodeString("q"), benc.bencodeString("ping"));
		send_hm.put(benc.bencodeString("a"), benc.bencodeDictionary(args));
		byte[] send_packet = benc.bencodeDictionary(send_hm);
		LinkedHashMap decoded_reply = udpRequestResponse(send_packet, ip, port);
		/*
		 * if (decoded_reply != null && !decoded_reply.isEmpty()) { byte[]
		 * ip_and_port_bytes = (byte[]) decoded_reply.get("ip");
		 * 
		 * // getIp(ip_and_port_bytes); // getPort(ip_and_port_bytes); }
		 */
		return decoded_reply;
	}

	private InetAddress getIp(byte[] compactInfo) throws UnknownHostException {
		byte[] ip_bytes = Arrays.copyOfRange(compactInfo, 0, 4);
		return InetAddress.getByAddress(ip_bytes);
	}

	private int getPort(byte[] compactInfo) throws UnknownHostException {
		// byte[] port_bytes = Arrays.copyOfRange(compactInfo,
		// compactInfo.length-2, compactInfo.length);
		byte[] port_bytes = Arrays.copyOfRange(compactInfo, 4, 6);
		short[] shorts = new short[1];
		ByteBuffer.wrap(port_bytes).order(ByteOrder.BIG_ENDIAN).asShortBuffer()
				.get(shorts);
		short signed_port = shorts[0];
		Integer port = signed_port >= 0 ? signed_port : 0x10000 + signed_port;

		return port;
	}

	private LinkedHashMap udpRequestResponse(byte[] send_packet,
			InetAddress ip, int port) throws IOException {
		this.send_packet = send_packet;
		this.ip = ip;
		this.port = port;

		// Thread th = new MessageThread(send_packet, ip, port);
		Udp th = new Udp(send_packet, ip, port);
		// th.start();
		th.run();
		/*
		 * synchronized (th) { try { th.wait(); } catch (InterruptedException e)
		 * { e.printStackTrace(); } }
		 */

		return (LinkedHashMap) ((Udp) th).getDecodedReply();
	}

	public void handShake(InetAddress ip, int port, String info_hash, String id) {
		System.out.println("handshake: ip: " + ip + ", port: " + port);
		Socket socket = null;
		try {
			socket = new Socket(ip, port);
			socket.setSoTimeout(60000);
		} catch (IOException e) {
			System.out.println("Socket connection not established");
			return;
		}

		// Send the message
		DataOutputStream dos = null;
		try {
			dos = new DataOutputStream(socket.getOutputStream());
			dos.writeByte(19);
			dos.writeBytes("BitTorrent protocol");
			byte[] reserved = new byte[8];
			reserved[5] = 0x10;
			dos.write(reserved);
			dos.writeBytes(info_hash);
			dos.writeBytes(id);
			dos.flush();
		} catch (IOException e) {
			e.printStackTrace();
		}
		String hs_response = null;
		try {
			DataInputStream in = new DataInputStream(socket.getInputStream());
			//Thread.sleep(1000);
			System.out.println("Waiting for received string: ");
			byte[] ca = new byte[20000];
			in.read(ca, 0, ca.length);
			hs_response = new String(ca);
		} catch (IOException e) {
			System.out.println("Read Timeout");
		}
		System.out.print("Received string: ");
		// hs_response = in.readLine();
		System.out.println(hs_response);
		// in.close();
		if (hs_response != null) {
			metadataSize = findMetadataSize(hs_response);
			System.out.println("Metadata Size: " + metadataSize);

			int ut = findUtMetadataCode(hs_response);
			System.out.println("ut code: " + ut);
			if (metadataSize > 0 && ut > 0) {
				getMetadata(socket, ut);
			} // else if (metadataSize < 0) { // try sending the message again
				// handShake(ip, port, info_hash, id);
				// }
		}
		// }
	}

	private int findMetadataSize(String hs_response) {
		// gets substring starting at metadata_size, and then from that
		// substring
		// returns the first substring between i and e
		String mtSize = "metadata_size";
		int ix = hs_response.indexOf(mtSize);
		if (ix > 0) {
			String sMtSize = hs_response.substring(ix + mtSize.length());
			int i = sMtSize.indexOf('i');
			int e = sMtSize.indexOf('e');
			metadataSize = Integer.parseInt(sMtSize.substring(i + 1, e));
		} else {
			metadataSize = -1;
		}
		return metadataSize;
	}

	private int findUtMetadataCode(String hs_response) {
		int utMetadataCode = -1;
		String utM = "ut_metadata";
		int ix = hs_response.indexOf(utM);
		if (ix > 0) {
			String utMSubstr = hs_response.substring(ix + utM.length());
			int i = utMSubstr.indexOf('i');
			int e = utMSubstr.indexOf('e');
			utMetadataCode = Integer.parseInt(utMSubstr.substring(i + 1, e));
		}
		return utMetadataCode;
	}

	private void sendExtensionHeader(DataOutputStream dos) {
		// extension header
		Map<byte[], byte[]> send_eh = new LinkedHashMap<byte[], byte[]>();
		Map<byte[], byte[]> m = new LinkedHashMap<byte[], byte[]>();
		m.put(benc.bencodeString("ut_metadata"), benc.bencodeInteger(2));
		send_eh.put(benc.bencodeString("m"), benc.bencodeDictionary(m));
		byte[] send_packet_eh = benc.bencodeDictionary(send_eh);
		System.out.println("length of extension header: "
				+ send_packet_eh.length);
		// send the messages
		try {
			dos.writeBytes(new String(send_packet_eh));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	// The prefix message, attached to the beginning to any bittorrent message
	private void sendPrMessage(DataOutputStream dos, int msglen, int ut) {
		try {
			dos.writeInt(msglen + 2);
			dos.writeByte(20);
			dos.writeByte(ut);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	// possibly do it in the other class
	public String getMetadata(Socket socket, int ut) {
		String metadata = "";
		int numPieces = (int) Math.ceil((double) metadataSize / blockSize);
		int prefixLen = 0;
		System.out.println("numPieces: " + numPieces);
		char[] metaInfo = new char[metadataSize];

		for (int i = 0; i < numPieces; i++) {
			requestPiece(socket, ut, i);
			String piece = receivePiece(socket);
			if (piece == null){
				break;
			}
			if (i == 0){
				prefixLen = reqPieceResponseMessageSize - blockSize;
			}
			System.out.println("Piece number index: " + i + " prefixLen: " + prefixLen);
			metadata += piece.substring(prefixLen);
		}
		System.out.println("METADATA: " + metadata);
		return metadata;
	}

	private void requestPiece(Socket socket, int ut, int pieceIx) {
		// extension message
		Map<byte[], byte[]> send_em = new LinkedHashMap<byte[], byte[]>();
		send_em.put(benc.bencodeString("msg_type"), benc.bencodeInteger(0));
		send_em.put(benc.bencodeString("piece"), benc.bencodeInteger(pieceIx));
		byte[] send_packet_em = benc.bencodeDictionary(send_em);
		System.out.println("requestPieces packets: " + " "
				+ new String(send_packet_em));

		// Send the message
		DataOutputStream dos = null;
		try {
			dos = new DataOutputStream(socket.getOutputStream());
			sendPrMessage(dos, 24, 0);
			sendExtensionHeader(dos);
			sendPrMessage(dos, send_packet_em.length, ut);
			dos.writeBytes(new String(send_packet_em));
			dos.flush();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private String receivePiece(Socket socket) {
		DataInputStream in;
		String piece = null;
		try {
			in = new DataInputStream(socket.getInputStream());

			System.out.println("requestPieces. Waiting for received string: ");

			System.out.print("requestPieces. Received string: ");
			// String rp_response = in.readLine();

			reqPieceResponseMessageSize = getInputLength(in);
			byte[] ch = new byte[reqPieceResponseMessageSize];
			
			//byte[] ch = new byte[16435];
			in.readFully(ch, 0, ch.length);
			
			//byte[] ch = new byte[50000];
			//int numRead = in.read(ch, 0, ch.length);
			//System.out.println("Number of characters read: " + numRead);
			piece = new String(ch);
			System.out.println(piece);

			// in.close();
		} catch (IOException e) {
			System.out.println("receivePieces. Read Timeout");
		}
		return piece;
	}

	// The length of the tcp string to be read in bytes
	private int getInputLength(DataInputStream in) {
		int numRead = 0;
		try {
			numRead = in.readInt();
			System.out.println("numRead: " + numRead);
		} catch (IOException e) {
			e.printStackTrace();
		}

		return numRead;
	}

	public void findNode(InetAddress ip, int port, String id) throws Exception {
		// InetAddress bootstrap_addr =
		// InetAddress.getByName(bootstrap_addr_str);
		Map<byte[], byte[]> args = new LinkedHashMap<byte[], byte[]>();
		args.put(benc.bencodeString("id"), benc.bencodeString(id));
		args.put(benc.bencodeString("target"), benc.bencodeString(id));

		Map<byte[], byte[]> send_hm = new LinkedHashMap<byte[], byte[]>();
		send_hm.put(benc.bencodeString("t"), benc.bencodeString("aaaa"));
		send_hm.put(benc.bencodeString("y"), benc.bencodeString("q"));
		send_hm.put(benc.bencodeString("q"), benc.bencodeString("find_node"));
		send_hm.put(benc.bencodeString("a"), benc.bencodeDictionary(args));
		byte[] send_packet = benc.bencodeDictionary(send_hm);
		String s = new String(send_packet);
		// System.out.println("find_node packet: " + s);
		byte[] nodes = null;

		Map decoded_reply = udpRequestResponse(send_packet, ip, port);

		System.out.println("DECODED find_node: " + decoded_reply);

		if (decoded_reply != null && !decoded_reply.isEmpty()) {
			Map r = (LinkedHashMap) decoded_reply.get("r");
			// The nodes array is 416 characters long
			nodes = (byte[]) r.get("nodes");
			addCompactInfo(nodes, findNodeCompactInfoList);
			for (int i = 0; i < findNodeCompactInfoList.size(); i++) {
				byte[] node_info = (byte[]) findNodeCompactInfoList.get(i);
				ip = getIp(node_info);
				port = getPort(node_info);
				// Send find node with new info
				findNode(ip, port, id);
			}
		}
	}

	// Note that each node has 26 bytes: 20 for id, 4 for ip, and 2 for port
	private void addCompactInfo(byte[] nodes, ArrayList al) {
		if (nodes == null) {
			return;
		}
		if ((nodes.length % 26) != 0) {
			System.out.println("nodes compact info has wrong length: "
					+ nodes.length);
		}
		for (int i = 20; i < nodes.length;) {
			int j = i + 26;
			al.add(Arrays.copyOfRange(nodes, i, j));
			i = j;
		}
	}

	public byte[] getPeers(String info_hash, InetAddress ip, int port, String id)
			throws Exception {
		System.out.println("peerCounter: " + peerCounter);
		Map<byte[], byte[]> args = new LinkedHashMap<byte[], byte[]>();
		args.put(benc.bencodeString("id"), benc.bencodeString(id));
		args.put(benc.bencodeString("info_hash"), benc.bencodeString(info_hash));

		Map<byte[], byte[]> send_hm = new LinkedHashMap<byte[], byte[]>();
		send_hm.put(benc.bencodeString("t"), benc.bencodeString("aa"));
		send_hm.put(benc.bencodeString("y"), benc.bencodeString("q"));
		send_hm.put(benc.bencodeString("q"), benc.bencodeString("get_peers"));
		send_hm.put(benc.bencodeString("a"), benc.bencodeDictionary(args));
		byte[] send_packet = benc.bencodeDictionary(send_hm);
		byte[] nodes = null;
		Vector values = null;

		// InetAddress address = InetAddress.getByName(bootstrap_addr_str);
		// int port = bootstrap_port;

		Map decoded_reply = udpRequestResponse(send_packet, ip, port);

		System.out.println("DECODED getPeers: " + decoded_reply);

		if (decoded_reply != null && !decoded_reply.isEmpty()) {
			Map r = (LinkedHashMap) decoded_reply.get("r");
			if (r == null) {
				return null;
			}
			values = (Vector) r.get("values");

			if (values != null) {
				contactPeers(values, info_hash, id);
			} else {
				// Possibly put this in else statement
				nodes = (byte[]) r.get("nodes");
				addCompactInfo(nodes, getPeersCompactInfoList);
				while (peerCounter < getPeersCompactInfoList.size()) {
					byte[] node_info = (byte[]) getPeersCompactInfoList
							.get(peerCounter);
					InetAddress node_ip = getIp(node_info);
					int node_port = getPort(node_info);
					peerCounter++;
					// Send get peers request with new info
					getPeers(info_hash, node_ip, node_port, id);
				}
				if (peerCounter == getPeersCompactInfoList.size()) {
					System.out.println("Resumed getPeers");
					getPeers(info_hash, ip, port, id);
				}
			}
		}
		return nodes;
	}

	private void contactPeers(Vector values, String info_hash, String id)
			throws Exception {
		for (int i = 0; i < values.size(); i++) {
			byte[] peer = (byte[]) values.elementAt(i);
			InetAddress peer_ip = getIp(peer);
			int peer_port = getPort(peer);
			LinkedHashMap ping_reply = sendPing(peer_ip, peer_port, id);
			if (ping_reply != null && !ping_reply.isEmpty()) {
				handShake(peer_ip, peer_port, info_hash, id);
			}
		}
	}

	private void populateMessageMap(String messageType) {

	}
}