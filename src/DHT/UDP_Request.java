package DHT;

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
import java.util.Map;

class MessageThread extends Thread {
	private Bencoder benc = new Bencoder();
	private byte[] send_packet;
	private InetAddress ip;
	private int port;
	private Map decoded_reply;

	public MessageThread(byte[] send_packet, InetAddress ip, int port) {
		this.send_packet = send_packet;
		this.ip = ip;
		this.port = port;
	}

	public synchronized void run() {
		if (port < 0 || port > 65535) {
			System.out.println("ERROR: wrong port number");
			return;
		}
		System.out.println(Thread.currentThread().getName());
		// Send the message and receive the response
		DatagramSocket socket = null;
		try {
			socket = new DatagramSocket();
		} catch (SocketException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		byte[] response_to_ping_b = new byte[UDP_Request.PACKET_SIZE];
		DatagramPacket dp_send = new DatagramPacket(send_packet,
				send_packet.length, ip, port);
		System.out.println("send message to ip:   " + ip + "     to port: "
				+ port);
		try {
			socket.send(dp_send);
			socket.setSoTimeout(1000);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
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
			notify();
		}

		decoded_reply = benc.unbencodeDictionary(dp_receive.getData());

	}

	public Map getDecodedReply() {
		return decoded_reply;
	}

}

/**
 * This class
 */
public class UDP_Request {
	public final static int PACKET_SIZE = 512;

	// private String bootstrap_addr_str = "67.215.242.138";//
	// "router.bittorrent.com";
	// private int bootstrap_port = 6881;
	// private String id = "abcdefghij0123456789";

	// private InetAddress bootstrap_addr;
	private DatagramSocket socket;

	// UDP requests parameters data
	private byte[] send_packet;
	private InetAddress ip;
	private int port;
	// private Map decoded_reply;

	private static ArrayList findNodeCompactInfoList = new ArrayList();
	private static ArrayList getPeersCompactInfoList = new ArrayList();

	private Bencoder benc = new Bencoder();

	public void sendPing(InetAddress ip, int port, String id) throws Exception {
		// tcpRequestResponse("", InetAddress.getByName("bittorrent.com"),
		// 6881);
		String ping_s = "d1:ad2:id20:abcdefghij0123456789e1:q4:ping1:t4:aaaa1:y1:qe";

		LinkedHashMap decoded_reply = udpRequestResponse(ping_s.getBytes(), ip,
				port);
		System.out.println("Ping reply: " + decoded_reply);
		byte[] ip_and_port_bytes = (byte[]) decoded_reply.get("ip");

		getIp(ip_and_port_bytes);
		getPort(ip_and_port_bytes);
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
		System.out.println("PORT: " + port);

		return port;
	}

	private LinkedHashMap udpRequestResponse(byte[] send_packet,
			InetAddress ip, int port) throws IOException {
		this.send_packet = send_packet;
		this.ip = ip;
		this.port = port;

		Thread th = new MessageThread(send_packet, ip, port);
		th.start();
		synchronized (th) {
			try {
				th.wait();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}

		return (LinkedHashMap) ((MessageThread) th).getDecodedReply();
	}

	/*
	 * private LinkedHashMap tcpRequestResponse(String send_packet, InetAddress
	 * ip, int port) throws IOException { System.out.println("tcp: ip: " + ip +
	 * ", port: " + port); Socket socket = new Socket(ip, port);
	 * 
	 * System.out.println("last thing that worked"); // Send the message
	 * DataOutputStream dos = new DataOutputStream(socket.getOutputStream()); if
	 * (send_packet != null && socket != null) { dos.writeBytes(send_packet); }
	 * 
	 * // Receive the message BufferedReader in = new BufferedReader(new
	 * InputStreamReader( socket.getInputStream()));
	 * System.out.print("Received string: ");
	 * 
	 * while (!in.ready()) { } System.out.println(in.readLine()); // Read one
	 * line and output it
	 * 
	 * System.out.print("'\n"); in.close(); return null; }
	 */

	public void handShake(InetAddress ip, int port, String info_hash, String id)
			throws IOException {
		System.out.println("tcp: ip: " + ip + ", port: " + port);
		Socket socket = new Socket(ip, port);

		System.out.println("last thing that worked");
		// Send the message
		DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
		if (socket != null) {
			dos.writeByte(19);
			dos.writeBytes("BitTorrent protocol");// possibly need write()
													// instead of writeBytes()
			dos.write(new byte[8]);
			dos.writeBytes(info_hash);
			dos.writeBytes(id);
		}

		// Receive the message
		BufferedReader in = new BufferedReader(new InputStreamReader(
				socket.getInputStream()));
		System.out.print("Received string: ");

		while (!in.ready()) {
		}
		System.out.println(in.readLine()); // Read one line and output it

		System.out.print("'\n");
		in.close();
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

		if (!decoded_reply.isEmpty()) {
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

				// getPeers(info_hash, ip, port, id);
			}
		}
	}

	// Note that each node has 26 bytes: 20 for id, 4 for ip, and 2 for port
	private void addCompactInfo(byte[] nodes, ArrayList al) {
		if ((nodes.length % 26) != 0) {
			System.out.println("nodes compact info has wrong length: "
					+ nodes.length);
		}
		for (int i = 20; i < nodes.length;) {
			int j = i + 26;
			al.add(Arrays.copyOfRange(nodes, i, j));
			i = j;
		}
		// System.out.println("SIZE OF THE ARRAY LIST: " +
		// compactInfoList.size());
	}

	public byte[] getPeers(String info_hash, InetAddress ip, int port, String id)
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
		String s = new String(send_packet);
		System.out.println("get_peers packet: " + s);
		byte[] nodes = null;
		byte[] values = null;
		
		// InetAddress address = InetAddress.getByName(bootstrap_addr_str);
		// int port = bootstrap_port;

		Map decoded_reply = udpRequestResponse(send_packet, ip, port);

		System.out.println("DECODED getPeers: " + decoded_reply);

		if (!decoded_reply.isEmpty()) {
			Map r = (LinkedHashMap) decoded_reply.get("r");
			values = (byte[]) r.get("values");
			
			if (values != null){
				System.out.println("VALUES NOT NULL: "+ values);
			}
			System.out.println("VALUES: "+ values);
			
			nodes = (byte[]) r.get("nodes");
			addCompactInfo(nodes, getPeersCompactInfoList);
			for (int i = 0; i < getPeersCompactInfoList.size(); i++) {
				byte[] node_info = (byte[]) getPeersCompactInfoList.get(i);
				ip = getIp(node_info);
				port = getPort(node_info);

				// Send get peers request with new info
				getPeers(info_hash, ip, port, id);
			}
		}
		// handShake(address, port, info_hash, id);
		return nodes;
	}
}