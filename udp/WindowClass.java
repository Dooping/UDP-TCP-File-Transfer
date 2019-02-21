package udp;

import java.util.*;

public class WindowClass {
	private List<OurTftpPacket> window;
	private int size;
	
	public WindowClass(int size){
		this.window = new LinkedList<OurTftpPacket>();
		this.size = size;
	}
	
	public int getSize(){
		return size;
	}
	
	public void setSize(int n){
		if (n>0)
			size = n; //comentar pra nao permitir resize da janela
	}
	
	public void add(OurTftpPacket p){
		window.add(p);
	}
	
	public Iterator<OurTftpPacket> getAll(){
		return window.iterator();
	}
	
	public OurTftpPacket getFirst(){
		return window.get(0);
	}
	
	public void remove(int n){
		for (int i = 0; i<n && i<size; i++)
			window.remove(0);
	}
	
	public boolean isFull(){
		return window.size() >= size;
	}
	
	public boolean isEmpty(){
		return window.isEmpty();
	}
	
	public int clearAck(){
		OurTftpPacket a;
		int count = 0;
		while(window.size()>0){
			a = getFirst();
			if (!a.isAcknowledged())
				break;
			window.remove(0);
			count++;
		}
		return count;
	}
	
}
