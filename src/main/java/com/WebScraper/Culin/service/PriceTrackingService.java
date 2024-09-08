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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class PriceTrackingService {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private UserRepository userRepository;

//    @Scheduled(fixedRate = 30000)
//    public void trackProductPrice(){
//        System.out.println("RUNNING 1 HOUR MACHINE!!!");
//        List<Product> products = productRepository.findAll();
//        boolean newLowerPriceFound = false;
//        if(products != null){
//            for(Product product : products){
//                Map<String, Map<String, Integer>> prices = priceTrack(product.getProductName());
//                for(Map.Entry<String,Integer> entry : prices.entrySet()){
//                    String site = entry.getKey();
//                    int price = entry.getValue();
//                    if(price < product.getCurrentPrice()){
//                        System.out.println("NEW CURRENT LOWEST PRICE: " + price + " AT SITE: " + site);
//                        product.setCurrentPrice(price);
//                        product.setLink(site);
//                        newLowerPriceFound = true;
//                    }
//                }
//                if(newLowerPriceFound){
//                    User user = userRepository.findByUsername(product.getUsername());
//                    sendEmail(user, product);
//                }
//            }
//        }
//    }

    @Scheduled(fixedRate = 30000)
    public void trackProductPrice() {
        System.out.println("RUNNING 1 HOUR MACHINE!!!");
        List<Product> products = productRepository.findAll();
        boolean newLowerPriceFound = false;

        if (products != null) {
            for (Product product : products) {
                Map<String, Map<String, String>> prices = priceTrack(product.getProductName());
                for (Map.Entry<String, Map<String, String>> entry : prices.entrySet()) {
                    String site = entry.getKey();
                    String priceString = entry.getValue().get("price");
                    String realLink = entry.getValue().get("url");

                    if (priceString != null) {
                        int price = Integer.parseInt(priceString);

                        if (price < product.getCurrentPrice()) {
                            System.out.println("NEW CURRENT LOWEST PRICE: " + price + " AT SITE: " + site);
                            product.setCurrentPrice(price);
                            product.setLink(realLink);  // Set the real link instead of the site
                            newLowerPriceFound = true;
                        }
                    }
                }

                if (newLowerPriceFound) {
                    User user = userRepository.findByUsername(product.getUsername());
                    sendEmail(user, product);  // Notify the user with the new price
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


    public Map<String, Map<String,String>> priceTrack(String product){
        Map<String, Map<String,String>> prices = new HashMap<>();
        ExecutorService executorService = Executors.newFixedThreadPool(Constants.SITE_LIST.length);
        for(String site : Constants.SITE_LIST){
            executorService.submit(() -> {
                System.out.println();
                Map<String, String> data = search(site, product);
                if(data != null){
                    prices.put(site, data);
                }
            });
        }
        shutdownAndAwaitTermination(executorService);

        return prices;
    }

    public Map<String,String> search(String site,String name){
        String price = "";
        String productName = "";
        String url = "";
        String rating = "";
        String nodePath = ".\\..\\NodeJs\\node.exe";
        String scriptPath = ".\\..\\puppeteer\\index.js";

        Map<String,String> data = new HashMap<>();

        try{

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
            while ((line = reader.readLine()) != null){
                if(line.contains("Product Price:")){
                    price = line.split("Product Price:")[1].trim().replaceAll("[^0-9]", "");
                }
                if(line.contains("Product Name:")){
                    productName = line.split("Product Name:")[1].trim();
                }
                if(line.contains("Page URL:")){
                    url = line.split("Page URL:")[1].trim();
                }
                if(line.contains("Product Rating:")){
                    rating = getRating(line.split("Product Rating:")[1].trim());
                }
                System.out.println(line);
            }
            reader.close();
            int exitVal = process.waitFor();
            if(exitVal != 0){
                System.out.println("Abnormal Behaviour Detected! Mission Abort!");
            }
        }catch(InterruptedException ie){
            System.out.println(ie);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        if(productName == null || url == null || productName == "" || url == ""){
            return null;
        }
        data.put("productName", productName);
        data.put("price",price);
        data.put("rating", rating);
        data.put("url", url);
        return data;
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

    public String getRating(String text){
        // Regular expression to match the rating number
        String regex = "\\d+(\\.\\d+)?";

        // Compile the pattern
        Pattern pattern = Pattern.compile(regex);

        return extractRating(text, pattern);
    }

    public static String extractRating(String text, Pattern pattern) {
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return matcher.group();  // Return the first match (the rating number)
        } else {
            return "Rating not found";
        }
    }

}
