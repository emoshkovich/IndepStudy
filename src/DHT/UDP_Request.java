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
	private String bootstrap_addr_str = "router.bittorrent.com";
	private int bootstrap_port = 6881;
	private InetAddress bootstrap_addr;
	private DatagramSocket socket;

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
		byte[] response_to_ping_b = new byte[ping_b.length];
		// Sending the message
		DatagramPacket dp_send = new DatagramPacket(ping_b, ping_b.length,
				bootstrap_addr, bootstrap_port);
		socket.send(dp_send);

		// Receiving the message
		DatagramPacket dp_receive = new DatagramPacket(response_to_ping_b,
				response_to_ping_b.length);
		socket.receive(dp_receive);
		System.out.println("Binary ping message: " + ping_b);
		System.out.println("Binary received data: " + dp_receive.getData());

		System.out.println();
		System.out.println("String of received data: "
				+ new String(dp_receive.getData()));
		System.out.println("String of received data: "
				+ new String(dp_receive.getData(), "US-ASCII"));
		System.out.println("String of received data: "
				+ new String(dp_receive.getData(), "UTF-8"));
		System.out.println("String of received data: "
				+ new String(dp_receive.getData(), "UTF-16"));
		System.out.println("String of received data: "
				+ new String(dp_receive.getData(), "UTF-32"));
		System.out.println("Received packet's address: "
				+ dp_receive.getAddress());
		System.out.println();

		/*
		 * This shows correct conversion from ping_b to it's string equivalent,
		 * so something is wrong with the data I receive, and not the conversion
		 * of the data
		 */
		System.out.println("String of original ping message: "
				+ new String(ping_b, "US-ASCII"));

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
		System.out.println("Received data: " + new String(dp_receive.getData()));
	}

	public static void main(String argv[]) throws Exception {
		UDP_Request req = new UDP_Request();
		//req.sendPing();
		req.sendFindNode();

	}
}
