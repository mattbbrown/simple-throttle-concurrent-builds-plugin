/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package javatesting;

import java.util.HashMap;
import java.util.Map;

/**
 *
 * @author mbrown
 */
public class JavaTesting {

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        String VHT_Installation = "SA_Windows_2012";
        long zero = 0;
        Map<String,Long> timeSinceChefPull = new HashMap<String,Long>();
        System.out.println((System.currentTimeMillis()-timeSinceChefPull.getOrDefault(VHT_Installation, zero))/1000);
    }

}
