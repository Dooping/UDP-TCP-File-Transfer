package udp;

/**
 * TftpServer - a very simple TFTP like server - RC FCT/UNL
 * 
 * Limitations:
 * 		default port is not 69;
 * 		ignores mode (always works as octal);
 *              ignores all options
 *              only sends files
 * 		only sends files always assuming the default timeout
 *              data and ack blocks are a long isntead of a short
 *              assumes that the sequence number and ack number is always equal
 *              to the order of the first byte (begining at 1) of the block sent
 *              or acked
 * Note: this implementation assumes that all Java Strings used contain only
 * ASCII characters. If it's not so, lenght() and getBytes().lenght return different sizes
 * and unexpected problems can appear ... 
 **/

import static udp.TftpPacket.MAX_TFTP_PACKET_SIZE;
import static udp.TftpPacket.OP_ACK;
import static udp.TftpPacket.OP_DATA;
import static udp.TftpPacket.OP_ERROR;
import static udp.TftpPacket.OP_RRQ;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;

public class FTUDPServer implements Runnable {
	static int DEFAULT_PORT = 10512; // my default port
	static int DEFAULT_TIMEOUT = 2000; // 2 sec.
	static int DEFAULT_BLOCKSIZE = 512; // default block size as in TFTP
										// RFC

	static int DEFAULT_MAX_RETRIES = 3; // default max retries
	static double DEFAULT_RTT_PLUS_DELTA_RATIO = 1.125; 

	static final String[] ACCEPTED_OPTIONS = new String[]{"blksize", "timeout"};

	// Change it for your tests, according
	// to the block size in your client
	
	private final static int DEFAULT_WINDOW_SIZE = 20;
	private final static int DEFAULT_MIN_TIMEOUT = 100;

	private int timeout = DEFAULT_TIMEOUT;
	private int blockSize = DEFAULT_BLOCKSIZE;
	private SocketAddress cltAddr;

	private String filename;
	long sumPackets,sumAcks,resent;
	
	private Queue<Integer> rtt = new LinkedList<Integer>();
	private List<Integer> sumrtt = new LinkedList<Integer>();

	FTUDPServer(TftpPacket req, SocketAddress cltAddr) {
		this.cltAddr = cltAddr;

		Map<String, String> options = req.getOptions();
		options.keySet().retainAll(Arrays.asList(ACCEPTED_OPTIONS));
		if (options.containsKey("timeout"))
			timeout = Integer.valueOf(options.get("timeout"));

		if (options.containsKey("blksize"))
			blockSize = Integer.valueOf(options.get("blksize"));

		if (options.size() > 0)
			System.out.println("Using supported options:" + options);

		filename = req.getFilename();
		sumPackets = 0;
		sumAcks = 0;
		resent = 0;
	}

	public void run() {
		System.out.println("INICIO!");
		sendFile(filename);
		System.out.println("FIM!");
	}

	/**
	 * Sends an error packet
	 *
	 * @param s
	 *            - Socket to use to communicate
	 * @param err
	 *            , str - Error number and description
	 * @param host
	 *            , port - Destination IP and Port
	 */
	private static void sendError(DatagramSocket s, int err, String str, SocketAddress dstAddr) throws IOException {
		TftpPacket pkt = new TftpPacket().putShort(OP_ERROR).putShort(err).putString(str).putByte(0);
		s.send(new DatagramPacket(pkt.getPacketData(), pkt.getLength(), dstAddr));
	}
	
	private void sendFile(String file){
		int ntimeouts = 0;			//detecta perdas de conexao
		boolean firstTimeout = false; //controla o slowstart
		long time = System.currentTimeMillis();
		long startTime = time; //tempo de começo da transferencia
		try {
			MyDatagramSocket sendingSocket = new MyDatagramSocket(); //alterar pra nao trabalhar com MyDatagramSocket		
			System.out.println("sending file: \"" + file + "\" to client: " + cltAddr + " from local port:" + sendingSocket.getLocalPort());
			try {
				FileInputStream f = new FileInputStream(file);

				long count = 1; // block count starts at 1
				
				WindowClass window = new WindowClass(DEFAULT_WINDOW_SIZE); //criar a janela
				int n = 0;
				byte[] buffer = new byte[blockSize];
				
				while(n>=0 || !window.isEmpty()){
					while(n>=0 && !window.isFull()){ //le até a window estar cheia ou eof
						n = f.read(buffer);
						if (n>0){
							OurTftpPacket pkt = new OurTftpPacket();
							pkt.putShort(OP_DATA).putLong(count).putBytes(buffer, n);
							pkt.setTime(System.currentTimeMillis());
							window.add(pkt);
							count += n;	
							send(sendingSocket,pkt,cltAddr);
						}
						//manda ultimo pacote se necessario
						if ((((count - 1L) % blockSize) == 0L) && (n == -1) && (!window.isFull())){
							OurTftpPacket pkt = new OurTftpPacket();
							pkt.putShort(OP_DATA).putLong(count).putBytes(new byte[0], 0);
							window.add(pkt);
							send(sendingSocket,pkt,cltAddr);
						}
					}
					if (!window.isEmpty())
						if(timeout + (window.getFirst().getTime() - System.currentTimeMillis())>0)
							sendingSocket.setSoTimeout(timeout + (int)(window.getFirst().getTime() - System.currentTimeMillis()));
						else{
							ntimeouts++;
							firstTimeout = true;
							window.setSize(window.getSize()/2);
							if (ntimeouts > DEFAULT_MAX_RETRIES)
								throw new IOException("Connection timed out!");
							resend(sendingSocket, window, cltAddr);
						}
					try{//tenta receber um ACK
						receive(sendingSocket, window);
						if (!firstTimeout)
							window.setSize(window.getSize()+1); //slowstart
						else if (time + timeout<System.currentTimeMillis()){ //aumento da janela linear
								time = System.currentTimeMillis();
								window.setSize(window.getSize()+1);
							 }
						
						ntimeouts = 0;
					} catch (SocketTimeoutException e){
						firstTimeout = true;
						window.setSize(window.getSize()/2);
						timeout = DEFAULT_TIMEOUT;
						rtt.clear();
						ntimeouts++;
						if (ntimeouts > DEFAULT_MAX_RETRIES)
							throw new IOException("Connection timed out!");
						resend(sendingSocket, window, cltAddr);
					}
				}

				f.close();
				sendingSocket.close();
				
				//impressao de resultados
				System.out.println("Total de bytes transferidos: "+(count-1));
				System.out.println("Numero total de pacotes enviados com dados: "+sumPackets);
				System.out.println("Numero total de pacotes recebidos com ACKs: "+sumAcks);
				System.out.println("Tempo total que durou a transferencia: "+(System.currentTimeMillis()-startTime) + " milissegundos");
				int avg=0;
				Iterator<Integer> it = sumrtt.iterator();
				while (it.hasNext())
					avg += it.next();
				System.out.println("Valor medio do RTT calculado: "+avg/sumrtt.size() + " milissegundos");
				System.out.println("N de blocos reenviados: "+resent);

			} catch (FileNotFoundException e) {
				System.err.printf("Can't read \"%s\"\n", file);
				sendError(sendingSocket, 1, "file not found", cltAddr);
			} catch (IOException e) {
				System.err.println("Failed with error \n" + e.getMessage());
				sendError(sendingSocket, 2, e.getMessage(), cltAddr);
			} 
		} catch (Exception x) {
			x.printStackTrace();
		}
	}
	
	private void resend(DatagramSocket sock, WindowClass window, SocketAddress addr) throws IOException{
		Iterator<OurTftpPacket> it = window.getAll();
		OurTftpPacket a;
		while (it.hasNext()){
			a = it.next();
			a.setTime(System.currentTimeMillis());
			send(sock, a, cltAddr);
			resent++;
		}
	}
	
	private void send(DatagramSocket sock, OurTftpPacket blk, SocketAddress dst) throws IOException{
		System.err.println("sending:" + blk.getByteCount() + "/size:" + blk.getBlockData().length);
		if (!sock.isClosed()){
			sock.send(new DatagramPacket(blk.getPacketData(), blk.getLength(), dst));
			sumPackets++;
		}
	}
	
	private int receive(DatagramSocket sock, WindowClass window) throws SocketException, IOException {
		byte[] buffer = new byte[MAX_TFTP_PACKET_SIZE];
		DatagramPacket msg = new DatagramPacket(buffer, buffer.length);
		sock.receive(msg); // waits for ACK
		TftpPacket ack = new TftpPacket(msg.getData(), msg.getLength());
		System.out.println("got ack:" + ack.getByteCount());
		sumAcks++;
		Iterator<OurTftpPacket> it = window.getAll();
		OurTftpPacket a;
		while (it.hasNext()){
			a = it.next();
			if (a.getByteCount()==ack.getByteCount()-blockSize && !a.isAcknowledged()){
				sumrtt.add((int)(System.currentTimeMillis()-a.getTime()));
				rtt.add((int)(System.currentTimeMillis()-a.getTime()));
				if (rtt.size()>5)
					rtt.remove();
				Iterator<Integer> itt = rtt.iterator();
				int sum=0;
				while(itt.hasNext())
					sum += itt.next();
				timeout = (int)Math.round((sum/rtt.size()) * DEFAULT_RTT_PLUS_DELTA_RATIO);
				timeout = Math.max(timeout, DEFAULT_MIN_TIMEOUT);
			}
			if (a.getByteCount()<ack.getByteCount())
				a.acknowledged();
		}
		int count = window.clearAck();
		return count;
	}

	public static void main(String[] args) throws Exception {
		switch (args.length) {
			case 4 :
				DEFAULT_MAX_RETRIES = Integer.valueOf(args[3]);
			case 3 :
				DEFAULT_TIMEOUT = Integer.valueOf(args[2]);
			case 2 :
				DEFAULT_BLOCKSIZE = Integer.valueOf(args[1]);
			case 1 :
				DEFAULT_PORT = Integer.valueOf(args[0]);
			case 0 :
				break;
			default :
				System.err.println("usage: java FTUDPServer [port [blocksize [timeout [retries]]]]");
				System.exit(0);
		}

		// create and bind socket to port for receiving client requests
		DatagramSocket mainSocket = new DatagramSocket(DEFAULT_PORT);
		System.out.println("New tftp server started at local port " + mainSocket.getLocalPort());
		MyDatagramSocket.init(10000, 0); //comentar se usarmos DatagramSocket

		for (;;) { // infinite processing loop...
			try {
				// prepare an empty datagram ...
				byte[] buffer = new byte[MAX_TFTP_PACKET_SIZE];
				DatagramPacket msg = new DatagramPacket(buffer, buffer.length);

				mainSocket.receive(msg);

				// look at datagram as a TFTP packet
				TftpPacket req = new TftpPacket(msg.getData(), msg.getLength());
				switch (req.getOpcode()) {
					case OP_RRQ : // Read Request
						System.out.println("Read Request");

						// Launch a dedicated thread to handle the client
						// request...
						new Thread(new FTUDPServer(req, msg.getSocketAddress())).start();
						break;
					default : // unexpected packet op code!
						System.err.printf("? packet opcode %d ignored\n", req.getOpcode());
						sendError(mainSocket, 0, "Unknown request type..." + req.getOpcode(), msg.getSocketAddress());
				}
			} catch (Throwable t) {
				t.printStackTrace();
			}
		}
	}

	static void sleep(int ms) {
		try {
			Thread.sleep(ms);
		} catch (InterruptedException e) {
		}
	}
}
