package DHT;

/* NOTES:
 * BitTorrent client will normally use ports 6881 to 6889.
 */
import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.HashMap;

/**
 * This class
 */
public class UDP_Request {
	public final int PACKET_SIZE = 512;

	private String bootstrap_addr_str = "router.bittorrent.com";
	private int bootstrap_port = 6881;
	private String id = "abcdefghij0123456789";
	private InetAddress bootstrap_addr;
	private DatagramSocket socket;

	private Bencoder benc = new Bencoder();

	public void sendPing() throws Exception {
		socket = new DatagramSocket();
		bootstrap_addr = InetAddress.getByName(bootstrap_addr_str);
		// System.out.println(bootstrap_addr);
		String ping_s = "d1:ad2:id20:abcdefghij0123456789e1:q4:ping1:t2:aa1:y1:qe";
		byte[] ping_b = ping_s.getBytes(); /*
											 * Note that the length of ping_b
											 * array is the same as the length
											 * of the ping_s string, 56
											 */
		byte[] response_to_ping_b = new byte[PACKET_SIZE];
		// Sending the message
		DatagramPacket dp_send = new DatagramPacket(ping_b, ping_b.length,
				bootstrap_addr, bootstrap_port);
		socket.send(dp_send);

		// Receiving the message
		DatagramPacket dp_receive = new DatagramPacket(response_to_ping_b,
				response_to_ping_b.length);
		socket.receive(dp_receive);

		HashMap decoded_reply = benc.unbencodeDictionary(dp_receive.getData());
		System.out.println("DECODED: " + decoded_reply);
		byte[] ip_and_port_bytes = (byte[]) decoded_reply.get("ip");

		//HashMap r = (HashMap) decoded_reply.get("r");
		//System.out.println("ID: " + r);
		
		byte[] ip_bytes = Arrays.copyOfRange(ip_and_port_bytes, 0, 4);
		InetAddress ip_address = InetAddress.getByAddress(ip_bytes);

		byte[] port_bytes = Arrays.copyOfRange(ip_and_port_bytes, 4, 6);
		short[] shorts = new short[1];
		ByteBuffer.wrap(port_bytes).order(ByteOrder.LITTLE_ENDIAN)
				.asShortBuffer().get(shorts);
		short signed_port = shorts[0];
		int port = signed_port >= 0 ? signed_port : 0x10000 + signed_port;

		System.out.println("returned ip address: " + ip_address);
		System.out.println("returned port: " + port);

		// Requestor's ip address. So far I do not need it.
		/*
		 * try { InetAddress addr = InetAddress.getLocalHost(); String ip =
		 * addr.getHostAddress(); System.out.println(ip); } catch
		 * (UnknownHostException e) { // TODO Auto-generated catch block
		 * e.printStackTrace(); }
		 */
	}

	public void sendFindNode() throws Exception {
		socket = new DatagramSocket();
		bootstrap_addr = InetAddress.getByName(bootstrap_addr_str);
		String fn_s = "d1:ad2:id20:abcdefghij01234567896:target20:mnopqrstuvwxyz123456e1:q9:find_node1:t2:aa1:y1:qe";
		byte[] fn_b = fn_s.getBytes();
		byte[] response_to_fn_b = new byte[fn_b.length];
		// Sending the message
		DatagramPacket dp_send = new DatagramPacket(fn_b, fn_b.length,
				bootstrap_addr, bootstrap_port);
		socket.send(dp_send);

		// Receiving the message
		DatagramPacket dp_receive = new DatagramPacket(response_to_fn_b,
				response_to_fn_b.length);
		socket.receive(dp_receive);
		System.out.println("Received data: " + new String(dp_receive.getData())
				+ "\n length: " + dp_receive.getLength());
	}

	public byte[] get_peers(String info_hash) throws Exception {
		HashMap<byte[], byte[]> args = new HashMap<byte[], byte[]>();		
		args.put(benc.bencodeString("id"), benc.bencodeString(id));
		args.put(benc.bencodeString("info_hash"), benc.bencodeString(info_hash));
		
		HashMap<byte[], byte[]> send_hm = new HashMap<byte[], byte[]>();
		send_hm.put(benc.bencodeString("t"), benc.bencodeString("aa"));
		send_hm.put(benc.bencodeString("y"), benc.bencodeString("q"));
		send_hm.put(benc.bencodeString("q"), benc.bencodeString("get_peers"));
		send_hm.put(benc.bencodeString("a"), benc.bencodeDictionary(args));
		byte[] send_packet = benc.bencodeDictionary(send_hm);
		String s = new String(send_packet);
		System.out.println(s);
		
		// Send the message and receive the response
		socket = new DatagramSocket();
		bootstrap_addr = InetAddress.getByName(bootstrap_addr_str);
		byte[] response_to_ping_b = new byte[PACKET_SIZE];
		DatagramPacket dp_send = new DatagramPacket(send_packet, send_packet.length,
				bootstrap_addr, bootstrap_port);
		socket.send(dp_send);

		// Receiving the message
		DatagramPacket dp_receive = new DatagramPacket(response_to_ping_b,
				response_to_ping_b.length);
		socket.receive(dp_receive);

		System.out.println(dp_receive.getLength());
		
		HashMap decoded_reply = benc.unbencodeDictionary(dp_receive.getData());
		System.out.println("DECODED getPeers: " + decoded_reply);
		HashMap r = (HashMap) decoded_reply.get("r");
		
		// The nodes array is 416 characters long
		byte[] nodes = (byte[]) r.get("nodes");
		 
		return nodes;
	}
}