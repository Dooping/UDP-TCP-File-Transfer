package udp;

public class OurTftpPacket extends TftpPacket{
	private long time;
	private boolean ack;
	
	public OurTftpPacket(){
		super();
		time = System.currentTimeMillis();
		ack = false;
	}
	
	public OurTftpPacket(byte[] packet, int length){
		super(packet, length);
		time = System.currentTimeMillis();
	}
	
	public void setTime(long time){
		this.time = time;
		ack = false;
	}
	
	public long getTime(){
		return time;
	}
	
	public void acknowledged(){
		ack = true;
	}
	
	public boolean isAcknowledged(){
		return ack;
	}

}
