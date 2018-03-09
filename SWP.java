/*===============================================================*
 *  Submitted by: Saklani Pankaj(U1621055F)                      *
 *  Lab: TS1                                                     *
 *===============================================================*/

/*===============================================================*
 *  File: SWP.java                                               *
 *                                                               *
 *  This class implements the sliding window protocol            *
 *  Used by VMach class					         *
 *  Uses the following classes: SWE, Packet, PFrame, PEvent,     *
 *                                                               *
 *  Author: Professor SUN Chengzheng                             *
 *          School of Computer Engineering                       *
 *          Nanyang Technological University                     *
 *          Singapore 639798                                     *
 *===============================================================*/

import java.util.Timer;
import java.util.TimerTask;

public class SWP {

	/*
	 * ========================================================================* the
	 * following are provided, do not change them!!
	 * ========================================================================
	 */
	// the following are protocol constants.
	public static final int MAX_SEQ = 7;
	public static final int NR_BUFS = (MAX_SEQ + 1) / 2;

	// the following are protocol variables
	private int oldest_frame = 0;
	private PEvent event = new PEvent();
	private Packet out_buf[] = new Packet[NR_BUFS];

	// the following are used for simulation purpose only
	private SWE swe = null;
	private String sid = null;

	// Constructor
	public SWP(SWE sw, String s) {
		swe = sw;
		sid = s;
	}

	// the following methods are all protocol related
	private void init() {
		for (int i = 0; i < NR_BUFS; i++) {
			out_buf[i] = new Packet();
		}
	}

	private void wait_for_event(PEvent e) {
		swe.wait_for_event(e); // may be blocked
		oldest_frame = e.seq; // set timeout frame seq
	}

	private void enable_network_layer(int nr_of_bufs) {
		// network layer is permitted to send if credit is available
		swe.grant_credit(nr_of_bufs);
	}

	private void from_network_layer(Packet p) {
		swe.from_network_layer(p);
	}

	private void to_network_layer(Packet packet) {
		swe.to_network_layer(packet);
	}

	private void to_physical_layer(PFrame fm) {
		System.out.println("SWP: Sending frame: seq = " + fm.seq + " ack = " + fm.ack + " kind = "
				+ PFrame.KIND[fm.kind] + " info = " + fm.info.data);
		System.out.flush();
		swe.to_physical_layer(fm);
	}

	private void from_physical_layer(PFrame fm) {
		PFrame fm1 = swe.from_physical_layer();
		fm.kind = fm1.kind;
		fm.seq = fm1.seq;
		fm.ack = fm1.ack;
		fm.info = fm1.info;
	}

	/*
	 * ===========================================================================*
	 * implement your Protocol Variables and Methods below:
	 * ==========================================================================
	 */
	// no nak has been sent yet
	private boolean no_nak = true;
	// Timer Array to store timers for DATA frames with the same size as the
	// slidding window
	private Timer frameTimer[] = new Timer[NR_BUFS];
	// Timer for ACK frames
	private Timer ackTimer = new Timer();

	// Final values to store the intervals of the two timeouts
	private static final int DATA_TIMEOUT_INTERVAL = 500;
	private static final int ACK_TIMEOUT_INTERVAL = 300;

	// Method used to increment a interger and making sure it is within the range of
	// the max sequence number
	public int inc(int n) {
		return (n + 1) % (MAX_SEQ + 1);
	}

	/*
	 * Method to check if the frame received is within the sliding window a: lower
	 * edge of sliding window b: frame sequence number c: upper edge of sliding
	 * window
	 */
	public static boolean between(int a, int b, int c) {
		return ((a <= b) && (b < c)) || ((c < a) && (a <= b)) || ((b < c) && (c < a));
	}

	// Method for creating and sending a data, ack, or nak frame
	public void send_frame(int frame_kind, int frame_no, int frame_expected, Packet buffer[]) {

		// Initialise the outbound frame
		PFrame outbound = new PFrame();

		outbound.kind = frame_kind;

		// Setting the Squence Number only used by DATA frames
		outbound.seq = frame_no;

		// Sets the acknowledgement field in the new frame to piggyback the
		// acknowledgement of a previously received frame
		outbound.ack = (frame_expected + MAX_SEQ) % (MAX_SEQ + 1);

		// Sending DATA Frame
		if (frame_kind == PFrame.DATA) {

			// Retrieving the DATA from the buffer slot, using the sequence number
			outbound.info = buffer[frame_no % NR_BUFS];
		}
		// Sending NAK Frame
		else if (frame_kind == PFrame.NAK) {
			no_nak = false;
		}

		// Transmit the frame
		to_physical_layer(outbound);

		/*
		 * Timer is set and starts when sender sends out a DATA frame so that the sender
		 * will know when the frame needs to be resent by using the timer
		 */
		if (frame_kind == PFrame.DATA) {
			start_timer(frame_no);
		}

		// No need for separate ACK frame
		stop_ack_timer();

	}

	public void protocol6() {

		init();
		while (true) {

			wait_for_event(event);
			switch (event.type) {
			case (PEvent.NETWORK_LAYER_READY):
				break;
			case (PEvent.FRAME_ARRIVAL):
				break;
			case (PEvent.CKSUM_ERR):
				break;
			case (PEvent.TIMEOUT):
				break;
			case (PEvent.ACK_TIMEOUT):
				break;
			default:
				System.out.println("SWP: undefined event type = " + event.type);
				System.out.flush();
			}
		}
	}

	/*
	 * Note: when start_timer() and stop_timer() are called, the "seq" parameter
	 * must be the sequence number, rather than the index of the timer array, of the
	 * frame associated with this timer,
	 */

	// TimerTask for retransmission of DATA Frame, when startTimer runs out
	class FrameRetransmissionTask extends TimerTask {
		private SWE swe = null;
		public int seq;

		// Constructor for
		public FrameRetransmissionTask(SWE swe, int seq){
         this.swe = swe;
         this.seq = seq
      }

		public void run() {
			stop_timer(seq);

			swe.generate_timeout_event(seq);

		}

	}

	// TimerTask for retransmission of ACK Frame, when ackTimer runs out
	class ACKRetransmissionTask extends TimerTask {

		private SWE swe = null;

		// Create a ACK retransmission task object
		public ACKRetransmissionTask(SWE swe) {
			this.swe = swe;
		}

		public void run() {
			// Stop the timer
			stop_ack_timer();

			// Generate a timeout event on the SWE
			swe.generate_acktimeout_event();
		}
	}

	// Methods called when a DATA Frame is sent
	private void start_timer(int seq) {

		// Stop timer is already running
		stop_timer(seq);

		frameTimer[seq % NR_BUF] = new Timer();

		// Schedule a Retransmission
		frameTimer[seq % NR_BUF].schedule(new FrameRetransmissionTask(seq), DATA_TIMEOUT_INTERVAL);
	}

	// Method to stop timer of a frame
	private void stop_timer(int seq) {

		// Ensure that a NULL timer is not referenced
		if (frameTimer[seq % NR_BUFS] != null) {
			frameTimer[seq % NR_BUFS].cancel();
		}

	}

	// Method to start timer when the reciever sends out ACK frame
	private void start_ack_timer() {
		// Stop timer is already running
		stop_ack_timer();

		// reinitlise the timer
		ackTimer = new Timer();

		// Schedule a Retransmission of ACK Frame
		ackTimer.schedule(new ACKRetransmissionTask(swe), ACK_TIMEOUT_INTERVAL);
	}

	// Method to stop the timer when the receiver sends out another ACK frame
	private void stop_ack_timer() {
		// Cancel only if initilised
		if (ackTimer != NULL)
			// Terminates a Timer and discards any scheduled tasks
			ackTimer.cancel();
	}

}// End of class

/*
 * Note: In class SWE, the following two public methods are available: .
 * generate_acktimeout_event() and . generate_timeout_event(seqnr).
 * 
 * To call these two methods (for implementing timers), the "swe" object should
 * be referred as follows: swe.generate_acktimeout_event(), or
 * swe.generate_timeout_event(seqnr).
 */
