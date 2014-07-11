/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package javatesting;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author mbrown
 */
public class JavaTesting {

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        Process p;
        try {
            long startTime = System.currentTimeMillis();
            p = Runtime.getRuntime().exec("cmd /C  knife exec -E \"nodes.find(:name => 'TERMINUS.qalab.local') {|n| n.set['vht']['taken']=true; n.save}\"");
            p.waitFor();

            String line;
            boolean found = false;
            while (!found) {
                p = Runtime.getRuntime().exec("cmd /C  knife exec -E \"nodes.find(:VHT_Installation => 'SA_Windows_2012', :taken => 'true') {|n| puts n.name}\"");
                p.waitFor();
                BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));

                while ((line = reader.readLine()) != null) {
                    found = true;
                    System.out.println(line);
                }
            }
            long stopTime = System.currentTimeMillis();
            System.out.println("Time in seconds: " + (stopTime - startTime)/1000);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
