import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Vector;

// A udp message
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

	public void run() {
		if (port < 0 || port > 65535) {
			// System.out.println("ERROR: wrong port number");
			return;
		}
		DatagramSocket socket = null;
		try {
			socket = new DatagramSocket();
		} catch (SocketException e) {
			e.printStackTrace();
		}
		byte[] response_to_ping_b = new byte[Messages.PACKET_SIZE];
		DatagramPacket dp_send = new DatagramPacket(send_packet,
				send_packet.length, ip, port);
		// System.out.println("send message to ip:   " + ip + "     to port: "
		// + port);
		try {
			socket.send(dp_send);
			socket.setSoTimeout(500);
		} catch (Exception e) {
			// System.out.println("socket timed out");
			socket.close();
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
 * This class contains the messages sent to the nodes and peers, and receives
 * messages from them
 */
public class Messages {
	public final static int PACKET_SIZE = 65535;
	// UDP requests parameters data
	private byte[] send_packet;
	private InetAddress ip;
	private int port;

	private static int metadataSize = -1;
	private static int utMetadataCode = -1;
	public static int reqPieceResponseMessageSize = -1;

	private Bencoder benc = new Bencoder();

	// Send the ping message to a node or a peer (dht protocol)
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

	// Retrieves ip from compact info of a node (obtained in response to
	// getPeers message)
	public InetAddress getIp(byte[] compactInfo) throws UnknownHostException {
		byte[] ip_bytes = Arrays.copyOfRange(compactInfo, 0, 4);
		return InetAddress.getByAddress(ip_bytes);
	}

	// Retrieves port from compact info of a node (obtained in response to
	// getPeers message)
	public int getPort(byte[] compactInfo) throws UnknownHostException {
		byte[] port_bytes = Arrays.copyOfRange(compactInfo, 4, 6);
		short[] shorts = new short[1];
		ByteBuffer.wrap(port_bytes).order(ByteOrder.BIG_ENDIAN).asShortBuffer()
				.get(shorts);
		short signed_port = shorts[0];
		Integer port = signed_port >= 0 ? signed_port : 0x10000 + signed_port;

		return port;
	}

	// Sends a udp message to a node or peer, and the returns response
	private LinkedHashMap udpRequestResponse(byte[] send_packet,
			InetAddress ip, int port) throws IOException {
		this.send_packet = send_packet;
		this.ip = ip;
		this.port = port;

		Udp th = new Udp(send_packet, ip, port);

		th.run();
		return (LinkedHashMap) ((Udp) th).getDecodedReply();
	}

	// The initial handshake that establishes bittorrent connection between
	// peers. Both sends and receives a message. Returns true if the response
	// contains metadata size, and supports ut_metadata, and false otherwise
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

		// Receive the message
		String hs_response = null;
		try {
			DataInputStream in = new DataInputStream(socket.getInputStream());
			// System.out.println("Waiting for received string: ");
			byte[] ca = new byte[20000];
			Thread.sleep(1000);
			in.read(ca, 0, ca.length);
			hs_response = new String(ca);
		} catch (IOException e) {
			// System.out.println("Read Timeout");
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		// System.out.print("Received string: ");
		// System.out.println(hs_response);
		if (hs_response != null) {
			setMetadataSize(findMetadataSize(hs_response));
			setUtMetadataCode(findUtMetadataCode(hs_response));
			if (metadataSize > 0 && utMetadataCode > 0) {
				return true;
			}
		}
		return false;
	}

	public void setMetadataSize(int metadataSize) {
		this.metadataSize = metadataSize;
	}

	public int getMetadataSize() {
		return metadataSize;
	}

	public void setUtMetadataCode(int utMetadataCode) {
		this.utMetadataCode = utMetadataCode;
	}

	public int getUtMetadataCode() {
		return utMetadataCode;
	}

	public void setReqPieceResponseMessageSize(int reqPieceResponseMessageSize) {
		this.reqPieceResponseMessageSize = reqPieceResponseMessageSize;
	}

	public int getReqPieceResponseMessageSize() {
		return reqPieceResponseMessageSize;
	}

	// Obtains total size of the metadata, from the handshake response
	private int findMetadataSize(String hs_response) {
		// gets substring starting at metadata_size, and then from that
		// substring returns the first substring between i and e
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

	// Obtains the code number assigned to ut_metadata, from the handshake
	// response
	private int findUtMetadataCode(String hs_response) {
		// gets substring starting at ut_metadata, and then from that
		// substring returns the first substring between i and e
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

	// Sends the extension header message, which needs to be sent before any
	// extension message
	private void sendExtensionHeader(DataOutputStream dos) {
		// extension header
		Map<byte[], byte[]> send_eh = new LinkedHashMap<byte[], byte[]>();
		Map<byte[], byte[]> m = new LinkedHashMap<byte[], byte[]>();
		m.put(benc.bencodeString("ut_metadata"), benc.bencodeInteger(2));
		send_eh.put(benc.bencodeString("m"), benc.bencodeDictionary(m));
		byte[] send_packet_eh = benc.bencodeDictionary(send_eh);
		//System.out.println("length of extension header: "
		//		+ send_packet_eh.length);
		// send the messages
		try {
			sendPrMessage(dos, send_packet_eh.length, 0);
			dos.writeBytes(new String(send_packet_eh));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	// The prefix message, attached to the beginning of any bittorrent message
	private void sendPrMessage(DataOutputStream dos, int msglen, int ut) {
		try {
			dos.writeInt(msglen + 2);
			dos.writeByte(20);
			dos.writeByte(ut);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	// Requests a piece of the metadata
	public void requestPiece(Socket socket, int ut, int pieceIx) {
		Map<byte[], byte[]> send_em = new LinkedHashMap<byte[], byte[]>();
		send_em.put(benc.bencodeString("msg_type"), benc.bencodeInteger(0));
		send_em.put(benc.bencodeString("piece"), benc.bencodeInteger(pieceIx));
		byte[] send_packet_em = benc.bencodeDictionary(send_em);
		// System.out.println("requestPieces packets: " + " " + new
		// String(send_packet_em));

		// Send the message
		DataOutputStream dos = null;
		try {
			dos = new DataOutputStream(socket.getOutputStream());
			sendExtensionHeader(dos);
			sendPrMessage(dos, send_packet_em.length, ut);
			dos.writeBytes(new String(send_packet_em));
			dos.flush();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	// Receives a piece of the metadata and returns it
	public String receivePiece(Socket socket) {
		DataInputStream in;
		String piece = null;
		try {
			in = new DataInputStream(socket.getInputStream());
			setReqPieceResponseMessageSize(getInputLength(in));
			byte[] ch = new byte[reqPieceResponseMessageSize];
			// System.out.println("reqPieceResponseMessageSize in Messages: " +
			// reqPieceResponseMessageSize);
			in.readFully(ch, 0, ch.length);
			piece = new String(ch);
			// System.out.println(piece);
		} catch (IOException e) {
			// System.out.println("receivePieces. Read Timeout");
		}
		return piece;
	}

	// The length of the tcp string to be read in bytes.
	private int getInputLength(DataInputStream in) {
		int numRead = 0;
		try {// Because the bittorrent protocol messages start with a 4-byte
				// message length
			numRead = in.readInt();
		} catch (IOException e) {
			e.printStackTrace();
		}

		return numRead;
	}

	// The get_peers dht protocol message. Returns decoded response (unbencoded)
	public Map getPeers(String info_hash, InetAddress ip, int port, String id)
			throws Exception {
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
		// System.out.println("DECODED getPeers: " + decoded_reply);
		return decoded_reply;
	}
}