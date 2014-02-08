package DHT;

/* NOTES:
 * BitTorrent client will normally use ports 6881 to 6889.
 */
import java.io.*;
import java.net.*;

/**
 * This class
 */
public class UDP_Request {
	public final int PACKET_SIZE = 512;
	private String bootstrap_addr_str = "router.bittorrent.com";
	private int bootstrap_port = 6881;
	private InetAddress bootstrap_addr;
	private DatagramSocket socket;

	private Bencoder benc = new Bencoder();

	private void sendPing() throws Exception {
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

		String temp = new String(dp_receive.getData());
		System.out.println("BENCODED: "
				+ temp);
		System.out.println("DECODED: "
				+ benc.unbencodeDictionary(dp_receive.getData()));

		byte[] ip = new byte[4];
		for (int j = 0; j < dp_receive.getLength(); j++){
		for (int i = j; i < j+4; i++){
			ip[i-j] = dp_receive.getData()[i];
		}
		System.out.println("ip using the response message: " + InetAddress.getByAddress(ip));
		}
		System.out.println("ip using getAddresst(): " + dp_receive.getAddress());
		System.out.println("port using getPort(): " + dp_receive.getPort());
		// remove this after done. For now, keep it to make sure the number is
		// always less than 512
		System.out.println("length of received packet: "
				+ dp_receive.getLength() + " " + temp.length());

		// Requestor's ip address. So far I do not need it.
		/*
		 * try { InetAddress addr = InetAddress.getLocalHost(); String ip =
		 * addr.getHostAddress(); System.out.println(ip); } catch
		 * (UnknownHostException e) { // TODO Auto-generated catch block
		 * e.printStackTrace(); }
		 */
	}

	private void sendFindNode() throws Exception {
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

	public static void main(String argv[]) throws Exception {
		UDP_Request req = new UDP_Request();
		req.sendPing();
		// req.sendFindNode();

	}
}
