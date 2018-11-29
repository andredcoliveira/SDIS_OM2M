package  org.eclipse.om2m.app;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;


import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.util.*;
import java.io.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.json.JSONArray;
import org.json.JSONObject;


/**
 * Modified by Carlos Pereira on 11/08/2017.
 * Created by João Cardoso on 11/03/2016.
 */

public class M2MApp {
    public static M2MApp instance = null;
    private static ExecutorService notService;

    public static String filesFolder = "/home/dartes/testesESP/";


    public static int numberThreads = 1;
    public static int frequency = 100;
    public static int size = 1;
    public static int run = 1;

    public static int counterReceptions = 0;

    public static HashMap<String,Long> tEpochsSub = null;
    

    private static HttpServer server = null;

    private static String originator="admin:admin";
    private static String cseProtocol="http";
    private static String cseIp = "192.168.106.131";//"192.168.106.131";//192.168.106.128"; //"192.168.1.69"
    private static int csePort = 8080;
    private static String cseId = "in-cse";
    private static String cseName = "dartes";

    private static String aeNamePub = "pc_pub";
    private static int appPubId = 12345;
    
    private static String aeNameMaster = "master";
    private static int appMasterId = 67891;
    
    private static String cntNamePub = "HR";
    private static String cntNameMaster = "ctrlcmd";

    private static String aeMonitorName = "mymonitor";
    private static String aeProtocol="http";    
    private static String aeIp = "192.168.106.131";//"192.168.106.131"; //"192.168.1.69";
    private static int aePort = 1600;
    private static String subName="monitorsub";
    private static String targetCse = "in-cse/dartes";


    private static String csePoa = cseProtocol+"://"+cseIp+":"+csePort;
    private static String appPoa = aeProtocol+"://"+aeIp+":"+aePort;
  

    public static boolean flagSubscription = false;

    public M2MApp() {

    }

    public static M2MApp getInstance() {
        if(instance == null) {
            instance = new M2MApp();
        }
        return instance;
    }


    /**
     * Initializes Hashmaps
     * Creates notservice threads
     * Creates Server
     */
    public void startServer() {
        tEpochsSub = new HashMap<String,Long>();
        //create more threads for receptions...
        notService = Executors.newFixedThreadPool(numberThreads*10);

        System.out.println("start server");

        server = null;
        try {
            server = HttpServer.create(new InetSocketAddress(aePort), 0);
        } catch (IOException e) {
            e.printStackTrace();
        }
        server.createContext("/", new MyHandlerM2M());
        server.setExecutor(notService);
        server.start();
    }


    /**
     * Creates an Handler for receiving and processing notifications
     * <p>
     * Responds with an OK to the request
     */
    static class MyHandlerM2M implements HttpHandler {

        public void handle(HttpExchange httpExchange)  {
            System.out.println("Event Received!");
            long epoch = System.currentTimeMillis();

            try{
                InputStream in = httpExchange.getRequestBody();

                String requestBody = "";
                int i;char c;
                while ((i = in.read()) != -1) {
                    c = (char) i;
                    requestBody = (String) (requestBody+c);
                }

                //System.out.println(requestBody);

                JSONObject json = new JSONObject(requestBody);
                if (json.getJSONObject("m2m:sgn").has("m2m:vrq")) {
                    System.out.println("Confirm subscription");
                } else {
                    if(flagSubscription) {

                        if (json.getJSONObject("m2m:sgn").has("m2m:nev")) {
                            if (json.getJSONObject("m2m:sgn").getJSONObject("m2m:nev").has("m2m:rep")) {
                                if (json.getJSONObject("m2m:sgn").getJSONObject("m2m:nev").getJSONObject("m2m:rep").has("m2m:cin")) {
                                    JSONObject cin = json.getJSONObject("m2m:sgn").getJSONObject("m2m:nev").getJSONObject("m2m:rep").getJSONObject("m2m:cin");
                                    int ty = cin.getInt("ty");

                                    if (ty == 4) {
                                        counterReceptions++;
                                        String ciName = cin.getString("rn");
                                        System.out.println(counterReceptions+","+ciName+","+epoch);
                                        //System.out.println(counterReceptions + " [INFO] " + ciName + " has been created");
                                        //M2MApp.getInstance().addToEpochSub(ciName, epoch);
                                    }
                                }
                            }
                        }
                    }
                }

                // Server needs the response. Otherwise, it issues the following in the terminal:
                // org.apache.http.NoHttpResponseException: IPXXX:PORTYYY failed to respond
                String responseBudy ="";
                byte[] out = responseBudy.getBytes("UTF-8");
                httpExchange.sendResponseHeaders(200, out.length);
                OutputStream os = httpExchange.getResponseBody();
                os.write(out);
                os.close();

            } catch(Exception e){
                e.printStackTrace();
            }
        }
    }

    /**
     * Stops the server
     */
    public void stopServer() {

        try {
            TimeUnit.SECONDS.sleep(10);
        }catch(InterruptedException e){
            System.out.println(e.getMessage());
        }
        server.stop(0);
    }

    /**
     * Writes files with metrics
     * Ends notservice threads
     */
    public void writeSubToFile() {
        System.out.println("tEpochsSub size: " + tEpochsSub.size());
        System.out.println("Wait a moment for notifications threads to end!");
        notService.shutdown();
        try {
            notService.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
            System.out.println("All notifications threads ended!");
            PrintWriter tSubWriter = null;

            try {
                tSubWriter = new PrintWriter(filesFolder+"sub_2_" + "frequency_" + frequency + "_"+"size_" + size +  "_Run"+run+".txt", "UTF-8");
            } catch (Exception e) {
                e.printStackTrace();
            }

            Iterator subIt = tEpochsSub.entrySet().iterator();
            while (subIt.hasNext()) {
                Map.Entry pair = (Map.Entry)subIt.next();
                tSubWriter.println(pair.getKey() + "," + pair.getValue());
                subIt.remove(); // avoids a ConcurrentModificationException
            }

            tSubWriter.close();
            System.out.println("Ended!");
        } catch (InterruptedException e) {
            System.err.println("Exception: " + e.getMessage());
        }
    }

    /**
     * Issues a creation of a new monitor application and creates a new subscription
     * TODO: Change method to include rnapp, api, rn sub
     *
     * @return      HTTPResponse
     */
   public void createMonitor() {
        JSONArray array = new JSONArray();
        array.put(appPoa);
        JSONObject obj = new JSONObject();
        obj.put("rn", aeMonitorName);
        obj.put("api", 12346);
        obj.put("rr", true);
        obj.put("poa",array);
        JSONObject ae = new JSONObject();
        ae.put("m2m:ae", obj);
        RestHttpClient.post(originator, csePoa+"/~/"+cseId+"/"+cseName, ae.toString(), 2);

        JSONArray array2 = new JSONArray();
        array2.put("/"+cseId+"/"+cseName+"/"+aeMonitorName);
        JSONObject obj2 = new JSONObject();
        obj2.put("nu", array2);
        obj2.put("rn", subName);
        obj2.put("nct", 2);
        JSONObject sub = new JSONObject();
        sub.put("m2m:sub", obj2);
        //HttpResponse httpResponseSub = RestHttpClient.post(originator, csePoa+"/~/"+targetCse+"/"+aeName+"/"+cntName, sub.toString(), 23);
        HttpResponse httpResponseSub = RestHttpClient.post(originator, csePoa+"/~/"+targetCse+"/esp_pub/HR", sub.toString(), 23);

    }

    /**
     * Creates a new application
     *
     * @param  name application
     * @param  id application
     */
    public void createApplication(String applicationId, int appId) {
        JSONObject obj = new JSONObject();
        obj.put("rn", applicationId);
        obj.put("api", appId);
        obj.put("rr", false);
        JSONObject resource = new JSONObject();
        resource.put("m2m:ae", obj);
        RestHttpClient.post(originator, csePoa+"/~/"+cseId+"/"+cseName, resource.toString(), 2);
    }

    /**
     * Creates a new container
     *
     * @param  name container
     */
    public void createContainer(String aeName, String containerId) {

        JSONObject obj = new JSONObject();
        obj.put("rn", containerId);
        JSONObject resource = new JSONObject();
        resource.put("m2m:cnt", obj);
        RestHttpClient.post(originator, csePoa+"/~/"+cseId+"/"+cseName+"/"+aeName, resource.toString(), 3);
    }


    /**
     * Creates a new contentInstance and store there data
     * Add application name instead of static
     *
     * @param  name data
     * @param  name container
     * @param  name contentInstance
     */
    public void createContentInstance(String data, String aeName, String containerId, String contentInstanceId) {

        JSONObject obj = new JSONObject();
        obj.put("rn",  contentInstanceId);
        obj.put("pc",  "cenas_teste");
        obj.put("cnf", "application/json");
        obj.put("con", data);
        JSONObject resource = new JSONObject();
        resource.put("m2m:cin", obj);

        HttpResponse httpResponse = RestHttpClient.post(originator, csePoa+"/~/"+cseId+"/"+cseName+"/"+aeName+"/"+containerId, resource.toString(), 4);
        //System.out.println("Status: " + httpResponse.getStatusCode());

    }

    /**
     * Sets a counter
     *
     * @param  counter  seconds
     */
    public void counterTime(int secCounter) throws InterruptedException {
        System.out.println("Counter");

        int timet = secCounter;
        long delay = timet * 1000;
        do {
            int minutes = timet / 60;
            int seconds = timet % 60;
            System.out.println(minutes + " minute(s), " + seconds + " second(s)");
            Thread.sleep(1000);
            timet = timet - 1;
            delay = delay - 1000;
        }
        while (delay != 0);
        System.out.println("Time's Up!");
    }


    public void addToEpochSub (String edge, long millis) {
        tEpochsSub.put(edge,millis);
    }



    public static void main(String[] args) throws IOException, InterruptedException {

        if (args.length == 3) {
            try {
                frequency = Integer.parseInt(args[0]);
                size = Integer.parseInt(args[1]);
                run = Integer.parseInt(args[2]);
            } catch (NumberFormatException e) {
                System.err.println("Arguments" + args[0] + args[1] + args[2] + " must be integers.");
                System.exit(1);
            }
        }

        System.out.println("oM2M PoA: "+csePoa);
        System.out.println("Publisher/Subscriber PoA: "+appPoa);
        System.out.println("Number of Threads: "+numberThreads);
        System.out.println("Folder for files: "+filesFolder);

        // Delete publish and monitor app via API
       // RestHttpClient.delete(originator, csePoa+"/~/"+"in-cse/acp-876889265");
        //RestHttpClient.delete(originator, csePoa+"/~/"+"in-cse/acp-735229994");
        //RestHttpClient.delete(originator, csePoa+"/~/"+cseId+"/"+cseName+"/"+aeNamePub);
        //RestHttpClient.delete(originator, csePoa+"/~/"+cseId+"/"+cseName+"/"+aeMonitorName);
        //String data = "73hdjetyru4682jeir638rnforte63uwir6390kmgsi2538endi84jdo7svcndghlduyduedsr353wefds43s";


        M2MApp.getInstance().startServer();
        M2MApp.getInstance().createApplication(aeNameMaster,appMasterId);
        M2MApp.getInstance().createMonitor();
        flagSubscription = true;  // start collecting times
        
        
      	//M2MApp.getInstance().createApplication(aeNamePub,appPubId);
        //M2MApp.getInstance().createContainer(aeNamePub, cntNamePub);
        
        
        //String data = "73hdjetyru4682jeir638rnforte63uwir6390kmgsi2538endi84jdo7svcndghlduyduedsr353wefds43s73hdjetyru4682jeir638rnforte63uwir6390kmgsi2538endi84jdo7svcndghlduyduedsr353wefds43s73hdjetyru4682jeir638rnforte63uwir6390kmgsi2538endi84jdo7svcndghlduyduedsr353wefds43s73hdjetyru4682jeir638rnforte63uwir6390kmgsi2538endi84jdo7svcndghlduyduedsr353wefds43s73hdjetyru4682jeir638rnforte63uwir6390kmgsi2538endi84jdo7svcndghlduyduedsr353wefds43s73hdjetyru4682jeir638rnforte63uwir6390kmgsi2538endi84jdo7svcndghlduyduedsr353wefds43s73hdjetyru4682jeir638rnforte63uwir6390kmgsi2538endi84jdo7svcndghlduyduedsr353wefds43s73hdjetyru4682jeir638rnforte63uwir6390kmgsi2538endi84jdo7svcndghlduyduedsr353wefds43s73hdjetyru4682jeir638rnforte63uwir6390kmgsi2538endi84jdo7svcndghlduyduedsr353wefds43s73hdjetyru4682jeir638rnforte63uwir6390kmgsi2538endi84jdo7svcndghlduyduedsr353wefds43s";
        
        //Thread.sleep(20000);
        
      /*  for (int i = 52; i < 10000; i++) {        	
         	M2MApp.getInstance().createContentInstance(data, aeNamePub, cntNamePub, Integer.toString(i));
         	Thread.sleep(5000);
 		}*/
       
        return;

     //M2MApp.getInstance().createApplication(aeNameMaster,appMasterId);        
       
     // M2MApp.getInstance().createContainer(aeNameMaster, cntNameMaster);
       // M2MApp.getInstance().createContainer(aeNamePub, cntNamePub);
        

        
      // M2MApp.getInstance().createContainer(aeNameMaster, cntNameMaster);
        
    
 // M2MApp.getInstance().createContentInstance(String.valueOf(System.currentTimeMillis()), aeNameMaster, cntNameMaster, Long.toString(System.currentTimeMillis()));
        
      //System.currentTimeMillis()
        

        
        
       // M2MApp.getInstance().createContentInstance("0x", aeNameMaster, cntNameMaster, "Modem-sleep");
        
        
        //M2MApp.getInstance().createMonitor();
        
        

      /*  flagSubscription = true;  // start collecting times

        try {
            M2MApp.getInstance().counterTime(340); //120 265
        }catch (InterruptedException e){
            System.out.println(e.getMessage());
        }

        M2MApp.getInstance().stopServer();
        M2MApp.getInstance().writeSubToFile();
        System.exit(0);

        return;*/
    }

}