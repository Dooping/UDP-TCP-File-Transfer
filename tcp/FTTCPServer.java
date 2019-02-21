package tcp;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;

public class FTTCPServer implements Runnable{
	
	public static final int DEFAULT_PORT = 8000;
	static final int BLOCKSIZE = 512;
	
	private Socket client;
	
	public FTTCPServer(Socket client){
		this.client = client;
	}

	public static void main(String[] args) {
		if (args.length != 0) {
			System.out.println("usage: java FTTCPServer");
			System.exit(0);
		}
		
		ServerSocket socket;
		try {
			socket = new ServerSocket(DEFAULT_PORT);
			System.out.println("New tftp server started at local port " + socket.getLocalPort());
			while(true){
				Socket clientsocket = socket.accept();
				new Thread(new FTTCPServer(clientsocket)).start();
				
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		

	}

	public void run() {
		System.out.println("INICIO!");
		receiveFile();
		System.out.println("FIM!");
	}

	private void receiveFile() {
		
		
		try {
			InputStream in = client.getInputStream();
			String filename = "";
			char c;
			while((c=(char)in.read())!='\0')
				filename+=c;
			FileOutputStream f = new FileOutputStream(filename + ".bak");
			byte[] buf = new byte[BLOCKSIZE] ;
			int n;
			while( (n = in.read( buf )) > 0 )
				f.write(buf, 0, n);
			client.close();
			f.close();
				
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

}
