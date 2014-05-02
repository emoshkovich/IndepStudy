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
	// UDP requests parameters data
	private byte[] send_packet;
	private InetAddress ip;
	private int port;

	private static int metadataSize = -1;
	private static int utMetadataCode = -1;
	public static int reqPieceResponseMessageSize = -1;

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

		return decoded_reply;
	}

	public InetAddress getIp(byte[] compactInfo) throws UnknownHostException {
		byte[] ip_bytes = Arrays.copyOfRange(compactInfo, 0, 4);
		return InetAddress.getByAddress(ip_bytes);
	}

	public int getPort(byte[] compactInfo) throws UnknownHostException {
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

	public boolean handShake(Socket socket, InetAddress ip, int port,
			String info_hash, String id) {
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
			System.out.println("Waiting for received string: ");
			byte[] ca = new byte[20000];
			Thread.sleep(1000);
			in.read(ca, 0, ca.length);
			hs_response = new String(ca);
		} catch (IOException e) {
			System.out.println("Read Timeout");
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		System.out.print("Received string: ");
		System.out.println(hs_response);
		if (hs_response != null) {
			setMetadataSize(findMetadataSize(hs_response));
			System.out.println("Metadata Size: " + metadataSize);

			setUtMetadataCode(findUtMetadataCode(hs_response));
			System.out.println("ut code: " + utMetadataCode);
			if (metadataSize > 0 && utMetadataCode > 0) {
				return true;
			}
		}
		return false;
	}

	public void setMetadataSize(int metadataSize){
		this.metadataSize = metadataSize;
	}
	public int getMetadataSize() {
		return metadataSize;
	}

	public void setUtMetadataCode(int utMetadataCode){
		this.utMetadataCode = utMetadataCode;
	}
	public int getUtMetadataCode() {
		return utMetadataCode;
	}
	
	public void setReqPieceResponseMessageSize(int reqPieceResponseMessageSize){
		this.reqPieceResponseMessageSize = reqPieceResponseMessageSize;
	}
	public int getReqPieceResponseMessageSize(){
		return reqPieceResponseMessageSize;
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
		String utM = "ut_metadata";
		int ix = hs_response.indexOf(utM);
		if (ix > 0) {
			String utMSubstr = hs_response.substring(ix + utM.length());
			int i = utMSubstr.indexOf('i');
			int e = utMSubstr.indexOf('e');
			utMetadataCode = Integer.parseInt(utMSubstr.substring(i + 1, e));
		} else {
			utMetadataCode = -1;
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

	public void requestPiece(Socket socket, int ut, int pieceIx) {
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

	public String receivePiece(Socket socket) {
		DataInputStream in;
		String piece = null;
		try {
			in = new DataInputStream(socket.getInputStream());

			System.out.println("requestPieces. Waiting for received string: ");

			System.out.print("requestPieces. Received string: ");

			setReqPieceResponseMessageSize(getInputLength(in));
			byte[] ch = new byte[reqPieceResponseMessageSize];
			System.out.println("reqPieceResponseMessageSize in Messages: "+ reqPieceResponseMessageSize);

			// byte[] ch = new byte[16435];
			in.readFully(ch, 0, ch.length);

			// byte[] ch = new byte[50000];
			// int numRead = in.read(ch, 0, ch.length);
			// System.out.println("Number of characters read: " + numRead);
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

	/*
	 * public void findNode(InetAddress ip, int port, String id, ArrayList
	 * findNodeCompactInfoList) throws Exception { Map<byte[], byte[]> args =
	 * new LinkedHashMap<byte[], byte[]>(); args.put(benc.bencodeString("id"),
	 * benc.bencodeString(id)); args.put(benc.bencodeString("target"),
	 * benc.bencodeString(id));
	 * 
	 * Map<byte[], byte[]> send_hm = new LinkedHashMap<byte[], byte[]>();
	 * send_hm.put(benc.bencodeString("t"), benc.bencodeString("aaaa"));
	 * send_hm.put(benc.bencodeString("y"), benc.bencodeString("q"));
	 * send_hm.put(benc.bencodeString("q"), benc.bencodeString("find_node"));
	 * send_hm.put(benc.bencodeString("a"), benc.bencodeDictionary(args));
	 * byte[] send_packet = benc.bencodeDictionary(send_hm); String s = new
	 * String(send_packet); // System.out.println("find_node packet: " + s);
	 * byte[] nodes = null;
	 * 
	 * Map decoded_reply = udpRequestResponse(send_packet, ip, port);
	 * 
	 * System.out.println("DECODED find_node: " + decoded_reply);
	 * 
	 * if (decoded_reply != null && !decoded_reply.isEmpty()) { Map r =
	 * (LinkedHashMap) decoded_reply.get("r"); // The nodes array is 416
	 * characters long nodes = (byte[]) r.get("nodes"); addCompactInfo(nodes,
	 * findNodeCompactInfoList); for (int i = 0; i <
	 * findNodeCompactInfoList.size(); i++) { byte[] node_info = (byte[])
	 * findNodeCompactInfoList.get(i); ip = getIp(node_info); port =
	 * getPort(node_info); // Send find node with new info findNode(ip, port,
	 * id); } } }
	 */
	// Note that each node has 26 bytes: 20 for id, 4 for ip, and 2 for port

	public Map getPeers(String info_hash, InetAddress ip, int port,
			String id) throws Exception {
		System.out.println("getPeers");
		//System.out.println("peerCounter: " + peerCounter);
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
		Vector<byte[]> values = null;
		InetAddress node_ip = ip;
		int node_port = port;
		Map decoded_reply = udpRequestResponse(send_packet, ip, port);
			System.out.println("DECODED getPeers: " + decoded_reply);
			/*if (decoded_reply != null && !decoded_reply.isEmpty()) {
				Map r = (LinkedHashMap) decoded_reply.get("r");
				if (r == null) {
					System.out.println("r is null");
					peerCounter++;
				}
				values = (Vector<byte[]>) r.get("values");
				// Possibly put this in else statement
				nodes = (byte[]) r.get("nodes");
				addCompactInfo(nodes, getPeersCompactInfoList);
				
				
				 * if (peerCounter == getPeersCompactInfoList.size()) {
				 * System.out.println("Resumed getPeers"); ip =
				 * InetAddress.getByName("67.215.242.138"); port = 6881; return
				 * getPeers(info_hash, ip, port, id, getPeersCompactInfoList,
				 * peerCounter); }
				 
			}
				peerCounter++;
				if (peerCounter < getPeersCompactInfoList.size()) {
					byte[] node_info = (byte[]) getPeersCompactInfoList
							.get(peerCounter);
					node_ip = getIp(node_info);
					node_port = getPort(node_info);
					//peerCounter++;
					// Send get peers request with new info
					// return getPeers(info_hash, node_ip, node_port, id,
					// getPeersCompactInfoList, peerCounter);
				}
				else {
					node_ip = ip;
					node_port = port;
				}
				System.out.println("peer counter: " + peerCounter);*/
		return decoded_reply;
	}

	private void populateMessageMap(String messageType) {

	}
}