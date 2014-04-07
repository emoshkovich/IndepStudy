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
		if (port < 0 || port > 65535){
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
		System.out
		.println("send message to ip:   "+ ip + "     to port: " + port);
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
			//System.out.println("GOT TO EXCEPTION " + socket.isClosed());
			socket.close();
			//System.out.println("AFTER EXCEPTION " + socket.isClosed());
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

		//System.out.println("received packet length: " + dp_receive.getData().length);

		decoded_reply = benc.unbencodeDictionary(dp_receive.getData());
		
		System.out.println("decoded_reply: " + decoded_reply.size());
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

	//private String bootstrap_addr_str = "67.215.242.138";// "router.bittorrent.com";
	//private int bootstrap_port = 6881;
	//private String id = "abcdefghij0123456789";
	
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
		/*
		 * socket = new DatagramSocket(); bootstrap_addr =
		 * InetAddress.getByName(bootstrap_addr_str); //
		 * System.out.println(bootstrap_addr);
		 * 
		 * byte[] ping_b = ping_s.getBytes(); /* Note that the length of ping_b
		 * array is the same as the length of the ping_s string, 56
		 */
		/*
		 * byte[] response_to_ping_b = new byte[PACKET_SIZE]; // Sending the
		 * message DatagramPacket dp_send = new DatagramPacket(ping_b,
		 * ping_b.length, bootstrap_addr, bootstrap_port); socket.send(dp_send);
		 * 
		 * // Receiving the message DatagramPacket dp_receive = new
		 * DatagramPacket(response_to_ping_b, response_to_ping_b.length);
		 * socket.receive(dp_receive);
		 * 
		 * HashMap decoded_reply =
		 * benc.unbencodeDictionary(dp_receive.getData());
		 * System.out.println("DECODED: " + decoded_reply);
		 */

		//InetAddress bootstrap_addr = InetAddress.getByName(bootstrap_addr_str);
		LinkedHashMap decoded_reply = udpRequestResponse(ping_s.getBytes(),
				ip, port);
		System.out.println(decoded_reply);
		byte[] ip_and_port_bytes = (byte[]) decoded_reply.get("ip");

		getIp(ip_and_port_bytes);
		getPort(ip_and_port_bytes);
	}

	private InetAddress getIp(byte[] compactInfo) throws UnknownHostException {
		byte[] ip_bytes = Arrays.copyOfRange(compactInfo, 0, 4);
		System.out
				.println("returned ip: " + InetAddress.getByAddress(ip_bytes));
		
		return InetAddress.getByAddress(ip_bytes);
	}

	private int getPort(byte[] compactInfo) throws UnknownHostException {
	//	byte[] port_bytes = Arrays.copyOfRange(compactInfo, compactInfo.length-2, compactInfo.length);
		byte[] port_bytes = Arrays.copyOfRange(compactInfo, 4, 6);
		short[] shorts = new short[1];
		ByteBuffer.wrap(port_bytes).order(ByteOrder.BIG_ENDIAN)
				.asShortBuffer().get(shorts);
		short signed_port = shorts[0];
		Integer port = signed_port >= 0 ? signed_port : 0x10000 + signed_port;
		System.out.println("PORT: "+ port + " unsigned port: " + shorts[0]);
		
		/* port = 0;
    	port |= compactInfo[compactInfo.length-2] & 0xFF;
    	System.out.println("port1: " + port);
    	port <<= 8;
    	System.out.println("port2: " + port);
    	port |= compactInfo[compactInfo.length-1] & 0xFF;
    	System.out.println("port3: " + port);
    	System.out.println("PORT: "+ port + "  " + compactInfo[compactInfo.length-2] + " " + compactInfo[compactInfo.length-1]);
    	*/
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
				//System.out.println("Waiting for th to complete...");
				th.wait();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		//System.out.println("Exited synchronized(th)");

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
		System.out.println("ENTER FIND_NODE");
		//InetAddress bootstrap_addr = InetAddress.getByName(bootstrap_addr_str);
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
		System.out.println("find_node packet: " + s);
		byte[] nodes = null;
		

		Map decoded_reply = udpRequestResponse(send_packet, ip, port);

		System.out.println("DECODED find_node: " + decoded_reply);
		if (decoded_reply.isEmpty()) {
			System.out.println("decoded_reply is empty");
		}
		if (!decoded_reply.isEmpty()) {
			Map r = (LinkedHashMap) decoded_reply.get("r");
			// The nodes array is 416 characters long
			nodes = (byte[]) r.get("nodes");
			addCompactInfo(nodes, findNodeCompactInfoList);
			for (int i = 0; i < findNodeCompactInfoList.size(); i++) {
				System.out.println(i);

				byte[] node_info = (byte[]) findNodeCompactInfoList.get(i);
				ip = getIp(node_info);
				port = getPort(node_info);
				//Send find node with new info
				findNode(ip, port, id);
			}
		}
		System.out.println("SIZE OF THE ARRAY LIST: " + findNodeCompactInfoList.size());
		/*
		 * String fn_s =
		 * "d1:ad2:id20:abcdefghij01234567896:target20:mnopqrstuvwxyz123456e1:q9:find_node1:t2:aa1:y1:qe"
		 * ; byte[] fn_b = fn_s.getBytes(); byte[] response_to_fn_b = new
		 * byte[fn_b.length]; // Sending the message DatagramPacket dp_send =
		 * new DatagramPacket(fn_b, fn_b.length, bootstrap_addr,
		 * bootstrap_port); socket.send(dp_send);
		 * 
		 * // Receiving the message DatagramPacket dp_receive = new
		 * DatagramPacket(response_to_fn_b, response_to_fn_b.length);
		 * socket.receive(dp_receive); System.out.println("Received data: " +
		 * new String(dp_receive.getData()) + "\n length: " +
		 * dp_receive.getLength());
		 */

		System.out.println("EXIT FIND_NODE");
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
		//System.out.println("SIZE OF THE ARRAY LIST: " + compactInfoList.size());
	}

	public byte[] getPeers(String info_hash, InetAddress ip, int port, String id) throws Exception {
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
		//InetAddress address = InetAddress.getByName(bootstrap_addr_str);
		//int port = bootstrap_port;

		for (int i = 0; i < 100; i++) {
			System.out.println(i);
			Map decoded_reply = udpRequestResponse(send_packet, ip, port);

			System.out.println("DECODED getPeers: " + decoded_reply);
			Map r = (LinkedHashMap) decoded_reply.get("r");

			// The nodes array is 416 characters long
			nodes = (byte[]) r.get("nodes");

			byte[] first_node = Arrays.copyOfRange(nodes, 0, 26);
			ip = getIp(first_node);
			port = getPort(first_node);

			// handShake(address, port, info_hash, id);
		}
		return nodes;
	}
}