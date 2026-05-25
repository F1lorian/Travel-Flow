package com.sep.sep_backend.TripManagement.dto;

import com.sep.sep_backend.Enitys.Categories;

public class CategoryDTO {
    private long id;
    private String name;

    public static CategoryDTO from(Categories c) {
        CategoryDTO dto = new CategoryDTO();
        dto.id = c.getId();
        dto.name = c.getName();
        return dto;
    }

    public long getId() { return id; }
    public String getName() { return name; }
}