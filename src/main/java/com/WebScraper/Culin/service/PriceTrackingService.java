package com.WebScraper.Culin.service;

import com.WebScraper.Culin.Constant.Constants;
import com.WebScraper.Culin.repository.ProductRepository;
import com.WebScraper.Culin.repository.UserRepository;
import com.WebScraper.Culin.user.Product;
import com.WebScraper.Culin.user.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Service
public class PriceTrackingService {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private UserRepository userRepository;

    @Scheduled(fixedRate = 30000)
    public void trackProductPrice(){
        System.out.println("RUNNING 1 HOUR MACHINE!!!");
        List<Product> products = productRepository.findAll();
        boolean newLowerPriceFound = false;
        if(products != null){
            for(Product product : products){
                Map<String, Integer> prices = priceTrack(product.getProductName());
                for(Map.Entry<String,Integer> entry : prices.entrySet()){
                    String site = entry.getKey();
                    int price = entry.getValue();
                    if(price < product.getCurrentPrice()){
                        System.out.println("NEW CURRENT LOWEST PRICE: " + price + " AT SITE: " + site);
                        product.setCurrentPrice(price);
                        product.setLink(site);
                        newLowerPriceFound = true;
                    }
                }
                if(newLowerPriceFound){
                    User user = userRepository.findByUsername(product.getUsername());
                    sendEmail(user, product);
                }
            }
        }
    }

    public void sendEmail(User user, Product product){
        String nodePath = ".\\..\\NodeJs\\node.exe";
        String scriptPath = ".\\..\\puppeteer\\email.js";
        String email = user.getEmail();

        try{
            String command = String.format(
                    "$env:EMAIL='%s'; $env:USERNAME='%s'; $env:PRODUCT='%s'; $env:LINK='%s'; & '%s' '%s'",
                    email, user.getUsername(), product.getProductName(), product.getLink(), nodePath, scriptPath
            );
            ProcessBuilder builder = new ProcessBuilder("powershell", "-command", command);
            builder.directory(new File(".\\..\\puppeteer"));
            builder.redirectErrorStream(true);
            Process process = builder.start();

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null){
                System.out.println(line);
            }
            reader.close();
            int exitVal = process.waitFor();

            if(exitVal != 0){
                System.out.println("FAILED TO SEND THE EMAIL!!!");
            }

        }catch (IOException ioe){
            throw new RuntimeException(ioe);
        }catch (InterruptedException ie){
            System.out.println(ie);
        }
    }


    public Map<String, Integer> priceTrack(String product) {
        Map<String, Integer> prices = new HashMap<>();
        ExecutorService executorService = Executors.newFixedThreadPool(Constants.SITE_LIST.length);

        for (String site : Constants.SITE_LIST) {
            executorService.submit(() -> {
                int price = search(site, product);
                synchronized (prices) { // To avoid race conditions when modifying the map
                    prices.put(site, price);
                }
            });
        }

        shutdownAndAwaitTermination(executorService);
        return prices;
    }

    public int search(String site, String name) {
        String priceStr = "";
        String nodePath = ".\\..\\NodeJs\\node.exe";
        String scriptPath = ".\\..\\puppeteer\\index.js";

        try {
            String command = String.format(
                    "$env:SITE_NAME='%s'; $env:SEARCH_QUERY='%s'; & '%s' '%s'",
                    site, name, nodePath, scriptPath
            );
            ProcessBuilder builder = new ProcessBuilder("powershell", "-command", command);
            builder.directory(new File(".\\..\\puppeteer"));
            builder.redirectErrorStream(true);
            Process process = builder.start();

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains("Product Price:")) {
                    priceStr = line.split("Product Price:")[1].trim().replaceAll("[^0-9]", "");
                }
                System.out.println(line);
            }
            reader.close();
            int exitVal = process.waitFor();
            if (exitVal != 0) {
                System.out.println("Abnormal Behaviour Detected! Mission Abort!");
            }
        } catch (InterruptedException ie) {
            System.out.println(ie);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        // Convert price to integer, return 0 if not parsable
        int price = 0;
        try {
            price = Integer.parseInt(priceStr);
        } catch (NumberFormatException nfe) {
            System.out.println("Error parsing price: " + nfe.getMessage());
        }
        return price;
    }

    public void shutdownAndAwaitTermination(ExecutorService executorService){
        executorService.shutdown();
        try{
            if(!executorService.awaitTermination(60, TimeUnit.SECONDS)){
                executorService.shutdownNow();
            }
        }catch (InterruptedException ie){
            executorService.shutdownNow();
            System.out.println("IE: "+ ie);
        }
    }

}
