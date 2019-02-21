package tcp;

/**
 * FileCopyTCPClient - cliente em TCP para transferencia de ficheiros 
 * para um servidor TCP a implementar.
 */

import java.io.FileInputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;

public class FTTCPClient {

	static final int BLOCKSIZE = 512;
	static final int PORT = 8000; // server port

	public static void main(String[] args) throws Exception {
		if (args.length != 2) {
			System.out.println("usage: java FTTCPClient filename host");
			System.exit(0);
		}

		String filename = args[0];
		String host = args[1];
		InetAddress server = InetAddress.getByName(host);

		// open file
		FileInputStream f = new FileInputStream(filename);
		System.out.println("Sending: " + filename);

		// Cria uma conexao para o servidor
		Socket socket = new Socket(server, PORT);
		// Obtem o canal de escrita associado ao socket.
		OutputStream os = socket.getOutputStream();

		os.write(filename.getBytes()); // envia nome do ficheiro
		os.write(new byte[]{0}); // envia separador

		long time = System.currentTimeMillis();
		int n;
		byte[] buf = new byte[BLOCKSIZE];
		while ((n = f.read(buf)) > 0)
			// copia o ficheiro para o servidor
			os.write(buf, 0, n);

		// Fecha o socket, quebrando a ligacao com o servidor.
		// (como consequencia tambem e' feito os.close() )
		socket.close();
		f.close();
		System.out.printf("Took %d milliseconds to copy file %s to the server at %s \n", System.currentTimeMillis() - time, filename, server);
		System.out.println("Done");
	}

}
