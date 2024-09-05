package com.WebScraper.Culin.DTO;

import java.util.Map;

public class ProductRegisterDTO {

    private String productName;
    private Map<String, String> data;

    public ProductRegisterDTO() {
    }

    public ProductRegisterDTO(String productName, Map<String, String> data) {
        this.productName = productName;
        this.data = data;
    }

    // Getters and Setters
    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public Map<String, String> getData() {
        return data;
    }

    public void setData(Map<String, String> data) {
        this.data = data;
    }

}
