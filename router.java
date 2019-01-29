import java.io.*;
import java.net.*;
import java.util.*;
import java.nio.*;
class pkt_HELLO
{
  public int router_id;
  public int link_id;

  public pkt_HELLO (int router_id, int link_id)
  {
    this.router_id = router_id;
    this.link_id = link_id;
  }
  public byte[] getData ()
  {
    ByteBuffer buffer = ByteBuffer.allocate (8);
    buffer.order (ByteOrder.LITTLE_ENDIAN);
    buffer.putInt (router_id);
    buffer.putInt (link_id);
    return buffer.array ();
  }
  public static pkt_HELLO ParseData (byte[]UDPdata) throws Exception
  {
    ByteBuffer buffer = ByteBuffer.wrap (UDPdata);
      buffer.order (ByteOrder.LITTLE_ENDIAN);
    int router_id = buffer.getInt ();
    int link_id = buffer.getInt ();
      return new pkt_HELLO (router_id, link_id);
  }
}

class pkt_LSPDU
{
  public int sender;
  public int router_id;
  public int link_id;
  public int cost;
  public int via;

  public pkt_LSPDU (int sender, int router_id, int link_id, int cost, int via)
  {
    this.sender = sender;
    this.router_id = router_id;
    this.link_id = link_id;
    this.cost = cost;
    this.via = via;
  }
  public byte[] getData ()
  {
    ByteBuffer buffer = ByteBuffer.allocate (20);
    buffer.order (ByteOrder.LITTLE_ENDIAN);
    buffer.putInt (sender);
    buffer.putInt (router_id);
    buffer.putInt (link_id);
    buffer.putInt (cost);
    buffer.putInt (via);
    return buffer.array ();
  }

  public static pkt_LSPDU ParseData (byte[]UDPdata) throws Exception
  {
    ByteBuffer buffer = ByteBuffer.wrap (UDPdata);
      buffer.order (ByteOrder.LITTLE_ENDIAN);
    int sender = buffer.getInt ();
    int router_id = buffer.getInt ();
    int link_id = buffer.getInt ();
    int cost = buffer.getInt ();
    int via = buffer.getInt ();
      return new pkt_LSPDU (sender, router_id, link_id, cost, via);
  }
  public void updatePDU (int sender, int via) throws Exception
  {
    this.sender = sender;
    this.via = via;
  }
}

class pkt_INIT
{
  public int router_id;

  public pkt_INIT (int router_id)
  {
    this.router_id = router_id;
  }

  public byte[] getData ()
  {
    ByteBuffer buffer = ByteBuffer.allocate (4);
    buffer.order (ByteOrder.LITTLE_ENDIAN);
    buffer.putInt (router_id);
    return buffer.array ();
  }

  public static pkt_INIT ParseData (byte[]UDPdata) throws Exception
  {
    ByteBuffer buffer = ByteBuffer.wrap (UDPdata);
      buffer.order (ByteOrder.LITTLE_ENDIAN);
    int router_id = buffer.getInt ();
      return new pkt_INIT (router_id);
  }
}

class link_cost
{
  public int link;
  public int cost;

  public link_cost (int link, int cost)
  {
    this.link = link;
    this.cost = cost;
  }
}

class circuit_DB
{
  public int nbr_link;
  public link_cost[] linkcost;

  public circuit_DB (int nbr_link, link_cost[]linkcost)
  {
    this.nbr_link = nbr_link;
    this.linkcost = linkcost;
  }

  public static circuit_DB ParseData (byte[]UDPdata) throws Exception
  {
    ByteBuffer buffer = ByteBuffer.wrap (UDPdata);
      buffer.order (ByteOrder.LITTLE_ENDIAN);
    int nbr_link = buffer.getInt ();
      link_cost[] linkcost = new link_cost[nbr_link];
    for (int i = 0; i < nbr_link; i++)
      {
	int link_id = buffer.getInt ();
	int cost = buffer.getInt ();
	  linkcost[i] = new link_cost (link_id, cost);
      }
    return new circuit_DB (nbr_link, linkcost);
  }
}


public class router
{
  public static final int NBR_ROUTER = 5;
  public static final int disMax = 100000000;
  private static int ID;
  private static String nseAddr;
  private static int nsePort;
  private static int port;
  private static DatagramSocket Socket;
  private static pkt_INIT pkt_init;
  private static circuit_DB db;
  private static int[] links_id;
  private static int[] links_cost;
  private static circuit_DB[] topology;
  private static LinkedList < pkt_LSPDU > PDUs;
  private static LinkedList < pkt_HELLO > Helloed;
  private static int[][] graph;
  private static int[] dist;
  private static int[] first;
  private static boolean[] in_queue;
  private static PrintWriter logger;
  private static boolean dij_begin;
  private static boolean modified;
  private static int u;
  private static int v;

  public static void sendpkt (byte[]Data) throws Exception
  {
    try
    {
      InetAddress nseIP = InetAddress.getByName (nseAddr);
      DatagramPacket pkt =
	new DatagramPacket (Data, Data.length, nseIP, nsePort);
        Socket.send (pkt);
    } catch (Exception e)
    {
      Socket.close ();
    }

  }
  public static void sendINIT () throws Exception
  {
    byte[] Data = pkt_init.getData ();
    sendpkt (Data);
    logger.printf ("R%d sends an INIT: router_id %d\n", ID, ID);
    logger.flush ();

  }

  public static DatagramPacket receivepkt () throws Exception
  {
    byte[] Data = new byte[512];
    DatagramPacket pkt = new DatagramPacket (Data, Data.length);
      Socket.receive (pkt);
      return pkt;

  }

  public static void sendHELLO () throws Exception
  {
    for (int i = 0; i < db.nbr_link; ++i)
      {
	links_id[i] = db.linkcost[i].link;
	links_cost[i] = db.linkcost[i].cost;
	pkt_HELLO hello = new pkt_HELLO (ID, links_id[i]);
	  byte[] Data = hello.getData ();
	  sendpkt (Data);
	  logger.printf ("R%d sends a HELLO: router_id %d link_id %d\n", ID,
			 ID, links_id[i]);
	  logger.flush ();
      }
  }

  public static void sendPDUs (int Sender, int via) throws Exception
  {
    for (int i = 0; i < PDUs.size (); ++i)
      {
	pkt_LSPDU pdu = PDUs.get (i);
	  pdu.updatePDU (Sender, via);
	  byte[] Data = pdu.getData ();
	  sendpkt (Data);
	  logger.
	  printf
	  ("R%d sends an LS PDU: sender %d, router_id %d, link_id %d, cost %d, via %d\n",
	   ID, pdu.sender, pdu.router_id, pdu.link_id, pdu.cost, pdu.via);
	  logger.flush ();

      }


  }

  public static Boolean checkexist (pkt_LSPDU pdu)
  {
    for (int i = 0; i < PDUs.size (); ++i)
      {
	pkt_LSPDU temp = PDUs.get (i);
	if (temp.link_id == pdu.link_id &&
	    temp.cost == pdu.cost && temp.router_id == pdu.router_id)
	  {
	    return true;
	  }
      }
    return false;
  }

  public static boolean updatePDU (pkt_LSPDU pdu)
  {

    boolean updated = false;

    int nbr = topology[pdu.router_id - 1].nbr_link;

    nbr = nbr + 1;
    link_cost[]lc = new link_cost[nbr];
    if (nbr == 1)
      {
	lc[0] = new link_cost (pdu.link_id, pdu.cost);
	topology[pdu.router_id - 1] = new circuit_DB (nbr, lc);
      }
    else
      {
	for (int i = 0; i < nbr - 1; ++i)
	  {
	    lc[i] = topology[pdu.router_id - 1].linkcost[i];
	  }
	lc[nbr - 1] = new link_cost (pdu.link_id, pdu.cost);
	topology[pdu.router_id - 1] = new circuit_DB (nbr, lc);
      }
    for (int i = 0; i < PDUs.size (); ++i)
      {
	int linkid = PDUs.get (i).link_id;
	if (pdu.link_id == linkid)
	  {
	    updated = true;
	    v = pdu.router_id - 1;
	    u = PDUs.get (i).router_id - 1;
	  }
      }
    PDUs.add (pdu);
    return updated;
  }

  public static void sendPDU (pkt_LSPDU pdu) throws Exception
  {
    for (int i = 0; i < Helloed.size (); ++i)
      {
	pkt_HELLO hello = Helloed.get (i);
	if (hello.link_id != pdu.via)
	  {

	    int k = pdu.via;
	    int link = hello.link_id;
	    pkt_LSPDU temp =
	      new pkt_LSPDU (ID, pdu.router_id, pdu.link_id, pdu.cost, link);
	      byte[] Data = temp.getData ();
	      sendpkt (Data);
	      logger.
	      printf
	      ("R%d sends an LS PDU: sender %d, router_id %d, link_id %d, cost %d, via %d\n",
	       ID, ID, pdu.router_id, pdu.link_id, pdu.cost, link);
	      logger.flush ();
	  }
      }
  }

  public static void modifyGraph ()
  {

    for (int i = 0; i < 5; ++i)
      {
	circuit_DB temp1 = topology[i];
	for (int j = 0; j < temp1.nbr_link; ++j)
	  {
	    int linkid = temp1.linkcost[j].link;
	    int cost = temp1.linkcost[j].cost;
	    for (int k = i + 1; k < 5; ++k)
	      {
		circuit_DB temp2 = topology[k];
		for (int q = 0; q < temp2.nbr_link; ++q)
		  {
		    int linkid2 = temp2.linkcost[q].link;
		    if (linkid == linkid2)
		      {
			graph[i][k] = cost;
			graph[k][i] = cost;

		      }
		  }
	      }
	  }

      }


  }
  public static void init_dij ()
  {
    int t = 0;
    int s = ID - 1;
    for (int i = 0; i < 5; ++i)
      {
	if (graph[s][i] != 0)
	  {
	    t = i;
	  }
      }
    for (int i = 0; i < 5; ++i)
      {
	first[i] = 0;
	if (i == s)
	  {
	    dist[i] = 0;
	    first[i] = s;
	  }
	else if (i == t)
	  {
	    dist[i] = graph[s][t];
	    first[i] = i;
	  }
	else
	  {
	    dist[i] = disMax;
	  }
      }
    dij_begin = false;

  }

  public static void dijkstra ()
  {
    for (int i = 0; i < NBR_ROUTER; ++i)
      {
	in_queue[i] = false;
      }
    in_queue[u] = true;
    in_queue[v] = true;
    while (true)
      {
	boolean empty = true;
	int idx = 0;
	for (int i = 0; i < NBR_ROUTER; ++i)
	  {
	    if (in_queue[i])
	      {
		if (empty)
		  {
		    empty = false;
		    idx = i;
		  }
		else if (dist[i] < dist[idx])
		  {
		    idx = i;
		  }
	      }
	  }
	if (empty)
	  {
	    break;
	  }
	for (int j = 0; j < NBR_ROUTER; ++j)
	  {
	    if (graph[idx][j] > 0)
	      {
		if (dist[idx] + graph[idx][j] < dist[j])
		  {
		    dist[j] = dist[idx] + graph[idx][j];
		    first[j] = first[idx];
		    if (idx == ID - 1)
		      {
			first[j] = j;
		      }
		    if (!in_queue[j])
		      {
			in_queue[j] = true;
		      }
		  }
	      }
	  }
	in_queue[idx] = false;
      }


  }
  private static void printSolution ()
  {
    printDB ();
    logger.printf ("#RIB\n");
    if (modified == false)
      {
	for (int i = 0; i < NBR_ROUTER; ++i)
	  {
	    if (i == ID - 1)
	      {
		logger.printf ("R%d -> R%d -> Local, 0\n", ID, ID);
	      }
	    else
	      {
		logger.printf ("R%d -> R%d -> INF, INF\n", ID, i + 1);
	      }

	  }
      }
    else
      {

	for (int i = 0; i < NBR_ROUTER; ++i)
	  {
	    if (i == ID - 1)
	      {
		logger.printf ("R%d -> R%d -> Local, 0\n", ID, ID);
	      }
	    else if (dist[i] >= disMax)
	      {
		logger.printf ("R%d -> R%d -> INF, INF\n", ID, i + 1);
	      }
	    else
	      {
		logger.printf ("R%d -> R%d -> R%d, %d\n", ID, i + 1,
			       first[i] + 1, dist[i]);
	      }


	  }
      }

    logger.flush ();
  }
  private static void printDB ()
  {
    logger.printf ("# Topology database\n");
    for (int i = 0; i < NBR_ROUTER; ++i)
      {
	circuit_DB temp = topology[i];
	if (temp.nbr_link > 0)
	  {
	    logger.printf ("R%d -> R%d nbr link %d\n", ID, i + 1,
			   temp.nbr_link);
	    for (int j = 0; j < temp.nbr_link; ++j)
	      {
		logger.printf ("R%d -> R%d link %d cost %d\n", ID, i + 1,
			       temp.linkcost[j].link, temp.linkcost[j].cost);
	      }
	  }
      }
    logger.flush ();
  }





  public static void updateTopology () throws Exception
  {
    while (true)
      {
	DatagramPacket pkt = receivepkt ();
	//if receive hello
	if (pkt.getLength () == 8)
	  {
	    pkt_HELLO hello = pkt_HELLO.ParseData (pkt.getData ());
	      Helloed.add (hello);
	      logger.
	      printf ("R%d receives a HELLO: router_id %d link_id %d\n", ID,
		      hello.router_id, hello.link_id);
	      logger.flush ();
	      sendPDUs (ID, hello.link_id);

	  }
	//if receive pdu
	if (pkt.getLength () == 20)
	  {
	    pkt_LSPDU pdu = pkt_LSPDU.ParseData (pkt.getData ());
	    logger.
	      printf
	      ("R%d receives an LS PDU: sender %d, router_id %d, link_id %d, cost %d, via %d\n",
	       ID, pdu.sender, pdu.router_id, pdu.link_id, pdu.cost, pdu.via);
	    logger.flush ();
	    int routerid = pdu.router_id;
	    if (routerid == ID)
	      {
		continue;
	      }
	    if (checkexist (pdu))
	      {
		continue;
	      }
	    else
	      {
		if (updatePDU (pdu))
		  {
		    modifyGraph ();
		    modified = true;

		    if (dij_begin)
		      {
			init_dij ();
		      }
		    else
		      {
			dijkstra ();
		      }
		    printSolution ();

		  }
		else
		  {
		    printSolution ();
		  }
		sendPDU (pdu);
	      }

	  }


      }

  }

  public static void main (String[]args) throws Exception
  {
    if (args.length != 4)
      {
	System.out.println ("Invalid input numbers");
	System.exit (1);
      }
    ID = Integer.parseInt (args[0]);
    nseAddr = args[1];
    nsePort = Integer.parseInt (args[2]);
    port = Integer.parseInt (args[3]);
    Socket = new DatagramSocket ();
    pkt_init = new pkt_INIT (ID);
    links_id = new int[NBR_ROUTER];
    links_cost = new int[NBR_ROUTER];
    topology = new circuit_DB[NBR_ROUTER];
    PDUs = new LinkedList < pkt_LSPDU > ();
    Helloed = new LinkedList < pkt_HELLO > ();
    graph = new int[NBR_ROUTER][NBR_ROUTER];
    dist = new int[5];
    first = new int[5];
    dij_begin = true;
    in_queue = new boolean[5];
    u = 0;
    v = 0;
    modified = false;
    logger =
      new PrintWriter (new FileWriter (String.format ("router%d.log", ID)),
		       true);



    for (int i = 0; i < NBR_ROUTER; ++i)
      {
	link_cost[]lc = new link_cost[1];
	topology[i] = new circuit_DB (0, lc);
	for (int j = 0; j < NBR_ROUTER; ++j)
	  {
	    graph[i][j] = 0;
	  }
      }



    //send pkt_INIT
    sendINIT ();
    //receive circuit_DB
    DatagramPacket pkt = receivepkt ();
    db = circuit_DB.ParseData (pkt.getData ());
    logger.printf ("R%d receives a CIRCUIT_DB: nbr_link %d\n", ID,
		   db.nbr_link);
    logger.flush ();
    topology[ID - 1] = new circuit_DB (db.nbr_link, db.linkcost);
    for (int i = 0; i < db.nbr_link; ++i)
      {
	int linkid = db.linkcost[i].link;
	int cost = db.linkcost[i].cost;
	pkt_LSPDU pdu = new pkt_LSPDU (0, ID, linkid, cost, 0);
	PDUs.add (pdu);
      }
    //send pkt_HELLO
    sendHELLO ();
    //send and receive all pkt_LSPDU to update topology
    updateTopology ();




  }














}
