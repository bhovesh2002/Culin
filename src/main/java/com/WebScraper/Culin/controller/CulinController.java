package com.WebScraper.Culin.controller;

import com.WebScraper.Culin.Constant.Constants;
import com.WebScraper.Culin.DTO.ProductRegisterDTO;
import com.WebScraper.Culin.repository.ProductRepository;
import com.WebScraper.Culin.repository.UserRepository;
import com.WebScraper.Culin.user.Product;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
//@RequestMapping("/scrape")
public class CulinController {

//    public static final String[] SITE_LIST={"amazon","flipkart","myntra"};

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private UserRepository userRepository;

//    @CrossOrigin(origins = "*")
//    @GetMapping
//    public Map<String, Map<String, String>> scrape(@RequestParam String product) {
//        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
//        String username = authentication.getName();
//        System.out.println("AUTH: " + authentication);
//        System.out.println("USERNAME: " + username);
//        String site = "myntra";
//        String name = product;
//        System.out.println("PRODUCT NAME: " + name);
//
//        Map<String, Map<String, String>> data = priceTrack(product);
////        Map<String, String> price = search(site,name);
//
////        System.out.println("FINAL PRICE:"+price);
//        System.out.println("FINAL PRICE:"+data);
//        return data;
//    }

    @CrossOrigin(origins = "*")
    @GetMapping("/find/product")
    public ResponseEntity<Map<String, Map<String, String>>> scrape(@RequestParam String product) {
        // Get the authentication object from the Security Context
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();

        // Log the authentication details for debugging
        System.out.println("AUTH: " + authentication);
        System.out.println("USERNAME: " + username);

        // Log the product name for debugging
        System.out.println("PRODUCT NAME: " + product);

        // Call the price tracking function with the product name
        Map<String, Map<String, String>> data = priceTrack(product);

        // Log the final price data for debugging
        System.out.println("FINAL PRICE: " + data);

        // Return the data in a response entity with an OK status
        return ResponseEntity.ok(data);
    }

    @CrossOrigin(origins = "*")
    @PostMapping("/register-product")
    public ResponseEntity<?> registerProduct(@RequestBody ProductRegisterDTO productRegisterDTO) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();
        Product product = new Product();
        product.setProductName(productRegisterDTO.getProductName());

        System.out.println("AUTH: " + authentication);
        System.out.println("USERNAME: " + username);
        System.out.println("PRODUCT RECEIVED: " + product.getProductName());

        product.setEmail(userRepository.findByUsername(username).getEmail());
        product.setUsername(username);

        System.out.println("PRODUCT EMAIL: "  +product.getEmail());
        System.out.println("PRODUCT USERNAME: " + product.getUsername());
        System.out.println("DATA: " + productRegisterDTO.getData());

        Entry<String, Integer> lowestPriceAndPlatform = findPlatformWithLowestPrice(productRegisterDTO.getData());
        System.out.println("ENTRY: " + lowestPriceAndPlatform);
        product.setLink(lowestPriceAndPlatform.getKey());
        if(product.getCurrentPrice() == 0 || product.getCurrentPrice() <= lowestPriceAndPlatform.getValue()){
            product.setLowestPrice(lowestPriceAndPlatform.getValue());
            product.setCurrentPrice(lowestPriceAndPlatform.getValue());
        }

        System.out.println("LOWEST: " + product.getLowestPrice());
        System.out.println("CURRENT: " + product.getCurrentPrice());

        productRepository.save(product);

        return new ResponseEntity<>(HttpStatus.OK);
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

    public static Entry<String, Integer> findPlatformWithLowestPrice(Map<String, String> data) {
        if (data == null || data.isEmpty()) {
            return null;
        }

        Entry<String, Integer> lowestEntry = null;

        for (Entry<String, String> entry : data.entrySet()) {
            int price = Integer.parseInt(entry.getValue().replaceAll("[^0-9]", ""));

            if (lowestEntry == null || price < lowestEntry.getValue()) {
                lowestEntry = Map.entry(entry.getKey(), price);
            }
        }

        return lowestEntry;
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
