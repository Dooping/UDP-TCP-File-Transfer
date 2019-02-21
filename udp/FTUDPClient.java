package udp;

/**
 * FTClientUDP - a very simple TFTP like client - RC 2014/15 FCT/UNL
 */

import static udp.TftpPacket.MAX_TFTP_PACKET_SIZE;
import static udp.TftpPacket.OP_ACK;
import static udp.TftpPacket.OP_DATA;
import static udp.TftpPacket.OP_ERROR;
import static udp.TftpPacket.OP_RRQ;

import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketTimeoutException;

public class FTUDPClient {
	static int BlockSize = 512; // Default block size as in TFTP RFC
	static int Timeout = 2000; //Default timeout

	public static void main(String[] args) throws Exception {

		switch (args.length) {
			case 4 :
				Timeout = Integer.parseInt(args[3]);
			case 3 :
				BlockSize = Integer.valueOf(args[2]);
			case 2 :
				break;
			default :
				System.out.printf("usage: java FTUDPClient filename servidor [blocksize [ timeout ]]\n");
				System.exit(0);
		}

		String filename = args[0];

		// Preparar endereco e o porto do servidor
		String server = args[1];
		SocketAddress srvAddr = new InetSocketAddress(server, FTUDPServer.DEFAULT_PORT);

		DatagramSocket socket = new DatagramSocket();
		receiveFile(socket, filename, srvAddr);
		socket.close();
	}
	private static void receiveFile(DatagramSocket socket, String filename, SocketAddress srvAddr) {
		System.out.println("receiving file:" + filename);
		int ntimeouts = 0;
		SocketAddress lastAddr = srvAddr;
		try {
			TftpPacket req = new TftpPacket().putShort(OP_RRQ).putString(filename).putByte(0).putString("octet").putByte(0);

			// For simplicity, we assume this datagram won't be lost...
			socket.send(new DatagramPacket(req.getPacketData(), req.getLength(), srvAddr));
			socket.setSoTimeout(Timeout);

			FileOutputStream f = new FileOutputStream(filename + ".bak");

			boolean finished = false;
			long expectedByte = 1; // expected byte
			do {
				byte[] buffer = new byte[MAX_TFTP_PACKET_SIZE];
				DatagramPacket datagram = new DatagramPacket(buffer, buffer.length);
				try{
					socket.receive(datagram);
					lastAddr = datagram.getSocketAddress();
					ntimeouts = 0;
					TftpPacket pkt = new TftpPacket(datagram.getData(), datagram.getLength());
					switch (pkt.getOpcode()) {
						case OP_DATA :
							if (pkt.getByteCount() == expectedByte) {
								byte[] data = pkt.getBlockData();
								System.err.println("got: " + pkt.getByteCount() + " size:" + data.length);
								f.write(data);
								expectedByte += data.length;
								finished = data.length < BlockSize;
							}
							sendAck(socket, expectedByte, datagram.getSocketAddress());
							break;
						case OP_ERROR :
							throw new IOException("Got error from server: " + pkt.getErrorCode() + ": " + pkt.getErrorMessage());
						default :
							throw new RuntimeException("Error receiving file:" + filename);
					}
				} catch (SocketTimeoutException e){
					sendAck(socket, expectedByte, lastAddr);
					ntimeouts++;
					if (ntimeouts > 5)
						throw new IOException("Connection has timed out!");
				}
				
			} while (!finished);
			f.close();

		} catch (IOException x) {
			System.err.println("Receive failed: " + x.getMessage());
		}

	}
	static void sendAck(DatagramSocket s, long expectedByte, SocketAddress dst) {
		TftpPacket ack = new TftpPacket().putShort(OP_ACK).putLong(expectedByte);
		System.out.printf("sent:  %d ack \n", ack.getByteCount());
		try {
			s.send(new DatagramPacket(ack.getPacketData(), ack.getLength(), dst));
		} catch (IOException e) {
			System.err.println("failed to send ack datagram");
		}
	}
}
